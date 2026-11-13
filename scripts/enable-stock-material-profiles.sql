-- Run only after deploying the matching backend/frontend and profile bundles.
-- The backend/frontend must both allow TPU 85A's 0.6/0.8 mm settings.
-- PC-ABS uses the explicitly reviewed initial Polymaker recipe; calibrate its
-- 3.2 mm3/s starting flow limit against workshop prints when refining pricing.
-- Existing custom/disabled mappings and all inventory/prices are preserved.
do $$
declare
    sequence_name text;
    highest_id bigint;
    sequence_value bigint;
    r record;
begin
    lock table public.material_orca_profile_map, public.printer_machine_profile,
               public.nozzle_layer_height_option in share row exclusive mode;
    for r in select * from (values
        ('printer_machine_profile', 'printer_machine_profile_id'),
        ('nozzle_layer_height_option', 'nozzle_layer_height_option_id')
    ) ids(table_name, id_column) loop
        sequence_name := pg_get_serial_sequence('public.' || r.table_name, r.id_column);
        execute format('select max(%I) from public.%I', r.id_column, r.table_name) into highest_id;
        select last_value into sequence_value from pg_sequences
        where schemaname = 'public' and sequencename = split_part(sequence_name, '.', 2);
        if highest_id > coalesce(sequence_value, 0) then
            perform setval(sequence_name::regclass, highest_id, true);
        end if;
    end loop;
    insert into public.printer_machine_profile (
        printer_machine_id, nozzle_diameter_mm, orca_machine_profile_name, is_default, is_active
    )
    select printer_machine_id, 0.80, 'Bambu Lab P2S 0.8 nozzle', false, true
    from public.printer_machine
    where is_active and replace(lower(printer_display_name), ' ', '') = 'bambulabp2s'
    on conflict (printer_machine_id, nozzle_diameter_mm) do nothing;
    insert into public.nozzle_layer_height_option(nozzle_diameter_mm, layer_height_mm, is_active)
    values (0.80, 0.240, true)
    on conflict (nozzle_diameter_mm, layer_height_mm) do nothing;
    if (select count(*) from public.printer_machine_profile p
        join public.printer_machine pm using (printer_machine_id)
        where pm.is_active and p.is_active
          and p.orca_machine_profile_name in ('Bambu Lab P2S 0.4 nozzle', 'Bambu Lab P2S 0.6 nozzle', 'Bambu Lab P2S 0.8 nozzle')) <> 3 then
        raise exception 'Expected active P2S 0.4, 0.6 and 0.8 profiles';
    end if;
    if not exists (select 1 from public.nozzle_layer_height_option
                   where nozzle_diameter_mm = 0.80 and layer_height_mm = 0.240 and is_active) then
        raise exception 'The 0.8 mm / 0.24 mm layer rule is disabled; review it explicitly';
    end if;
    sequence_name := pg_get_serial_sequence('public.material_orca_profile_map', 'material_orca_profile_map_id');
    select max(material_orca_profile_map_id) into highest_id from public.material_orca_profile_map;
    select last_value into sequence_value from pg_sequences
    where schemaname = 'public' and sequencename = split_part(sequence_name, '.', 2);
    if highest_id > coalesce(sequence_value, 0) then
        perform setval(sequence_name::regclass, highest_id, true);
    end if;
    insert into public.material_orca_profile_map (
        printer_machine_profile_id, filament_material_type_id, orca_filament_profile_name, is_active
    )
    select p.printer_machine_profile_id, m.filament_material_type_id, recipe.filament, true
    from (values
        ('PC-ABS', 'Bambu Lab P2S 0.4 nozzle', 'Polymaker PC-ABS @BBL P2S'),
        ('PC-ABS', 'Bambu Lab P2S 0.6 nozzle', 'Polymaker PC-ABS @BBL P2S'),
        ('TPU 85A', 'Bambu Lab P2S 0.6 nozzle', 'Bambu TPU 85A @BBL P2S'),
        ('TPU 85A', 'Bambu Lab P2S 0.8 nozzle', 'Bambu TPU 85A @BBL P2S 0.8 nozzle')
    ) recipe(material, machine, filament)
    join public.filament_material_type m on upper(trim(m.material_code)) = recipe.material
    join public.printer_machine_profile p on p.orca_machine_profile_name = recipe.machine and p.is_active
    join public.printer_machine pm using (printer_machine_id)
    where pm.is_active
    on conflict (printer_machine_profile_id, filament_material_type_id) do nothing;
end $$;
