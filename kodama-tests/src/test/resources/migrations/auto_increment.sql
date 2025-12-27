-- Auto-increment test tables for SERIAL and IDENTITY support

-- SERIAL test table (PostgreSQL-specific auto-increment)
drop table if exists serial_test;

create table if not exists serial_test
(
    id    SERIAL primary key,
    name  varchar(255) not null,
    value integer      not null
);

-- IDENTITY test table (SQL standard auto-increment)
drop table if exists identity_test;

create table if not exists identity_test
(
    id    integer GENERATED ALWAYS AS IDENTITY primary key,
    name  varchar(255) not null,
    value integer      not null
);

-- BIGSERIAL test table (large auto-increment IDs)
drop table if exists bigserial_test;

create table if not exists bigserial_test
(
    id          BIGSERIAL primary key,
    description varchar(255) not null
);

-- SMALLSERIAL test table (small auto-increment IDs)
drop table if exists smallserial_test;

create table if not exists smallserial_test
(
    id  SMALLSERIAL primary key,
    tag varchar(50) not null
);
