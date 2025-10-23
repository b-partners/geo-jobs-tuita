DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'detection_step_name') THEN
            CREATE TYPE detection_step_name AS ENUM (
                'CONFIGURING',
                'TILING',
                'MACHINE_DETECTION',
                'HUMAN_DETECTION',
                'GEO_JSON_CONVERSION'
                );
        END IF;
    END
$$;