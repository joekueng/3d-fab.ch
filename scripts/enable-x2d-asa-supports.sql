-- Run in DataGrip ONLY AFTER deploying the backend, OrcaSlicer 2.4.2 and X2D profiles.
-- First run prepare-x2d-asa-supports.sql on this same intended connection.
-- This explicitly activates the prepared X2D and P2S machines, not disabled profiles/maps.
-- Verify costing power/fleet weights against your measurements before production activation.
-- Existing prices, stock, variants and custom mappings are preserved. No schema changes.
do $$
begin
    lock table public.printer_machine, public.printer_machine_profile,
               public.material_orca_profile_map in share row exclusive mode;
    if (select count(*) from public.printer_machine
        where replace(lower(printer_display_name), ' ', '') in ('bambulabx2d', 'bambulabp2s')) <> 2 then
        raise exception 'Expected exactly one prepared X2D and P2S machine';
    end if;
    if (select count(*) from public.printer_machine_profile p
        join public.printer_machine pm using (printer_machine_id)
        where replace(lower(pm.printer_display_name), ' ', '') in ('bambulabx2d', 'bambulabp2s')
          and p.is_active and p.nozzle_diameter_mm in (0.40, 0.60)
          and p.orca_machine_profile_name = 'Bambu Lab '
              || case when replace(lower(pm.printer_display_name), ' ', '') = 'bambulabx2d'
                      then 'X2D' else 'P2S' end
              || case when p.nozzle_diameter_mm = 0.40 then ' 0.4 nozzle' else ' 0.6 nozzle' end) <> 4 then
        raise exception 'Expected active X2D/P2S 0.4 and 0.6 profiles; review inactive/custom profiles';
    end if;
    if (select count(*) from public.material_orca_profile_map mp
        join public.printer_machine_profile p using (printer_machine_profile_id)
        join public.printer_machine pm using (printer_machine_id)
        join public.filament_material_type m using (filament_material_type_id)
        where replace(lower(pm.printer_display_name), ' ', '') in ('bambulabx2d', 'bambulabp2s')
          and p.nozzle_diameter_mm in (0.40, 0.60) and mp.is_active
          and nullif(trim(mp.orca_filament_profile_name), '') is not null
          and regexp_replace(upper(m.material_code), '[[:space:]_-]', '', 'g') in ('PA12CF', 'PETCF', 'ASA')) <> 12 then
        raise exception 'Expected twelve active material mappings; review disabled/missing mappings';
    end if;
    update public.printer_machine set is_active = true
    where replace(lower(printer_display_name), ' ', '') in ('bambulabx2d', 'bambulabp2s');
end $$;

select printer_machine_id, printer_display_name, power_watts, fleet_weight, is_active
from public.printer_machine
where replace(lower(printer_display_name), ' ', '') in ('bambulabx2d', 'bambulabp2s');
