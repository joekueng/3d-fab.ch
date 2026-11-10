-- Review and run in DataGrip AFTER deploying the OrcaSlicer 2.4.2 backend and X2D profiles.
-- Run separately against the intended local/remote connection. No schema changes.
-- DataGrip parameters: x2d_power_watts (measured costing power, positive integer),
-- x2d_fleet_weight (positive numeric fleet weight). No guessed machine costs are seeded.
-- Existing material classifications, variants, prices, stock and custom mappings are preserved.
begin;
lock table printer_machine in share row exclusive mode;

insert into printer_machine (
    printer_display_name, build_volume_x_mm, build_volume_y_mm, build_volume_z_mm,
    power_watts, fleet_weight, is_active
)
select 'BambuLab X2D', 256, 256, 261,
       cast(:x2d_power_watts as integer), cast(:x2d_fleet_weight as numeric), true
where cast(:x2d_power_watts as integer) > 0 and cast(:x2d_fleet_weight as numeric) > 0
  and not exists (
    select 1 from printer_machine where replace(lower(printer_display_name), ' ', '') = 'bambulabx2d'
  );

do $$
begin
    if (select count(*) from printer_machine
        where replace(lower(printer_display_name), ' ', '') = 'bambulabx2d') <> 1 then
        raise exception 'Expected exactly one X2D printer; check power/fleet inputs and duplicate records';
    end if;
end $$;

insert into printer_machine_profile (
    printer_machine_id, nozzle_diameter_mm, orca_machine_profile_name, is_default, is_active
)
select pm.printer_machine_id, p.nozzle, p.profile, p.is_default, true
from printer_machine pm
cross join (values
    (0.40::numeric, 'Bambu Lab X2D 0.4 nozzle', true),
    (0.60::numeric, 'Bambu Lab X2D 0.6 nozzle', false)
) p(nozzle, profile, is_default)
where replace(lower(pm.printer_display_name), ' ', '') = 'bambulabx2d'
on conflict (printer_machine_id, nozzle_diameter_mm) do nothing;

insert into material_orca_profile_map (
    printer_machine_profile_id, filament_material_type_id, orca_filament_profile_name, is_active
)
select p.printer_machine_profile_id, m.filament_material_type_id,
       case regexp_replace(upper(m.material_code), '[[:space:]_-]', '', 'g')
           when 'PA12CF' then 'Generic PA-CF @BBL X2D'
           when 'PETCF' then 'Bambu PET-CF @BBL X2D'
           when 'ASA' then 'Generic ASA @BBL X2D'
       end || case when p.nozzle_diameter_mm = 0.40 then ' 0.4 nozzle' else '' end,
       true
from printer_machine_profile p
join printer_machine pm using (printer_machine_id)
cross join filament_material_type m
where replace(lower(pm.printer_display_name), ' ', '') = 'bambulabx2d'
  and p.nozzle_diameter_mm in (0.40, 0.60)
  and regexp_replace(upper(m.material_code), '[[:space:]_-]', '', 'g') in ('PA12CF', 'PETCF', 'ASA')
on conflict (printer_machine_profile_id, filament_material_type_id) do nothing;

-- Review these rows. An intentionally inactive existing printer/profile is not reactivated.
-- PA12-CF/PET-CF should be classified Technical and have active stocked variants in the catalogue.
select pm.printer_display_name, pm.is_active as printer_active, p.nozzle_diameter_mm,
       p.orca_machine_profile_name, p.is_active as profile_active,
       m.material_code, m.is_technical, mp.orca_filament_profile_name, mp.is_active as mapping_active
from printer_machine pm
join printer_machine_profile p using (printer_machine_id)
left join material_orca_profile_map mp using (printer_machine_profile_id)
left join filament_material_type m using (filament_material_type_id)
where replace(lower(pm.printer_display_name), ' ', '') = 'bambulabx2d'
order by p.nozzle_diameter_mm, m.material_code;

commit;
