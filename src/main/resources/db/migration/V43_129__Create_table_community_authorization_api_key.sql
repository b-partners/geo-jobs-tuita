create table if not exists "community_authorization_api_key"
(
    id                               varchar primary key         default uuid_generate_v4(),
    key_value                        varchar not null,
    creation_datetime                timestamp without time zone default current_timestamp,
    id_community_authorization_owner varchar references "community_authorization" (id)
);

insert into community_authorization_api_key (key_value, id_community_authorization_owner)
select community_authorization.api_key, community_authorization.id
from community_authorization;