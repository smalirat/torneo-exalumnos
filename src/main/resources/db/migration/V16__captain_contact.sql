-- Contact of a captain (email and phone): asked when signing up or claiming a team; the existing users
-- complete it later in "Mis datos". Only the user and the admin see it.
alter table users add column email varchar(120);
alter table users add column phone varchar(30);
