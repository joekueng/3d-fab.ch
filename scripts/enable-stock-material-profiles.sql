-- Run only after deploying the matching backend/frontend and profile bundles.
-- The backend/frontend must both enforce TPU 85A's 0.6 mm quick/advanced settings.
-- PC-ABS uses the explicitly reviewed initial Polymaker recipe; calibrate its
-- 3.2 mm3/s starting flow limit against workshop prints when refining pricing.
-- Existing custom/disabled mappings and all inventory/prices are preserved.
do $$
declare
    sequence_name text;
    highest_id bigint;
    sequence_value bigint;
begin
    lock table public.material_orca_profile_map in share row exclusive mode;
    if (select count(*) from public.printer_machine_profile p
        join public.printer_machine pm using (printer_machine_id)
        where pm.is_active and p.is_active
          and p.orca_machine_profile_name in ('Bambu Lab P2S 0.4 nozzle', 'Bambu Lab P2S 0.6 nozzle')) <> 2 then
        raise exception 'Expected active P2S 0.4 and 0.6 profiles';
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
        ('TPU 85A', 'Bambu Lab P2S 0.6 nozzle', 'Bambu TPU 85A @BBL P2S')
    ) recipe(material, machine, filament)
    join public.filament_material_type m on upper(trim(m.material_code)) = recipe.material
    join public.printer_machine_profile p on p.orca_machine_profile_name = recipe.machine and p.is_active
    join public.printer_machine pm using (printer_machine_id)
    where pm.is_active
    on conflict (printer_machine_profile_id, filament_material_type_id) do nothing;
end $$;
