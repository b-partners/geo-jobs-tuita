alter table detection
    add column if not exists needs_image_output          boolean,
    add column if not exists polygon_geo_json_zone       jsonb,
    add column if not exists split_polygon_geo_json_zone jsonb;