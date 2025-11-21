create table if not exists "city_json_request"
(
    id                 varchar primary key,
    status             city_json_request_status,
    creation_datetime  timestamp with time zone default now()::timestamp with time zone,
    community_owner_id varchar references "community_authorization"(id),
    delimitations      jsonb not null
);