do
$$
    begin
        if not exists (select from pg_type where typname = 'detectable_model_name') then
            create type detectable_model_name as ENUM ('BP_TOITURE', 'BP_LOM', 'BP_ZAN', 'BP_CLIMAT_RESILIENCE', 'BP_CONFIRMITE_PLU', 'BP_TROTTOIRS', 'BP_OLD');
        end if;
    end
$$;

alter table if exists community_authorization
    add column if not exists detectable_models text[];