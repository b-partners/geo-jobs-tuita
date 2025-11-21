ALTER TABLE IF EXISTS "detection"
    ADD COLUMN IF NOT EXISTS "to_notify" boolean DEFAULT false;