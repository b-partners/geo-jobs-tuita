DO
$$
    BEGIN
        IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'detection_step_name') THEN
            DROP TYPE detection_step_name;
        END IF;

        IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'detection_step_name_new') THEN
            ALTER TYPE detection_step_name_new RENAME TO detection_step_name;
        END IF;
    END
$$;
