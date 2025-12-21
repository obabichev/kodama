drop table if exists events;

create table if not exists events
(
    id                   integer primary key not null,
    event_date           date                not null,
    event_time           time                not null,
    created_at           timestamp           not null,
    scheduled_for        timestamp,
    event_timestamp      timestamptz         not null,
    reminder_time        timetz              not null,
    duration             interval            not null,
    optional_duration    interval
);
