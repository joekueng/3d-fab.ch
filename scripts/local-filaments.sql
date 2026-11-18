-- Local test fixtures only: sample costs/stock are not a production price list.
-- Run through seed-local-filaments.sh after the local schema/printers are ready.
begin;

create temporary table local_filament_fixture (
    material_code text,
    is_flexible boolean,
    is_technical boolean,
    technical_type_label text,
    cost_chf_per_kg numeric,
    orca_profile text
) on commit drop;

insert into local_filament_fixture values
    ('ABS', false, false, null, 25.00, 'Generic ABS'),
    ('CARBON PLA', false, true, null, 40.00, 'Generic PLA-CF'),
    ('Nylon', false, false, null, 45.00, 'Generic PA'),
    ('PA12-CF', false, true, 'High temperature resistance', 80.00, 'Generic PA-CF'),
    ('PC', false, true, 'engineering', 48.00, 'Generic PC'),
    ('PETG', false, false, null, 24.00, 'Generic PETG'),
    ('PLA', false, false, null, 18.00, 'Generic PLA'),
    ('PLA TOUGH', false, false, null, 18.00, 'Bambu PLA Tough @BBL A1'),
    ('TPU', true, false, null, 42.00, 'Generic TPU');

-- Case-insensitive matching also reuses the legacy "Carbon PLA" material.
-- Existing classifications and technical labels belong to the administrator.
insert into filament_material_type (material_code, is_flexible, is_technical, technical_type_label)
select f.material_code, f.is_flexible, f.is_technical, f.technical_type_label
from local_filament_fixture f
where not exists (
    select 1 from filament_material_type m
    where upper(trim(m.material_code)) = upper(f.material_code)
);

-- Add one stocked black variant only when a material has no active variants.
-- Preserve all existing colors, prices, stock and inactive variants.
insert into filament_variant (
    filament_material_type_id, variant_display_name, color_name,
    color_label_it, color_label_en, color_label_de, color_label_fr,
    color_hex, finish_type, is_matte, is_special,
    cost_chf_per_kg, stock_spools, spool_net_kg, is_active
)
select m.filament_material_type_id, m.material_code || ' Nero (test locale)', 'Nero',
       'Nero', 'Black', 'Schwarz', 'Noir', '#1A1A1A', 'GLOSSY', false, false,
       f.cost_chf_per_kg, 1.000, 1.000, true
from local_filament_fixture f
join filament_material_type m on upper(trim(m.material_code)) = upper(f.material_code)
where not exists (
    select 1 from filament_variant v
    where v.filament_material_type_id = m.filament_material_type_id and v.is_active
)
on conflict (filament_material_type_id, variant_display_name) do nothing;

-- Calculator options require active material mappings as well as variants.
-- Generic profile names refer to the bundled BBL library. Existing mappings,
-- including deliberately disabled ones, are never overwritten.
insert into material_orca_profile_map (
    printer_machine_profile_id, filament_material_type_id, orca_filament_profile_name, is_active
)
select p.printer_machine_profile_id, m.filament_material_type_id, f.orca_profile, true
from local_filament_fixture f
join filament_material_type m on upper(trim(m.material_code)) = upper(f.material_code)
cross join printer_machine_profile p
join printer_machine pm on pm.printer_machine_id = p.printer_machine_id
where pm.printer_display_name in ('BambuLab A1', 'BambuLab P2S')
  and pm.is_active and p.is_active and p.nozzle_diameter_mm in (0.40, 0.60)
on conflict (printer_machine_profile_id, filament_material_type_id) do nothing;

select m.material_code, count(v.filament_variant_id) filter (where v.is_active) as active_variants
from filament_material_type m
left join filament_variant v using (filament_material_type_id)
group by m.filament_material_type_id
order by m.material_code;

commit;
