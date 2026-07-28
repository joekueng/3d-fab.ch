DROP VIEW public.printer_fleet_current;

CREATE VIEW public.printer_fleet_current AS
SELECT
    1::bigint AS fleet_id,
    CASE
        WHEN sum(printer_machine.fleet_weight) = 0::numeric THEN NULL::integer
        ELSE round(
            sum(printer_machine.power_watts::numeric * printer_machine.fleet_weight)
            / sum(printer_machine.fleet_weight)
        )::integer
    END AS weighted_average_power_watts,
    max(printer_machine.build_volume_x_mm) AS fleet_max_build_x_mm,
    max(printer_machine.build_volume_y_mm) AS fleet_max_build_y_mm,
    max(printer_machine.build_volume_z_mm) AS fleet_max_build_z_mm
FROM public.printer_machine
WHERE printer_machine.is_active = true;
