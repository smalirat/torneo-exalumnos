-- A new user can ask to be the captain of a team that has none yet (a pending captain, e.g. from the Excel).
-- The request waits on the team until the admin approves (the user takes over) or rejects it.
alter table teams add column claim_user_id bigint;
alter table teams add column claim_requested_at timestamp(6);
alter table teams add constraint fk_teams_claim_user foreign key (claim_user_id) references users (id) on delete set null;
