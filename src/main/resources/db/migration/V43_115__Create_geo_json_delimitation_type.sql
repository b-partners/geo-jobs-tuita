DO
$$
    begin
        if not exists (select from pg_type where typname = 'geo_json_delimitation_type') then
            create type geo_json_delimitation_type as ENUM ('ROOF', 'ZONE');
        end if;
    end
$$;
