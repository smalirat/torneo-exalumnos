"""Creates an Always Free server in Oracle Cloud, retrying until there is capacity: the ARM one (VM.Standard.A1.Flex,
up to 4 OCPU / 24 GB) or, with --micro, the small AMD one (VM.Standard.E2.1.Micro, 1 GB, almost always available).

The console fails with "Out of capacity" when no ARM host is free; this asks again every few minutes, in every
availability domain, without fixing a fault domain (the API lets Oracle pick one, the console does not).
It reuses the network if it exists (VCN "torneo-vcn", public subnet "torneo-public") and creates it otherwise.

Auth: ~/.oci/config (user, tenancy, fingerprint, key_file, region), the same file the OCI CLI uses.

    python infra/oci_launch.py                  # 1 OCPU / 6 GB, every 2 minutes
    python infra/oci_launch.py --ocpus 2 --memory 12 --every 60
    python infra/oci_launch.py --micro          # instance "torneo-demo"
"""
import argparse
import os
import time
from datetime import datetime

import oci

A1, MICRO = 'VM.Standard.A1.Flex', 'VM.Standard.E2.1.Micro'
VCN_NAME, SUBNET_NAME = 'torneo-vcn', 'torneo-public'
SSH_KEY = os.path.expanduser('~/.ssh/oracle_torneo.pub')


def log(msg):
    print(f'{datetime.now():%H:%M:%S} {msg}', flush=True)


def ensure_network(net, compartment):
    """Public subnet with a route to the internet: VCN + internet gateway + default route + subnet."""
    vcns = [v for v in net.list_vcns(compartment, display_name=VCN_NAME).data if v.lifecycle_state == 'AVAILABLE']
    if vcns:
        vcn = vcns[0]
    else:
        log(f'creating VCN {VCN_NAME}')
        vcn = net.create_vcn(oci.core.models.CreateVcnDetails(
            compartment_id=compartment, display_name=VCN_NAME, cidr_blocks=['10.0.0.0/16'], dns_label='torneo')).data
        oci.wait_until(net, net.get_vcn(vcn.id), 'lifecycle_state', 'AVAILABLE')
    subnets = [s for s in net.list_subnets(compartment, vcn_id=vcn.id, display_name=SUBNET_NAME).data
               if s.lifecycle_state == 'AVAILABLE']
    if subnets:
        return subnets[0]

    gateways = net.list_internet_gateways(compartment, vcn_id=vcn.id).data
    if gateways:
        gateway = gateways[0]
    else:
        log('creating internet gateway')
        gateway = net.create_internet_gateway(oci.core.models.CreateInternetGatewayDetails(
            compartment_id=compartment, vcn_id=vcn.id, is_enabled=True, display_name='torneo-igw')).data
        oci.wait_until(net, net.get_internet_gateway(gateway.id), 'lifecycle_state', 'AVAILABLE')
    # Everything that is not inside the VCN goes out through the gateway
    routes = net.get_route_table(vcn.default_route_table_id).data
    if not any(r.destination == '0.0.0.0/0' for r in routes.route_rules):
        net.update_route_table(routes.id, oci.core.models.UpdateRouteTableDetails(route_rules=routes.route_rules + [
            oci.core.models.RouteRule(destination='0.0.0.0/0', destination_type='CIDR_BLOCK',
                                      network_entity_id=gateway.id)]))
    log(f'creating public subnet {SUBNET_NAME}')
    subnet = net.create_subnet(oci.core.models.CreateSubnetDetails(
        compartment_id=compartment, vcn_id=vcn.id, display_name=SUBNET_NAME, cidr_block='10.0.0.0/24',
        dns_label='public', prohibit_public_ip_on_vnic=False)).data
    return oci.wait_until(net, net.get_subnet(subnet.id), 'lifecycle_state', 'AVAILABLE').data


def ubuntu_image(compute, compartment, shape):
    images = compute.list_images(compartment, operating_system='Canonical Ubuntu', operating_system_version='24.04',
                                 shape=shape, sort_by='TIMECREATED', sort_order='DESC').data
    if not images:
        raise SystemExit(f'No Ubuntu 24.04 image for {shape} found')
    return images[0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ocpus', type=float, default=1)
    ap.add_argument('--memory', type=float, default=6, help='GB')
    ap.add_argument('--every', type=int, default=120, help='seconds between rounds')
    ap.add_argument('--micro', action='store_true', help='VM.Standard.E2.1.Micro instead of A1 (fixed 1 OCPU / 1 GB)')
    args = ap.parse_args()
    name, shape = ('torneo-demo', MICRO) if args.micro else ('torneo', A1)

    config = oci.config.from_file()
    compartment = config['tenancy']  # root compartment
    identity = oci.identity.IdentityClient(config)
    compute = oci.core.ComputeClient(config)
    net = oci.core.VirtualNetworkClient(config)

    existing = [i for i in compute.list_instances(compartment, display_name=name).data
                if i.lifecycle_state not in ('TERMINATED', 'TERMINATING')]
    if existing:
        raise SystemExit(f'There is already an instance "{name}" ({existing[0].lifecycle_state}): not creating another')

    ads = [ad.name for ad in identity.list_availability_domains(compartment).data]
    subnet = ensure_network(net, compartment)
    image = ubuntu_image(compute, compartment, shape)
    with open(SSH_KEY) as f:
        ssh_key = f.read().strip()
    size = '' if args.micro else f', {args.ocpus:g} OCPU / {args.memory:g} GB'
    log(f'{shape}{size}, image {image.display_name}, ADs: {", ".join(ads)}')

    attempt = 0
    while True:
        for ad in ads:
            attempt += 1
            try:
                instance = compute.launch_instance(oci.core.models.LaunchInstanceDetails(
                    compartment_id=compartment, availability_domain=ad, display_name=name, shape=shape,
                    # Flex shapes take their size; the micro has a fixed one
                    shape_config=None if args.micro else oci.core.models.LaunchInstanceShapeConfigDetails(
                        ocpus=args.ocpus, memory_in_gbs=args.memory),
                    source_details=oci.core.models.InstanceSourceViaImageDetails(image_id=image.id),
                    create_vnic_details=oci.core.models.CreateVnicDetails(
                        subnet_id=subnet.id, assign_public_ip=True, display_name='torneo-vnic'),
                    metadata={'ssh_authorized_keys': ssh_key})).data
            except oci.exceptions.ServiceError as e:
                if 'capacity' in e.message.lower():
                    log(f'#{attempt} {ad}: no capacity')
                    continue
                if e.status == 429:  # too many requests: wait and go on
                    log(f'#{attempt} {ad}: too many requests')
                    time.sleep(30)
                    continue
                raise
            log(f'#{attempt} {ad}: created, waiting until it runs...')
            oci.wait_until(compute, compute.get_instance(instance.id), 'lifecycle_state', 'RUNNING',
                           max_wait_seconds=900)
            vnic_id = compute.list_vnic_attachments(compartment, instance_id=instance.id).data[0].vnic_id
            ip = net.get_vnic(vnic_id).data.public_ip
            log(f'RUNNING. Public IP: {ip}   ->   ssh -i ~/.ssh/oracle_torneo ubuntu@{ip}')
            return
        time.sleep(args.every)


if __name__ == '__main__':
    main()
