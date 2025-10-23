DO
$$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_type WHERE typname = 'roof_covering_type') THEN
            CREATE TYPE roof_covering_type AS ENUM (
                'roof_ardoise',
                'roof_asphalte_bitume',
                'roof_bac_acier',
                'roof_beton_brut',
                'roof_fibro_ciment',
                'roof_gravier',
                'roof_membrane_synthetique',
                'roof_tole_ondulee',
                'roof_tuiles',
                'roof_zinc'
                );
        END IF;
    END
$$;

ALTER TABLE IF EXISTS "detected_tile"
    ADD COLUMN IF NOT EXISTS primary_roof_covering_type   roof_covering_type,
    ADD COLUMN IF NOT EXISTS primary_roof_covering_area   numeric,
    ADD COLUMN IF NOT EXISTS secondary_roof_covering_type roof_covering_type,
    ADD COLUMN IF NOT EXISTS secondary_roof_covering_area numeric;
