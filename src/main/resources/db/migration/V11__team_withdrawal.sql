-- A team can withdraw from an edition ("se dio de baja"): it stays in the table and history, and its remaining
-- matches are 3-0 walkovers flagged as withdrawal (matches.withdrawal). Withdrawal walkovers count in the
-- table like any walkover but the public home does not show them as "last/next round".
alter table registrations add column withdrawn boolean not null default false;
alter table registrations add column withdrawn_at date;
alter table matches add column withdrawal boolean not null default false;

-- Walkovers already loaded with the Excel note "X se dio de baja"
update matches set withdrawal = true
where status = 'FINISHED' and (home_present = false or away_present = false)
  and lower(notes) like '%se dio de baja%';

update registrations r set withdrawn = true
where exists (select 1 from matches m
              where m.withdrawal and m.tournament_id = r.tournament_id
                and ((m.home_team_id = r.team_id and m.home_present = false)
                  or (m.away_team_id = r.team_id and m.away_present = false)));
