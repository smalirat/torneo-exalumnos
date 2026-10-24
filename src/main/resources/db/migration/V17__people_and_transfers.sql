-- A person (who someone is: name and DNI) apart from their file in a team's roster (players). A person can be in
-- several teams over time, but in one at a time: a transfer closes the old file (left_at) and opens a new one.
-- Goals, cards, sanctions and stats keep pointing to the file, so they stay with the team they were made for.
create table people (
    id        bigserial    primary key,
    full_name varchar(255),
    dni       varchar(10),
    player_id bigint
);
create unique index uq_people_dni on people (dni);

-- Every existing player becomes its own person (with its DNI, if it had one)
insert into people (full_name, dni, player_id) select full_name, dni, id from players;

alter table players add column person_id bigint;
update players set person_id = (select pe.id from people pe where pe.player_id = players.id);
alter table players alter column person_id set not null;
alter table players add constraint fk_players_person foreign key (person_id) references people (id);
create index ix_players_person on players (person_id);
alter table people drop column player_id;

-- The DNI lives in the person now
drop index uq_players_dni;
alter table players drop column dni;

-- When the file opened and closed (null = still in the roster)
alter table players add column joined_at date;
alter table players add column left_at date;
