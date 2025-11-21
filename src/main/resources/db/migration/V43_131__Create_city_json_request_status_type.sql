DO
$$
    begin
        if not exists (select from pg_type where typname = 'city_json_request_status') then
            create type city_json_request_status as ENUM ('FINISHED', 'PROCESSING', 'FAILED');
        end if;
    end
$$;
