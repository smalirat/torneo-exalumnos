-- Players the Asociación de Exalumnos does not allow in the tournament (by DNI): they cannot be added to a roster.
create table banned_people (
    id         bigserial    primary key,
    dni        varchar(10)  not null,
    full_name  varchar(255),
    reason     varchar(500),
    created_at timestamp    not null,
    constraint uq_banned_people_dni unique (dni)
);
