-- Read-only export: technical settings only; no orders, customers or credentials.
SELECT json_build_object(
  'materials', (
    SELECT json_agg(row_to_json(x)) FROM (
      SELECT m.material_code AS code, m.is_technical AS technical
      FROM filament_material_type m
      WHERE EXISTS (SELECT 1 FROM filament_variant v
                    WHERE v.filament_material_type_id=m.filament_material_type_id AND v.is_active)
      ORDER BY m.material_code
    ) x
  ),
  'machines', (
    SELECT json_agg(row_to_json(x)) FROM (
      SELECT p.printer_machine_profile_id AS id, pm.printer_display_name AS printer,
             p.nozzle_diameter_mm AS nozzle, p.orca_machine_profile_name AS profile
      FROM printer_machine_profile p JOIN printer_machine pm USING (printer_machine_id)
      WHERE p.is_active AND pm.is_active
      ORDER BY pm.printer_machine_id,p.nozzle_diameter_mm
    ) x
  ),
  'mappings', (
    SELECT json_agg(row_to_json(x)) FROM (
      SELECT mp.printer_machine_profile_id AS machine, m.material_code AS material,
             mp.orca_filament_profile_name AS filament
      FROM material_orca_profile_map mp JOIN filament_material_type m USING (filament_material_type_id)
      WHERE mp.is_active
    ) x
  ),
  'layers', (
    SELECT json_agg(row_to_json(x)) FROM (
      SELECT n.nozzle_diameter_mm AS nozzle,n.layer_height_mm AS layer
      FROM nozzle_layer_height_option n
      WHERE n.is_active AND EXISTS (SELECT 1 FROM nozzle_option o
                                    WHERE o.is_active AND o.nozzle_diameter_mm=n.nozzle_diameter_mm)
      ORDER BY 1,2
    ) x
  ),
  'variantOverrideCount', (SELECT count(*) FROM filament_variant_orca_override WHERE is_active)
);
