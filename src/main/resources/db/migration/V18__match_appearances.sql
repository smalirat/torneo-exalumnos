-- Who played a match (ticked by the admin from the paper match sheet). Players with goals or cards played too.
-- Used to know whether someone already played an edition for a team (transfers).
create table match_appearances (
    id        bigserial primary key,
    match_id  bigint    not null,
    player_id bigint    not null,
    team_id   bigint    not null,
    constraint uq_match_appearances unique (match_id, player_id),
    constraint fk_appearances_match foreign key (match_id) references matches (id),
    constraint fk_appearances_player foreign key (player_id) references players (id),
    constraint fk_appearances_team foreign key (team_id) references teams (id)
);
create index ix_appearances_player on match_appearances (player_id);
