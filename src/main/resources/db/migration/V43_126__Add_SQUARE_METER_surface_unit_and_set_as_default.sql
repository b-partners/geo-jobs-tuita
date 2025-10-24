alter type surface_unit add value if not exists 'SQUARE_METER';

alter table community_authorization
    alter column max_surface_unit set default 'SQUARE_METER';