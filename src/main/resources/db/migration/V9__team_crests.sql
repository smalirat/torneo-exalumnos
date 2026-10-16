-- Team crest (logo). The image lives apart so lists of teams never load it; teams.crest_version changes on
-- every upload and goes in the image URL, so browsers can cache each version forever. No crest = null.
create table team_crests (
    team_id      bigint      primary key,
    content_type varchar(50) not null,
    image_data   bytea       not null,
    constraint fk_team_crests_team foreign key (team_id) references teams (id) on delete cascade
);
alter table teams add column crest_version integer;
