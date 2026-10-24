-- DNI of a player: it identifies the person, so it is unique (the same DNI twice is the same player).
-- Optional for now: the players imported from the Excel have none (several nulls are allowed by a unique index).
alter table players add column dni varchar(10);
create unique index uq_players_dni on players (dni);
