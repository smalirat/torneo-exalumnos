-- Banner (cover) and logo of an edition, shown on its Inicio in the panel. Like team crests: the image lives
-- apart and tournaments.<kind>_version changes on every upload and goes in the URL (cached forever). None = null.
create table tournament_images (
    id            bigserial    primary key,
    tournament_id bigint       not null,
    kind          varchar(10)  not null,
    content_type  varchar(50)  not null,
    image_data    bytea        not null,
    constraint uq_tournament_images unique (tournament_id, kind),
    constraint fk_tournament_images_tournament foreign key (tournament_id) references tournaments (id) on delete cascade
);
alter table tournaments add column banner_version integer;
alter table tournaments add column logo_version integer;
