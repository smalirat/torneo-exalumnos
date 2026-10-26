-- When a player was asked for (captain) or added (admin): with the Thursday 18 hs deadline of the rules it says
-- from which match day they can play. Null for older players.
alter table players add column registered_at timestamp;
