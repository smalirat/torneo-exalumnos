-- Editions move by hand: DRAFT -> OPEN (captains register) -> LIVE (played) -> COMPLETE, and only one can be
-- active (OPEN or LIVE) at a time. "Registration closed" goes away: starting the tournament closes it.
-- Registration dates stay, only as information.
update tournaments set status = 'LIVE' where status = 'CLOSED';
update tournaments set status = 'DRAFT' where status is null;

-- Keep active only the newest edition (season year, then Clausura over Apertura, then newest created)
update tournaments set status = 'COMPLETE'
where status in ('OPEN', 'LIVE')
  and id <> (
    select t.id from tournaments t left join seasons s on s.id = t.season_id
    where t.status in ('OPEN', 'LIVE')
    order by coalesce(s.season_year, 0) desc,
             case t.stage when 'CLAUSURA' then 2 when 'APERTURA' then 1 else 0 end desc,
             t.id desc
    limit 1);

alter table tournaments drop constraint ck_tournaments_status;
alter table tournaments alter column status set not null;
alter table tournaments add constraint ck_tournaments_status check (status in ('DRAFT', 'OPEN', 'LIVE', 'COMPLETE'));
