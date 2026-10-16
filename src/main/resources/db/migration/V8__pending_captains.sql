-- Every team has exactly one captain user, and a captain has a single team. Teams created by the Excel import
-- (or by an admin) get a "pending" captain: no usable password, cannot log in, until someone claims it with
-- the invitation link (invite_token) and picks a username and password.
alter table users add column pending boolean not null default false;
alter table users add column invite_token varchar(64);
alter table users add constraint uk_users_invite_token unique (invite_token);

-- A captain with several teams keeps the oldest; the others get their own pending captain below
update teams t set captain_id = null
where exists (select 1 from teams o where o.captain_id = t.captain_id and o.id < t.id);

insert into users (username, password_hash, role, pending)
select 'pendiente-' || t.id, '!', 'CAPTAIN', true from teams t where t.captain_id is null;

update teams t set captain_id = (select u.id from users u where u.username = 'pendiente-' || t.id)
where t.captain_id is null;

alter table teams alter column captain_id set not null;
alter table teams add constraint uk_teams_captain unique (captain_id);
