-- A match suspended while being played (rain, a team under the minimum): it keeps the score so far and the
-- minutes left, does not count until its final result is loaded.
alter table matches add column suspended boolean not null default false;
alter table matches add column remaining_minutes integer;

-- A captain's protest about a match of their team; the admin (Comisión / Tribunal) resolves it.
create table match_protests (
    id          bigserial     primary key,
    match_id    bigint        not null,
    team_id     bigint        not null,
    author_id   bigint        not null,
    facts       varchar(2000) not null,
    request     varchar(1000) not null,
    status      varchar(20)   not null,
    resolution  varchar(2000),
    created_at  timestamp     not null,
    resolved_at timestamp,
    constraint fk_protests_match foreign key (match_id) references matches (id),
    constraint fk_protests_team foreign key (team_id) references teams (id),
    constraint fk_protests_author foreign key (author_id) references users (id),
    constraint ck_protests_status check (status in ('OPEN', 'RESOLVED'))
);
