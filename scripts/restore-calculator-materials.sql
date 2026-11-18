-- Restore missing ordinary material maps on the already configured active A1.
-- Atomic and repeatable; preserve existing maps (including disabled/custom rows),
-- stock, variants, prices, machine activation and all technical recipes.
do $$
declare
    sequence_name text;
    highest_id bigint;
    sequence_value bigint;
begin
    lock table public.material_orca_profile_map in share row exclusive mode;
    if not exists (
        select 1 from public.printer_machine_profile p
        join public.printer_machine pm using (printer_machine_id)
        where pm.is_active and p.is_active
          and p.orca_machine_profile_name = 'Bambu Lab A1 0.4 nozzle'
    ) then
        raise exception 'An active A1 0.4 profile is required; do not change fleet activation automatically';
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
    select p.printer_machine_profile_id, m.filament_material_type_id, f.profile_name, true
    from (values
        ('PLA', 'Bambu PLA Basic @BBL A1'),
        ('PETG', 'Generic PETG @BBL A1'),
        ('PLA-CF', 'Generic PLA-CF @BBL A1'),
        ('TPU 95A', 'Bambu TPU 95A HF @BBL A1')
    ) f(material_code, profile_name)
    join public.filament_material_type m on upper(trim(m.material_code)) = f.material_code
    cross join public.printer_machine_profile p
    join public.printer_machine pm using (printer_machine_id)
    where pm.is_active and p.is_active
      and p.orca_machine_profile_name in ('Bambu Lab A1 0.4 nozzle', 'Bambu Lab A1 0.6 nozzle')
      and exists (select 1 from public.filament_variant v
                  where v.filament_material_type_id = m.filament_material_type_id and v.is_active)
    on conflict (printer_machine_profile_id, filament_material_type_id) do nothing;
end $$;

select m.material_code, count(*) as active_mappings
from public.filament_material_type m
join public.material_orca_profile_map mp using (filament_material_type_id)
join public.printer_machine_profile p using (printer_machine_profile_id)
join public.printer_machine pm using (printer_machine_id)
where pm.is_active and p.is_active and mp.is_active
group by m.material_code order by m.material_code;
