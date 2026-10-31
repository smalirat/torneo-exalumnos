"""Off-site storage for the nightly database dumps: an Object Storage bucket in Oracle Cloud (Always Free: 20 GB).

Creates (or reuses) everything and prints, on stdout, a write-only upload URL for the server's .env (OFFSITE_URL):

- Bucket "torneo-backups", private, Standard tier.
- Retention rule: no object can be overwritten or deleted for 30 days, not even with the upload URL or by mistake.
- Lifecycle rule: objects older than 90 days are deleted. Object Storage needs an IAM policy to delete on its own
  ("Allow service objectstorage-<region> to manage object-family in tenancy"), created here if missing.
- Pre-authenticated request (PAR) that can only write objects: it cannot list, read or delete. It expires after a year;
  running this again makes a new one and revokes the old ones (rotation).

The URL is a secret: pipe it straight to the server instead of printing it (see docs/deploy.md, "Backups fuera del
servidor"). Everything else is logged on stderr.

    python infra/oci_backup_bucket.py | ssh ubuntu@IP 'cat > /tmp/offsite && ...'

Auth: ~/.oci/config, like infra/oci_launch.py.
"""
import sys
import time
from datetime import datetime, timedelta, timezone

import oci

BUCKET = 'torneo-backups'
PAR_NAME = 'torneo-backup-upload'
RETENTION_DAYS, DELETE_AFTER_DAYS, URL_VALID_DAYS = 30, 90, 365


def log(msg):
    print(f'{datetime.now():%H:%M:%S} {msg}', file=sys.stderr, flush=True)


def ensure_lifecycle_policy(identity, tenancy, region):
    statement = f'Allow service objectstorage-{region} to manage object-family in tenancy'
    policies = identity.list_policies(tenancy).data
    if any(statement.lower() in (s.lower() for s in p.statements) for p in policies):
        return
    log('creating IAM policy so Object Storage can apply the lifecycle rule')
    identity.create_policy(oci.identity.models.CreatePolicyDetails(
        compartment_id=tenancy, name='objectstorage-lifecycle',
        description='Object Storage deletes old objects (lifecycle rules)', statements=[statement]))


def main():
    config = oci.config.from_file()
    tenancy, region = config['tenancy'], config['region']
    storage = oci.object_storage.ObjectStorageClient(config)
    namespace = storage.get_namespace().data

    try:
        storage.get_bucket(namespace, BUCKET)
        log(f'bucket {BUCKET} exists')
    except oci.exceptions.ServiceError as e:
        if e.status != 404:
            raise
        log(f'creating bucket {BUCKET}')
        storage.create_bucket(namespace, oci.object_storage.models.CreateBucketDetails(
            name=BUCKET, compartment_id=tenancy, public_access_type='NoPublicAccess', storage_tier='Standard'))

    if not storage.list_retention_rules(namespace, BUCKET).data.items:
        log(f'retention rule: {RETENTION_DAYS} days without overwrite or delete')
        storage.create_retention_rule(namespace, BUCKET, oci.object_storage.models.CreateRetentionRuleDetails(
            display_name=f'keep-{RETENTION_DAYS}-days',
            duration=oci.object_storage.models.Duration(time_amount=RETENTION_DAYS, time_unit='DAYS')))

    ensure_lifecycle_policy(oci.identity.IdentityClient(config), tenancy, region)
    log(f'lifecycle rule: delete after {DELETE_AFTER_DAYS} days')
    rule = oci.object_storage.models.PutObjectLifecyclePolicyDetails(items=[oci.object_storage.models.ObjectLifecycleRule(
        name=f'delete-after-{DELETE_AFTER_DAYS}-days', target='objects', action='DELETE',
        time_amount=DELETE_AFTER_DAYS, time_unit='DAYS', is_enabled=True)])
    for attempt in range(20):
        try:
            storage.put_object_lifecycle_policy(namespace, BUCKET, rule)
            break
        except oci.exceptions.ServiceError as e:
            # A new IAM policy takes a few minutes to reach every Oracle server
            if e.code != 'InsufficientServicePermissions' or attempt == 19:
                raise
            log('the IAM policy has not propagated yet, retrying in 15 s')
            time.sleep(15)

    old = [p for p in storage.list_preauthenticated_requests(namespace, BUCKET).data if p.name == PAR_NAME]
    expires = datetime.now(timezone.utc) + timedelta(days=URL_VALID_DAYS)
    par = storage.create_preauthenticated_request(
        namespace, BUCKET, oci.object_storage.models.CreatePreauthenticatedRequestDetails(
            name=PAR_NAME, access_type='AnyObjectWrite', bucket_listing_action='Deny', time_expires=expires)).data
    for p in old:
        storage.delete_preauthenticated_request(namespace, BUCKET, p.id)
    log(f'write-only upload URL created, valid until {expires:%Y-%m-%d}' + (f'; revoked {len(old)} old one(s)' if old else ''))
    url = par.full_path or f'https://objectstorage.{region}.oraclecloud.com{par.access_uri}'
    sys.stdout.buffer.write(f'{url}\n'.encode())   # bare \n: print() on Windows ends with \r\n, which breaks the URL


if __name__ == '__main__':
    main()
