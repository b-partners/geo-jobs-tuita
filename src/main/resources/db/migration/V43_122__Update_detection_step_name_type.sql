DO
$$
    BEGIN
        -- 1. Creates the new type if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM pg_type WHERE typname = 'detection_step_name_new'
        ) THEN
            CREATE TYPE detection_step_name_new AS ENUM (
                'REQUEST_ACCEPTED',
                'TILING',
                'MACHINE_DETECTION',
                'POST_PROCESSING'
                );
        END IF;

        IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'detection_step_name') THEN
            ALTER TYPE detection_step_name ADD VALUE 'REQUEST_ACCEPTED';
            ALTER TYPE detection_step_name ADD VALUE 'POST_PROCESSING';
        END IF;
    END
$$;
