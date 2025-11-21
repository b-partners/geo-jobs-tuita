alter table "community_authorization"
    add column if not exists dashboard_api_key varchar;

update "community_authorization"
set dashboard_api_key=api_key
where dashboard_api_key is null;

alter table "community_authorization"
    alter column dashboard_api_key set not null;

alter table "community_authorization"
    alter column api_key drop not null;