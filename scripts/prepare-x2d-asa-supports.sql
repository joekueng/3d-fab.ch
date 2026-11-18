-- Run in DataGrip on each intended connection. Atomic, repeatable provisioning.
-- New machines stay INACTIVE until enable-x2d-asa-supports.sql is run after deployment.
-- Reference costing power, not measured PA12-CF/ASA consumption:
-- X2D 550 W = official steady-state PC at 25 C; P2S 200 W = official steady-state PLA.
-- https://bambulab.cn/zh-cn/x2d/specs
-- https://bambulab.cn/zh-cn/p2s/specs (checked 2026-11-10)
-- Fleet weight 1.000 is an initial weighting, not a discovered physical printer count.
-- Existing rows, custom mappings, prices, variants and stock are preserved.
do $$
declare
    r record;
    maximum_id bigint;
    sequence_value bigint;
    sequence_name text;
begin
    lock table public.printer_machine, public.printer_machine_profile,
               public.material_orca_profile_map in share row exclusive mode;

    -- Earlier manually seeded IDs may leave an identity sequence behind its table.
    -- Only move a sequence forward; retain any already higher allocated value.
    for r in select * from (values
        ('printer_machine', 'printer_machine_id'),
        ('printer_machine_profile', 'printer_machine_profile_id'),
        ('material_orca_profile_map', 'material_orca_profile_map_id')
    ) ids(table_name, id_column) loop
        execute format('select max(%I) from public.%I', r.id_column, r.table_name) into maximum_id;
        sequence_name := pg_get_serial_sequence('public.' || r.table_name, r.id_column);
        select s.last_value into sequence_value from pg_sequences s
        where s.schemaname = 'public' and s.sequencename = split_part(sequence_name, '.', 2);
        if maximum_id > coalesce(sequence_value, 0) then
            perform setval(sequence_name::regclass, maximum_id, true);
        end if;
    end loop;

    if (select count(*) from public.filament_material_type
        where regexp_replace(upper(material_code), '[[:space:]_-]', '', 'g')
              in ('PA12CF', 'PETCF', 'ASA')) <> 3 then
        raise exception 'Expected one existing material each for PA12-CF, PET-CF and ASA';
    end if;

    insert into public.printer_machine (
        printer_display_name, build_volume_x_mm, build_volume_y_mm, build_volume_z_mm,
        power_watts, fleet_weight, is_active
    )
    select p.name, 256, 256, p.height, p.watts, 1.000, false
    from (values ('BambuLab X2D', 260, 550), ('BambuLab P2S', 256, 200)) p(name, height, watts)
    where not exists (
        select 1 from public.printer_machine pm
        where replace(lower(pm.printer_display_name), ' ', '') = replace(lower(p.name), ' ', '')
    );

    if exists (
        select 1 from public.printer_machine
        where replace(lower(printer_display_name), ' ', '') in ('bambulabx2d', 'bambulabp2s')
        group by replace(lower(printer_display_name), ' ', '') having count(*) <> 1
    ) then
        raise exception 'Duplicate X2D/P2S printers; resolve before provisioning';
    end if;

    insert into public.printer_machine_profile (
        printer_machine_id, nozzle_diameter_mm, orca_machine_profile_name, is_default, is_active
    )
    select pm.printer_machine_id, n.nozzle,
           'Bambu Lab ' || case when replace(lower(pm.printer_display_name), ' ', '') = 'bambulabx2d'
                               then 'X2D' else 'P2S' end || n.suffix,
           n.is_default, true
    from public.printer_machine pm
    cross join (values (0.40::numeric, ' 0.4 nozzle', true),
                       (0.60::numeric, ' 0.6 nozzle', false)) n(nozzle, suffix, is_default)
    where replace(lower(pm.printer_display_name), ' ', '') in ('bambulabx2d', 'bambulabp2s')
    on conflict (printer_machine_id, nozzle_diameter_mm) do nothing;

    insert into public.material_orca_profile_map (
        printer_machine_profile_id, filament_material_type_id, orca_filament_profile_name, is_active
    )
    select p.printer_machine_profile_id, m.filament_material_type_id,
           case when replace(lower(pm.printer_display_name), ' ', '') = 'bambulabx2d' then
               case regexp_replace(upper(m.material_code), '[[:space:]_-]', '', 'g')
                   when 'PA12CF' then 'Generic PA-CF @BBL X2D'
                   when 'PETCF' then 'Bambu PET-CF @BBL X2D'
                   when 'ASA' then 'Generic ASA @BBL X2D'
               end || case when p.nozzle_diameter_mm = 0.40 then ' 0.4 nozzle' else '' end
           else
               case regexp_replace(upper(m.material_code), '[[:space:]_-]', '', 'g')
                   when 'PA12CF' then 'Generic PA-CF'
                   when 'PETCF' then 'Bambu PET-CF @BBL X1C'
                   when 'ASA' then 'Generic ASA'
               end
           end, true
    from public.printer_machine_profile p
    join public.printer_machine pm using (printer_machine_id)
    cross join public.filament_material_type m
    where replace(lower(pm.printer_display_name), ' ', '') in ('bambulabx2d', 'bambulabp2s')
      and p.nozzle_diameter_mm in (0.40, 0.60)
      and regexp_replace(upper(m.material_code), '[[:space:]_-]', '', 'g') in ('PA12CF', 'PETCF', 'ASA')
    on conflict (printer_machine_profile_id, filament_material_type_id) do nothing;
end $$;

select pm.printer_display_name, pm.is_active as printer_active, pm.power_watts, pm.fleet_weight,
       p.nozzle_diameter_mm, p.orca_machine_profile_name, p.is_active as profile_active,
       m.material_code, mp.orca_filament_profile_name, mp.is_active as mapping_active
from public.printer_machine pm
join public.printer_machine_profile p using (printer_machine_id)
join public.material_orca_profile_map mp using (printer_machine_profile_id)
join public.filament_material_type m using (filament_material_type_id)
where replace(lower(pm.printer_display_name), ' ', '') in ('bambulabx2d', 'bambulabp2s')
  and regexp_replace(upper(m.material_code), '[[:space:]_-]', '', 'g') in ('PA12CF', 'PETCF', 'ASA')
order by pm.printer_display_name, p.nozzle_diameter_mm, m.material_code;
