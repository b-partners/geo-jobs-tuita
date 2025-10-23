alter table if exists "detection"
    add column if not exists "creation_datetime" timestamp without time zone;