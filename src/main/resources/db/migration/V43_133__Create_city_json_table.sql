create table if not exists "city_json"
(
    id                 varchar primary key,
    s3_file_key        varchar not null,
    city_json_request_id varchar references "city_json_request"("id")
);
