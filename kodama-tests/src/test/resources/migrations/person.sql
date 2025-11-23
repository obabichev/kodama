drop table if exists person;

create table if not exists person
(
    name text primary key not null,
    age  integer          not null
);

insert into person values ('kodama', 1);

insert into person values ('kokoro', 2);

