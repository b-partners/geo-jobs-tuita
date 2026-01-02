create index if not exists detected_tile_zdj_job_id_idx on "detected_tile" (zdj_job_id);

create index if not exists detected_object_detected_tile_id_idx on "detected_object" (detected_tile_id);

create index if not exists detectable_object_type_object_id_idx on "detectable_object_type" (object_id);