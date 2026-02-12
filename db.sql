create table printer_machine
(
    printer_machine_id   bigserial primary key,
    printer_display_name text          not null unique,

    build_volume_x_mm    integer       not null check (build_volume_x_mm > 0),
    build_volume_y_mm    integer       not null check (build_volume_y_mm > 0),
    build_volume_z_mm    integer       not null check (build_volume_z_mm > 0),

    power_watts          integer       not null check (power_watts > 0),

    fleet_weight         numeric(6, 3) not null default 1.000,

    is_active            boolean       not null default true,
    created_at           timestamptz   not null default now()
);

create view printer_fleet_current as
select 1                  as fleet_id,
       case
           when sum(fleet_weight) = 0 then null
           else round(sum(power_watts * fleet_weight) / sum(fleet_weight))::integer
           end            as weighted_average_power_watts,
       max(build_volume_x_mm) as fleet_max_build_x_mm,
       max(build_volume_y_mm) as fleet_max_build_y_mm,
       max(build_volume_z_mm) as fleet_max_build_z_mm
from printer_machine
where is_active = true;



create table filament_material_type
(
    filament_material_type_id bigserial primary key,
    material_code             text    not null unique,        -- PLA, PETG, TPU, ASA...
    is_flexible               boolean not null default false, -- sì/no
    is_technical              boolean not null default false, -- sì/no
    technical_type_label      text                            -- es: "alta temperatura", "rinforzato", ecc.
);

create table filament_variant
(
    filament_variant_id       bigserial primary key,
    filament_material_type_id bigint         not null references filament_material_type (filament_material_type_id),

    variant_display_name      text           not null, -- es: "PLA Nero Opaco BrandX"
    color_name                text           not null, -- Nero, Bianco, ecc.
    is_matte                  boolean        not null default false,
    is_special                boolean        not null default false,

    cost_chf_per_kg           numeric(10, 2) not null,

    -- Stock espresso in rotoli anche frazionati
    stock_spools              numeric(6, 3)  not null default 0.000,
    spool_net_kg              numeric(6, 3)  not null default 1.000,

    is_active                 boolean        not null default true,
    created_at                timestamptz    not null default now(),

    unique (filament_material_type_id, variant_display_name)
);

-- (opzionale) kg disponibili calcolati
create view filament_variant_stock_kg as
select filament_variant_id,
       stock_spools,
       spool_net_kg,
       (stock_spools * spool_net_kg) as stock_kg
from filament_variant;



create table pricing_policy
(
    pricing_policy_id            bigserial primary key,

    policy_name                  text           not null,              -- es: "2026 Q1", "Default", ecc.

    -- validità temporale (consiglio: valid_to esclusiva)
    valid_from                   timestamptz    not null,
    valid_to                     timestamptz,

    electricity_cost_chf_per_kwh numeric(10, 6) not null,
    markup_percent               numeric(6, 3)  not null default 20.000,

    fixed_job_fee_chf            numeric(10, 2) not null default 0.00, -- "costo fisso"
    nozzle_change_base_fee_chf   numeric(10, 2) not null default 0.00, -- base cambio ugello, se vuoi
    cad_cost_chf_per_hour        numeric(10, 2) not null default 0.00,

    is_active                    boolean        not null default true,
    created_at                   timestamptz    not null default now()
);

create table pricing_policy_machine_hour_tier
(
    pricing_policy_machine_hour_tier_id bigserial primary key,
    pricing_policy_id                   bigint         not null references pricing_policy (pricing_policy_id),

    tier_start_hours                    numeric(10, 2) not null,
    tier_end_hours                      numeric(10, 2), -- null = infinito
    machine_cost_chf_per_hour           numeric(10, 2) not null,

    constraint chk_tier_start_non_negative check (tier_start_hours >= 0),
    constraint chk_tier_end_gt_start check (tier_end_hours is null or tier_end_hours > tier_start_hours)
);

create index idx_pricing_policy_validity
    on pricing_policy (valid_from, valid_to);

create index idx_pricing_tier_lookup
    on pricing_policy_machine_hour_tier (pricing_policy_id, tier_start_hours);


create table nozzle_option
(
    nozzle_option_id            bigserial primary key,
    nozzle_diameter_mm          numeric(4, 2)  not null unique, -- 0.4, 0.6, 0.8...

    owned_quantity              integer        not null default 0 check (owned_quantity >= 0),

    -- extra costo specifico oltre ad eventuale base fee della pricing_policy
    extra_nozzle_change_fee_chf numeric(10, 2) not null default 0.00,

    is_active                   boolean        not null default true,
    created_at                  timestamptz    not null default now()
);


create table layer_height_option
(
    layer_height_option_id bigserial primary key,
    layer_height_mm        numeric(5, 3) not null unique, -- 0.12, 0.20, 0.28...

    -- opzionale: moltiplicatore costo/tempo (es: 0.12 costa di più)
    time_multiplier        numeric(6, 3) not null default 1.000,

    is_active              boolean       not null default true
);

create table layer_height_profile
(
    layer_height_profile_id bigserial primary key,
    profile_name            text          not null unique, -- "Standard", "Fine", ecc.

    min_layer_height_mm     numeric(5, 3) not null,
    max_layer_height_mm     numeric(5, 3) not null,
    default_layer_height_mm numeric(5, 3) not null,

    time_multiplier         numeric(6, 3) not null default 1.000,

    constraint chk_layer_range check (max_layer_height_mm >= min_layer_height_mm)
);


begin;

set timezone = 'Europe/Zurich';

        is_active = excluded.is_active;


-- =========================================================
-- 1) Pricing policy (valori ESATTI da Excel)
-- Valid from: 2026-01-01, valid_to: NULL
-- =========================================================
insert into pricing_policy (
    policy_name,
    valid_from,
    valid_to,
    electricity_cost_chf_per_kwh,
    markup_percent,
    fixed_job_fee_chf,
    nozzle_change_base_fee_chf,
    cad_cost_chf_per_hour,
    is_active
) values (
             'Excel Tariffe 2026-01-01',
             '2026-01-01 00:00:00+01'::timestamptz,
             null,
             0.156,          -- Costo elettricità CHF/kWh (Excel)
             0.000,          -- Markup non specificato -> 0 (puoi cambiarlo dopo)
             1.00,           -- Costo fisso macchina CHF (Excel)
             0.00,           -- Base cambio ugello: non specificato -> 0
             25.00,          -- Tariffa CAD CHF/h (Excel)
             true
         )
on conflict do nothing;

-- scaglioni tariffa stampa (Excel)
insert into pricing_policy_machine_hour_tier (
    pricing_policy_id,
    tier_start_hours,
    tier_end_hours,
    machine_cost_chf_per_hour
)
select
    p.pricing_policy_id,
    tiers.tier_start_hours,
    tiers.tier_end_hours,
    tiers.machine_cost_chf_per_hour
from pricing_policy p
         cross join (
    values
        (0.00::numeric, 10.00::numeric, 2.00::numeric),   -- 0–10 h
        (10.00::numeric, 20.00::numeric, 1.40::numeric),  -- 10–20 h
        (20.00::numeric, null::numeric, 0.50::numeric)    -- >20 h
) as tiers(tier_start_hours, tier_end_hours, machine_cost_chf_per_hour)
where p.policy_name = 'Excel Tariffe 2026-01-01'
on conflict do nothing;


-- =========================================================
-- 2) Stampante: BambuLab A1
-- =========================================================
insert into printer_machine (
    printer_display_name,
    build_volume_x_mm,
    build_volume_y_mm,
    build_volume_z_mm,
    power_watts,
    fleet_weight,
    is_active
) values (
             'BambuLab A1',
             256,
             256,
             256,
             150,       -- hai detto "150, 140": qui ho messo 150
             1.000,
             true
         )
on conflict (printer_display_name) do update
    set
        build_volume_x_mm = excluded.build_volume_x_mm,
        build_volume_y_mm = excluded.build_volume_y_mm,
        build_volume_z_mm = excluded.build_volume_z_mm,
        power_watts = excluded.power_watts,
        fleet_weight = excluded.fleet_weight,
        is_active = excluded.is_active;


-- =========================================================
-- 3) Material types (da Excel) - per ora niente technical
-- =========================================================
insert into filament_material_type (
    material_code,
    is_flexible,
    is_technical,
    technical_type_label
) values
      ('PLA', false, false, null),
      ('PETG', false, false, null),
      ('TPU', true,  false, null),
      ('ABS', false, false, null),
      ('Nylon', false, false, null),
      ('Carbon PLA', false, false, null)
on conflict (material_code) do update
    set
        is_flexible = excluded.is_flexible,
        is_technical = excluded.is_technical,
        technical_type_label = excluded.technical_type_label;


-- =========================================================
-- 4) Filament variants (PLA colori) - costi da Excel
-- Excel: PLA = 18 CHF/kg, TPU = 42 CHF/kg (non inserito perché quantità non chiara)
-- Stock in "rotoli" (3 = 3 kg se spool_net_kg=1)
-- =========================================================

-- helper: ID PLA
with pla as (
    select filament_material_type_id
    from filament_material_type
    where material_code = 'PLA'
)
insert into filament_variant (
    filament_material_type_id,
    variant_display_name,
    color_name,
    is_matte,
    is_special,
    cost_chf_per_kg,
    stock_spools,
    spool_net_kg,
    is_active
)
select
    pla.filament_material_type_id,
    v.variant_display_name,
    v.color_name,
    v.is_matte,
    v.is_special,
    18.00,                 -- PLA da Excel
    v.stock_spools,
    1.000,
    true
from pla
         cross join (
    values
        ('PLA Bianco', 'Bianco', false, false, 3.000::numeric),
        ('PLA Nero', 'Nero', false, false, 3.000::numeric),
        ('PLA Blu', 'Blu', false, false, 1.000::numeric),
        ('PLA Arancione', 'Arancione', false, false, 1.000::numeric),
        ('PLA Grigio', 'Grigio', false, false, 1.000::numeric),
        ('PLA Grigio Scuro', 'Grigio scuro', false, false, 1.000::numeric),
        ('PLA Grigio Chiaro', 'Grigio chiaro', false, false, 1.000::numeric),
        ('PLA Viola', 'Viola', false, false, 1.000::numeric)
) as v(variant_display_name, color_name, is_matte, is_special, stock_spools)
on conflict (filament_material_type_id, variant_display_name) do update
    set
        color_name = excluded.color_name,
        is_matte = excluded.is_matte,
        is_special = excluded.is_special,
        cost_chf_per_kg = excluded.cost_chf_per_kg,
        stock_spools = excluded.stock_spools,
        spool_net_kg = excluded.spool_net_kg,
        is_active = excluded.is_active;


-- =========================================================
-- 5) Ugelli
-- 0.4 standard (0 extra), 0.6 con attivazione 50 CHF
-- =========================================================
insert into nozzle_option (
    nozzle_diameter_mm,
    owned_quantity,
    extra_nozzle_change_fee_chf,
    is_active
) values
      (0.40, 1, 0.00, true),
      (0.60, 1, 50.00, true)
on conflict (nozzle_diameter_mm) do update
    set
        owned_quantity = excluded.owned_quantity,
        extra_nozzle_change_fee_chf = excluded.extra_nozzle_change_fee_chf,
        is_active = excluded.is_active;


-- =========================================================
-- 6) Layer heights (opzioni)
-- =========================================================
insert into layer_height_option (
    layer_height_mm,
    time_multiplier,
    is_active
) values
      (0.080, 1.000, true),
      (0.120, 1.000, true),
      (0.160, 1.000, true),
      (0.200, 1.000, true),
      (0.240, 1.000, true),
      (0.280, 1.000, true)
on conflict (layer_height_mm) do update
    set
        time_multiplier = excluded.time_multiplier,
        is_active = excluded.is_active;

commit;




-- Sostituisci __MULTIPLIER__ con il tuo valore (es. 1.10)
update layer_height_option
set time_multiplier = 0.1
where layer_height_mm = 0.080;
