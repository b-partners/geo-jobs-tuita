DO
$$
    BEGIN
        IF to_regclass('public.detection_step') IS NOT NULL THEN
            UPDATE detection_step
            SET name = 'REQUEST_ACCEPTED'
            WHERE name = 'CONFIGURING';

            UPDATE detection_step
            SET name = 'POST_PROCESSING'
            WHERE name IN ('HUMAN_DETECTION', 'GEO_JSON_CONVERSION');

            IF EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_name = 'detection_step'
                  AND column_name = 'name'
                  AND udt_name = 'detection_step_name'
            ) THEN
                ALTER TABLE detection_step
                    ALTER COLUMN name TYPE detection_step_name_new
                        USING name::text::detection_step_name_new;
            END IF;
        END IF;
    END
$$;
