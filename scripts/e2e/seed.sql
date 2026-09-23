-- Synthetic, disposable reference data for the browser suite. This file is
-- intentionally small; it is never imported into an existing developer DB.
BEGIN;

INSERT INTO pricing_policy
  (policy_name, valid_from, electricity_cost_chf_per_kwh, markup_percent,
   fixed_job_fee_chf, nozzle_change_base_fee_chf, cad_cost_chf_per_hour, is_active)
VALUES ('E2E fixed policy', '2026-01-01 00:00:00+00', 0.156, 0, 1, 0, 25, true);

INSERT INTO pricing_policy_machine_hour_tier
  (pricing_policy_id, tier_start_hours, tier_end_hours, machine_cost_chf_per_hour)
SELECT pricing_policy_id, 0, NULL, 2 FROM pricing_policy WHERE policy_name = 'E2E fixed policy';

INSERT INTO printer_machine
  (printer_display_name, build_volume_x_mm, build_volume_y_mm, build_volume_z_mm,
   power_watts, fleet_weight, is_active)
VALUES ('BambuLab A1', 256, 256, 256, 150, 1, true);

INSERT INTO filament_material_type
  (material_code, is_flexible, is_technical)
VALUES ('PLA', false, false);

INSERT INTO filament_variant
  (filament_material_type_id, variant_display_name, color_name,
   color_label_it, color_label_en, color_label_de, color_label_fr,
   color_hex, finish_type, cost_chf_per_kg, stock_spools, spool_net_kg, is_active)
SELECT filament_material_type_id, 'E2E PLA Nero', 'Nero',
  'Nero', 'Black', 'Schwarz', 'Noir', '#1A1A1A', 'GLOSSY', 18, 10, 1, true
FROM filament_material_type WHERE material_code = 'PLA';

INSERT INTO nozzle_option
  (nozzle_diameter_mm, owned_quantity, extra_nozzle_change_fee_chf, is_active)
VALUES (0.40, 1, 0, true);

INSERT INTO nozzle_layer_height_option (nozzle_diameter_mm, layer_height_mm, is_active)
VALUES (0.40, 0.20, true);

INSERT INTO layer_height_option (layer_height_mm, time_multiplier, is_active)
VALUES (0.20, 1, true);

INSERT INTO infill_pattern (pattern_code, display_name, is_active)
VALUES ('grid', 'Grid', true);

INSERT INTO printer_machine_profile
  (printer_machine_id, nozzle_diameter_mm, orca_machine_profile_name, is_default, is_active)
SELECT printer_machine_id, 0.40, 'Bambu Lab A1 0.4 nozzle', true, true
FROM printer_machine WHERE printer_display_name = 'BambuLab A1';

INSERT INTO material_orca_profile_map
  (printer_machine_profile_id, filament_material_type_id, orca_filament_profile_name, is_active)
SELECT p.printer_machine_profile_id, m.filament_material_type_id, 'Bambu PLA Basic @BBL A1', true
FROM printer_machine_profile p
JOIN printer_machine pm ON pm.printer_machine_id = p.printer_machine_id
CROSS JOIN filament_material_type m
WHERE pm.printer_display_name = 'BambuLab A1' AND m.material_code = 'PLA';

-- Hibernate's update mode creates tables for these read-only entity mappings in
-- an empty database. Replace them with the calculated views used by the app.
DROP TABLE IF EXISTS printer_fleet_current;
CREATE VIEW printer_fleet_current AS
SELECT 1::bigint AS fleet_id,
  round(sum(power_watts * fleet_weight) / nullif(sum(fleet_weight), 0))::integer AS weighted_average_power_watts,
  max(build_volume_x_mm) AS fleet_max_build_x_mm,
  max(build_volume_y_mm) AS fleet_max_build_y_mm,
  max(build_volume_z_mm) AS fleet_max_build_z_mm
FROM printer_machine WHERE is_active = true;

DROP TABLE IF EXISTS filament_variant_stock_kg;
CREATE VIEW filament_variant_stock_kg AS
SELECT filament_variant_id, stock_spools, spool_net_kg,
  stock_spools * spool_net_kg AS stock_kg
FROM filament_variant;

CREATE VIEW quote_session_totals AS
SELECT qs.quote_session_id,
  qs.setup_cost_chf + coalesce(sum(qli.unit_price_chf * qli.quantity), 0) AS total_chf
FROM quote_sessions qs
LEFT JOIN quote_line_items qli ON qli.quote_session_id = qs.quote_session_id
  AND qli.status = 'READY'
GROUP BY qs.quote_session_id;

INSERT INTO shop_category
  (shop_category_id, slug, name, name_it, name_en, name_de, name_fr, description, is_active)
VALUES (gen_random_uuid(), 'e2e-objects', 'E2E objects', 'Oggetti test', 'Test objects', 'Testobjekte', 'Objets test',
  'Synthetic browser-test category', true);

INSERT INTO shop_product
  (shop_product_id, shop_category_id, slug, name, name_it, name_en, name_de, name_fr,
   excerpt, description, is_active, is_featured)
SELECT gen_random_uuid(), shop_category_id, 'e2e-cube', 'E2E cube', 'Cubo test', 'Test cube',
  'Testwürfel', 'Cube test', 'Small synthetic cube', 'Synthetic product for browser tests', true, true
FROM shop_category WHERE slug = 'e2e-objects';

INSERT INTO shop_product_variant
  (shop_product_variant_id, shop_product_id, sku, variant_label, color_name,
   color_label_it, color_label_en, color_label_de, color_label_fr,
   color_hex, internal_material_code, price_chf, is_default, is_active)
SELECT gen_random_uuid(), shop_product_id, 'E2E-CUBE-BLACK', 'Black PLA', 'Black',
  'Nero', 'Black', 'Schwarz', 'Noir', '#1A1A1A', 'PLA', 12.50, true, true
FROM shop_product WHERE slug = 'e2e-cube';

INSERT INTO quote_sessions
  (quote_session_id, status, session_type, pricing_version, material_code, nozzle_diameter_mm,
   layer_height_mm, infill_pattern, infill_percent, supports_enabled,
   setup_cost_chf, cad_hours, cad_hourly_rate_chf, expires_at, notes)
VALUES (gen_random_uuid(), 'CAD_ACTIVE', 'PRINT_QUOTE', 'e2e-v1', 'PLA', 0.40,
  0.20, 'grid', 20, false, 0, 1, 25, now() + interval '30 days', 'E2E CAD fixture');

INSERT INTO quote_line_items
  (quote_line_item_id, quote_session_id, status, line_item_type, original_filename, display_name,
   quantity, color_code, filament_variant_id, material_code,
   nozzle_diameter_mm, layer_height_mm, infill_pattern, infill_percent,
   supports_enabled, material_grams, unit_price_chf)
SELECT gen_random_uuid(), qs.quote_session_id, 'READY', 'PRINT_FILE', 'e2e-cube.stl', 'E2E cube',
  1, 'Nero', fv.filament_variant_id, 'PLA', 0.40, 0.20, 'grid', 20,
  false, 12, 12.50
FROM quote_sessions qs CROSS JOIN filament_variant fv
WHERE qs.notes = 'E2E CAD fixture' AND fv.variant_display_name = 'E2E PLA Nero';

INSERT INTO orders
  (order_id, source_type, status, customer_email, preferred_language,
   billing_customer_type, billing_first_name, billing_last_name,
   billing_address_line1, billing_zip, billing_city, billing_country_code,
   shipping_same_as_billing, currency, setup_cost_chf, shipping_cost_chf,
   discount_chf, subtotal_chf, is_cad_order, cad_total_chf, total_chf)
SELECT gen_random_uuid(), 'CALCULATOR', 'PENDING_PAYMENT',
  'pending-order-' || browser || '-' || attempt || '@example.test', 'it',
  'PRIVATE', 'E2E', 'Customer', 'Teststrasse 1', '8000', 'Zürich', 'CH',
  true, 'CHF', 0, 0, 0, 12.50, false, 0, 12.50
FROM (VALUES ('chromium'), ('firefox'), ('webkit'), ('mobile-chromium'),
  ('mobile-webkit')) AS browsers(browser) CROSS JOIN generate_series(0, 1) AS attempt;

INSERT INTO order_items
  (order_item_id, order_id, item_type, original_filename, stored_relative_path, stored_filename,
   material_code, filament_variant_id, color_code, quantity, unit_price_chf, line_total_chf)
SELECT gen_random_uuid(), o.order_id, 'PRINT_FILE', 'e2e-cube.stl', 'e2e/fixture/e2e-cube.stl',
  'e2e-cube.stl', 'PLA', fv.filament_variant_id, 'Nero', 1, 12.50, 12.50
FROM orders o CROSS JOIN filament_variant fv
WHERE o.customer_email LIKE 'pending-order-%@example.test'
  AND fv.variant_display_name = 'E2E PLA Nero';

INSERT INTO payments (payment_id, order_id, method, status, currency, amount_chf)
SELECT gen_random_uuid(), order_id, 'OTHER', 'PENDING', 'CHF', 12.50
FROM orders WHERE customer_email LIKE 'pending-order-%@example.test';

INSERT INTO orders
  (order_id, source_type, status, customer_email, preferred_language,
   billing_customer_type, billing_first_name, billing_last_name,
   billing_address_line1, billing_zip, billing_city, billing_country_code,
   shipping_same_as_billing, currency, setup_cost_chf, shipping_cost_chf,
   discount_chf, subtotal_chf, is_cad_order, cad_hours, cad_hourly_rate_chf,
   cad_total_chf, total_chf)
VALUES (gen_random_uuid(), 'CALCULATOR', 'PENDING_PAYMENT', 'cad-order@example.test', 'it',
  'PRIVATE', 'E2E', 'CAD Customer', 'Teststrasse 2', '8000', 'Zürich', 'CH',
  true, 'CHF', 0, 0, 0, 25, true, 1, 25, 25, 50);

WITH cad_order AS (
  SELECT order_id FROM orders WHERE customer_email = 'cad-order@example.test'
), cad_items AS (
  SELECT cad_order.order_id, gen_random_uuid() AS item_id, f.name, f.price
  FROM cad_order CROSS JOIN (VALUES ('e2e-part-a.stl', 10.00::numeric),
    ('e2e-part-b.stl', 15.00::numeric)) AS f(name, price)
)
INSERT INTO order_items
  (order_item_id, order_id, item_type, original_filename,
   stored_relative_path, stored_filename, material_code, filament_variant_id,
   color_code, quantity, unit_price_chf, line_total_chf)
SELECT item_id, order_id, 'PRINT_FILE', name,
  'orders/' || order_id || '/3d-files/' || item_id || '/' || name,
  name, 'PLA', fv.filament_variant_id, 'Nero', 1, price, price
FROM cad_items CROSS JOIN filament_variant fv
WHERE fv.variant_display_name = 'E2E PLA Nero';

INSERT INTO payments (payment_id, order_id, method, status, currency, amount_chf)
SELECT gen_random_uuid(), order_id, 'OTHER', 'PENDING', 'CHF', 50
FROM orders WHERE customer_email = 'cad-order@example.test';

COMMIT;
