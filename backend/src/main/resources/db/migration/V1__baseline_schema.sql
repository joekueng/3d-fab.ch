-- Print Calculator canonical schema baseline.
-- Generated from the local PostgreSQL 15 schema on 2026-07-28.
-- Existing environments must be compared with this file and explicitly
-- baselined at version 1 before Flyway is enabled there.


-- Dumped from database version 15.18
-- Dumped by pg_dump version 18.3


--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--





--
-- Name: custom_quote_request_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_quote_request_attachments (
    attachment_id uuid DEFAULT gen_random_uuid() NOT NULL,
    request_id uuid NOT NULL,
    original_filename text NOT NULL,
    stored_relative_path text NOT NULL,
    stored_filename text NOT NULL,
    file_size_bytes bigint,
    mime_type text,
    sha256_hex text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT custom_quote_request_attachments_file_size_bytes_check CHECK ((file_size_bytes >= 0))
);


--
-- Name: custom_quote_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_quote_requests (
    request_id uuid DEFAULT gen_random_uuid() NOT NULL,
    request_type text NOT NULL,
    customer_type text NOT NULL,
    email text NOT NULL,
    phone text,
    name text,
    company_name text,
    contact_person text,
    message text NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT custom_quote_requests_customer_type_check CHECK ((customer_type = ANY (ARRAY['PRIVATE'::text, 'COMPANY'::text]))),
    CONSTRAINT custom_quote_requests_status_check CHECK ((status = ANY (ARRAY['NEW'::text, 'PENDING'::text, 'IN_PROGRESS'::text, 'DONE'::text, 'CLOSED'::text])))
);


--
-- Name: customers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customers (
    customer_id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_type text NOT NULL,
    email text NOT NULL,
    phone text,
    first_name text,
    last_name text,
    company_name text,
    contact_person text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT customers_customer_type_check CHECK ((customer_type = ANY (ARRAY['PRIVATE'::text, 'COMPANY'::text])))
);


--
-- Name: filament_material_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.filament_material_type (
    filament_material_type_id bigint NOT NULL,
    material_code text NOT NULL,
    is_flexible boolean DEFAULT false NOT NULL,
    is_technical boolean DEFAULT false NOT NULL,
    technical_type_label text
);


--
-- Name: filament_material_type_filament_material_type_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.filament_material_type_filament_material_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: filament_material_type_filament_material_type_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.filament_material_type_filament_material_type_id_seq OWNED BY public.filament_material_type.filament_material_type_id;


--
-- Name: filament_variant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.filament_variant (
    filament_variant_id bigint NOT NULL,
    filament_material_type_id bigint NOT NULL,
    variant_display_name text NOT NULL,
    color_name text NOT NULL,
    is_matte boolean DEFAULT false NOT NULL,
    is_special boolean DEFAULT false NOT NULL,
    cost_chf_per_kg numeric(10,2) NOT NULL,
    stock_spools numeric(6,3) DEFAULT 0.000 NOT NULL,
    spool_net_kg numeric(6,3) DEFAULT 1.000 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    brand text,
    color_hex text,
    finish_type text DEFAULT 'GLOSSY'::text,
    color_label_de text,
    color_label_en text,
    color_label_fr text,
    color_label_it text
);


--
-- Name: filament_variant_filament_variant_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.filament_variant_filament_variant_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: filament_variant_filament_variant_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.filament_variant_filament_variant_id_seq OWNED BY public.filament_variant.filament_variant_id;


--
-- Name: filament_variant_orca_override; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.filament_variant_orca_override (
    filament_variant_orca_override_id bigint NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    orca_filament_profile_name text NOT NULL,
    filament_variant_id bigint NOT NULL,
    printer_machine_profile_id bigint NOT NULL
);


--
-- Name: filament_variant_orca_overrid_filament_variant_orca_overrid_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.filament_variant_orca_override ALTER COLUMN filament_variant_orca_override_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.filament_variant_orca_overrid_filament_variant_orca_overrid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: filament_variant_stock_kg; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.filament_variant_stock_kg AS
 SELECT filament_variant.filament_variant_id,
    filament_variant.stock_spools,
    filament_variant.spool_net_kg,
    (filament_variant.stock_spools * filament_variant.spool_net_kg) AS stock_kg
   FROM public.filament_variant;


--
-- Name: gridfinity_generation_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gridfinity_generation_job (
    gridfinity_generation_job_id uuid NOT NULL,
    clearance_mm numeric(10,2) NOT NULL,
    confidence_score numeric(5,4),
    contour_json_path text,
    contour_svg_path text,
    corrected_image_path text,
    created_at timestamp(6) with time zone NOT NULL,
    detected_a4 boolean,
    diagnostics_json text,
    grid_x integer,
    grid_y integer,
    holder_height_mm numeric(10,2),
    original_image_path text,
    requested_height_mm numeric(10,2) NOT NULL,
    requested_length_mm numeric(10,2) NOT NULL,
    requested_width_mm numeric(10,2) NOT NULL,
    scad_path text,
    status character varying(64) NOT NULL,
    stl_path text,
    updated_at timestamp(6) with time zone NOT NULL,
    user_id uuid
);


--
-- Name: gridfinity_generation_review; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gridfinity_generation_review (
    job_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    notes text,
    review_status character varying(32) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: home_project; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.home_project (
    home_project_id uuid NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    description_de text,
    description_en text,
    description_fr text,
    description_it text,
    eyebrow_de text,
    eyebrow_en text,
    eyebrow_fr text,
    eyebrow_it text,
    is_active boolean DEFAULT true NOT NULL,
    slug text NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    title_de text,
    title_en text,
    title_fr text,
    title_it text,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL
);


--
-- Name: infill_pattern; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infill_pattern (
    infill_pattern_id bigint NOT NULL,
    pattern_code text NOT NULL,
    display_name text NOT NULL,
    is_active boolean DEFAULT true NOT NULL
);


--
-- Name: infill_pattern_infill_pattern_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infill_pattern_infill_pattern_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infill_pattern_infill_pattern_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.infill_pattern_infill_pattern_id_seq OWNED BY public.infill_pattern.infill_pattern_id;


--
-- Name: layer_height_option; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.layer_height_option (
    layer_height_option_id bigint NOT NULL,
    layer_height_mm numeric(5,3) NOT NULL,
    time_multiplier numeric(6,3) DEFAULT 1.000 NOT NULL,
    is_active boolean DEFAULT true NOT NULL
);


--
-- Name: layer_height_option_layer_height_option_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.layer_height_option_layer_height_option_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: layer_height_option_layer_height_option_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.layer_height_option_layer_height_option_id_seq OWNED BY public.layer_height_option.layer_height_option_id;


--
-- Name: layer_height_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.layer_height_profile (
    layer_height_profile_id bigint NOT NULL,
    profile_name text NOT NULL,
    min_layer_height_mm numeric(5,3) NOT NULL,
    max_layer_height_mm numeric(5,3) NOT NULL,
    default_layer_height_mm numeric(5,3) NOT NULL,
    time_multiplier numeric(6,3) DEFAULT 1.000 NOT NULL,
    CONSTRAINT chk_layer_range CHECK ((max_layer_height_mm >= min_layer_height_mm))
);


--
-- Name: layer_height_profile_layer_height_profile_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.layer_height_profile_layer_height_profile_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: layer_height_profile_layer_height_profile_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.layer_height_profile_layer_height_profile_id_seq OWNED BY public.layer_height_profile.layer_height_profile_id;


--
-- Name: material_orca_profile_map; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.material_orca_profile_map (
    material_orca_profile_map_id bigint NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    orca_filament_profile_name text NOT NULL,
    filament_material_type_id bigint NOT NULL,
    printer_machine_profile_id bigint NOT NULL
);


--
-- Name: material_orca_profile_map_material_orca_profile_map_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.material_orca_profile_map ALTER COLUMN material_orca_profile_map_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.material_orca_profile_map_material_orca_profile_map_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: media_asset; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.media_asset (
    media_asset_id uuid NOT NULL,
    alt_text text,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    file_size_bytes bigint NOT NULL,
    height_px integer,
    mime_type text NOT NULL,
    original_filename text NOT NULL,
    sha256_hex text NOT NULL,
    status text NOT NULL,
    storage_key text NOT NULL,
    title text,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    visibility text NOT NULL,
    width_px integer
);


--
-- Name: media_usage; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.media_usage (
    media_usage_id uuid NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    owner_id uuid,
    sort_order integer DEFAULT 0 NOT NULL,
    usage_key text NOT NULL,
    usage_type text NOT NULL,
    media_asset_id uuid NOT NULL,
    alt_text_de text,
    alt_text_en text,
    alt_text_fr text,
    alt_text_it text,
    title_de text,
    title_en text,
    title_fr text,
    title_it text
);


--
-- Name: media_variant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.media_variant (
    media_variant_id uuid NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    file_size_bytes bigint NOT NULL,
    format text NOT NULL,
    height_px integer NOT NULL,
    is_generated boolean DEFAULT true NOT NULL,
    mime_type text NOT NULL,
    storage_key text NOT NULL,
    variant_name text NOT NULL,
    width_px integer NOT NULL,
    media_asset_id uuid NOT NULL
);


--
-- Name: nozzle_layer_height_option; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.nozzle_layer_height_option (
    nozzle_layer_height_option_id bigint NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    layer_height_mm numeric(5,3) NOT NULL,
    nozzle_diameter_mm numeric(4,2) NOT NULL
);


--
-- Name: nozzle_layer_height_option_nozzle_layer_height_option_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.nozzle_layer_height_option ALTER COLUMN nozzle_layer_height_option_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.nozzle_layer_height_option_nozzle_layer_height_option_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: nozzle_option; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.nozzle_option (
    nozzle_option_id bigint NOT NULL,
    nozzle_diameter_mm numeric(4,2) NOT NULL,
    owned_quantity integer DEFAULT 0 NOT NULL,
    extra_nozzle_change_fee_chf numeric(10,2) DEFAULT 0.00 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT nozzle_option_owned_quantity_check CHECK ((owned_quantity >= 0))
);


--
-- Name: nozzle_option_nozzle_option_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.nozzle_option_nozzle_option_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: nozzle_option_nozzle_option_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.nozzle_option_nozzle_option_id_seq OWNED BY public.nozzle_option.nozzle_option_id;


--
-- Name: order_deliverable_files; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_deliverable_files (
    order_deliverable_file_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    file_size_bytes bigint,
    mime_type text,
    original_filename text NOT NULL,
    stored_filename text NOT NULL,
    stored_relative_path text NOT NULL,
    order_id uuid NOT NULL
);


--
-- Name: order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_items (
    order_item_id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    original_filename text NOT NULL,
    stored_relative_path text NOT NULL,
    stored_filename text NOT NULL,
    file_size_bytes bigint,
    mime_type text,
    sha256_hex text,
    material_code text NOT NULL,
    color_code text,
    quantity integer DEFAULT 1 NOT NULL,
    print_time_seconds integer,
    material_grams numeric(12,2),
    unit_price_chf numeric(12,2) NOT NULL,
    line_total_chf numeric(12,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    bounding_box_x_mm numeric(10,3),
    bounding_box_y_mm numeric(10,3),
    bounding_box_z_mm numeric(10,3),
    filament_variant_id bigint,
    infill_pattern text,
    infill_percent integer,
    layer_height_mm numeric(5,3),
    nozzle_diameter_mm numeric(4,2),
    quality text,
    supports_enabled boolean,
    display_name text,
    item_type text DEFAULT 'PRINT_FILE'::text NOT NULL,
    shop_product_name text,
    shop_product_slug text,
    shop_variant_color_hex text,
    shop_variant_color_name text,
    shop_variant_label text,
    shop_product_id uuid,
    shop_product_variant_id uuid,
    requires_split_printing boolean DEFAULT false,
    CONSTRAINT order_items_file_size_bytes_check CHECK ((file_size_bytes >= 0)),
    CONSTRAINT order_items_line_total_chf_check CHECK ((line_total_chf >= (0)::numeric)),
    CONSTRAINT order_items_material_grams_check CHECK ((material_grams >= (0)::numeric)),
    CONSTRAINT order_items_print_time_seconds_check CHECK ((print_time_seconds >= 0)),
    CONSTRAINT order_items_quantity_check CHECK ((quantity >= 1)),
    CONSTRAINT order_items_unit_price_chf_check CHECK ((unit_price_chf >= (0)::numeric))
);


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    order_id uuid DEFAULT gen_random_uuid() NOT NULL,
    source_quote_session_id uuid,
    status text NOT NULL,
    customer_id uuid,
    customer_email text NOT NULL,
    customer_phone text,
    billing_customer_type text NOT NULL,
    billing_first_name text,
    billing_last_name text,
    billing_company_name text,
    billing_contact_person text,
    billing_address_line1 text NOT NULL,
    billing_address_line2 text,
    billing_zip text NOT NULL,
    billing_city text NOT NULL,
    billing_country_code character varying(2) DEFAULT 'CH'::bpchar NOT NULL,
    shipping_same_as_billing boolean DEFAULT true NOT NULL,
    shipping_first_name text,
    shipping_last_name text,
    shipping_company_name text,
    shipping_contact_person text,
    shipping_address_line1 text,
    shipping_address_line2 text,
    shipping_zip text,
    shipping_city text,
    shipping_country_code character varying(2),
    currency character varying(3) DEFAULT 'CHF'::bpchar NOT NULL,
    setup_cost_chf numeric(12,2) DEFAULT 0.00 NOT NULL,
    shipping_cost_chf numeric(12,2) DEFAULT 0.00 NOT NULL,
    discount_chf numeric(12,2) DEFAULT 0.00 NOT NULL,
    subtotal_chf numeric(12,2) DEFAULT 0.00 NOT NULL,
    total_chf numeric(12,2) DEFAULT 0.00 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    paid_at timestamp with time zone,
    preferred_language character varying(2) DEFAULT 'it'::character varying,
    cad_hourly_rate_chf numeric(10,2),
    cad_hours numeric(10,2),
    cad_total_chf numeric(12,2) DEFAULT 0.00 NOT NULL,
    is_cad_order boolean DEFAULT false NOT NULL,
    source_request_id uuid,
    source_type text DEFAULT 'CALCULATOR'::text NOT NULL,
    CONSTRAINT orders_billing_customer_type_check CHECK ((billing_customer_type = ANY (ARRAY['PRIVATE'::text, 'COMPANY'::text]))),
    CONSTRAINT orders_status_check CHECK ((status = ANY (ARRAY['PENDING_PAYMENT'::text, 'PAID'::text, 'IN_PRODUCTION'::text, 'SHIPPED'::text, 'COMPLETED'::text, 'CANCELLED'::text])))
);


--
-- Name: payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payments (
    payment_id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    method text NOT NULL,
    status text NOT NULL,
    currency character varying(3) DEFAULT 'CHF'::bpchar NOT NULL,
    amount_chf numeric(12,2) NOT NULL,
    payment_reference text,
    provider_transaction_id text,
    qr_payload text,
    initiated_at timestamp with time zone DEFAULT now() NOT NULL,
    received_at timestamp with time zone,
    reported_at timestamp with time zone,
    CONSTRAINT payments_amount_chf_check CHECK ((amount_chf >= (0)::numeric)),
    CONSTRAINT payments_method_check CHECK ((method = ANY (ARRAY['QR_BILL'::text, 'BANK_TRANSFER'::text, 'TWINT'::text, 'CARD'::text, 'OTHER'::text]))),
    CONSTRAINT payments_status_check CHECK ((status = ANY (ARRAY['PENDING'::text, 'REPORTED'::text, 'RECEIVED'::text, 'FAILED'::text, 'CANCELLED'::text, 'REFUNDED'::text])))
);


--
-- Name: pricing_policy; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pricing_policy (
    pricing_policy_id bigint NOT NULL,
    policy_name text NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    valid_to timestamp with time zone,
    electricity_cost_chf_per_kwh numeric(10,6) NOT NULL,
    markup_percent numeric(6,3) DEFAULT 20.000 NOT NULL,
    fixed_job_fee_chf numeric(10,2) DEFAULT 0.00 NOT NULL,
    nozzle_change_base_fee_chf numeric(10,2) DEFAULT 0.00 NOT NULL,
    cad_cost_chf_per_hour numeric(10,2) DEFAULT 0.00 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    split_model_setup_fee_chf numeric(10,2) DEFAULT 10.00
);


--
-- Name: pricing_policy_machine_hour_tier; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pricing_policy_machine_hour_tier (
    pricing_policy_machine_hour_tier_id bigint NOT NULL,
    pricing_policy_id bigint NOT NULL,
    tier_start_hours numeric(10,2) NOT NULL,
    tier_end_hours numeric(10,2),
    machine_cost_chf_per_hour numeric(10,2) NOT NULL,
    CONSTRAINT chk_tier_end_gt_start CHECK (((tier_end_hours IS NULL) OR (tier_end_hours > tier_start_hours))),
    CONSTRAINT chk_tier_start_non_negative CHECK ((tier_start_hours >= (0)::numeric))
);


--
-- Name: pricing_policy_machine_hour_t_pricing_policy_machine_hour_t_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pricing_policy_machine_hour_t_pricing_policy_machine_hour_t_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pricing_policy_machine_hour_t_pricing_policy_machine_hour_t_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pricing_policy_machine_hour_t_pricing_policy_machine_hour_t_seq OWNED BY public.pricing_policy_machine_hour_tier.pricing_policy_machine_hour_tier_id;


--
-- Name: pricing_policy_pricing_policy_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pricing_policy_pricing_policy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pricing_policy_pricing_policy_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pricing_policy_pricing_policy_id_seq OWNED BY public.pricing_policy.pricing_policy_id;


--
-- Name: printer_machine; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.printer_machine (
    printer_machine_id bigint NOT NULL,
    printer_display_name text NOT NULL,
    build_volume_x_mm integer NOT NULL,
    build_volume_y_mm integer NOT NULL,
    build_volume_z_mm integer NOT NULL,
    power_watts integer NOT NULL,
    fleet_weight numeric(6,3) DEFAULT 1.000 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    slicer_machine_profile character varying(255),
    CONSTRAINT printer_machine_build_volume_x_mm_check CHECK ((build_volume_x_mm > 0)),
    CONSTRAINT printer_machine_build_volume_y_mm_check CHECK ((build_volume_y_mm > 0)),
    CONSTRAINT printer_machine_build_volume_z_mm_check CHECK ((build_volume_z_mm > 0)),
    CONSTRAINT printer_machine_power_watts_check CHECK ((power_watts > 0))
);


--
-- Name: printer_fleet_current; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.printer_fleet_current AS
 SELECT
        CASE
            WHEN (sum(printer_machine.fleet_weight) = (0)::numeric) THEN NULL::integer
            ELSE (round((sum(((printer_machine.power_watts)::numeric * printer_machine.fleet_weight)) / sum(printer_machine.fleet_weight))))::integer
        END AS weighted_average_power_watts,
    max(printer_machine.build_volume_x_mm) AS fleet_max_build_x_mm,
    max(printer_machine.build_volume_y_mm) AS fleet_max_build_y_mm,
    max(printer_machine.build_volume_z_mm) AS fleet_max_build_z_mm
   FROM public.printer_machine
  WHERE (printer_machine.is_active = true);


--
-- Name: printer_machine_printer_machine_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.printer_machine_printer_machine_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: printer_machine_printer_machine_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.printer_machine_printer_machine_id_seq OWNED BY public.printer_machine.printer_machine_id;


--
-- Name: printer_machine_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.printer_machine_profile (
    printer_machine_profile_id bigint NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    nozzle_diameter_mm numeric(4,2) NOT NULL,
    orca_machine_profile_name text NOT NULL,
    printer_machine_id bigint NOT NULL
);


--
-- Name: printer_machine_profile_printer_machine_profile_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.printer_machine_profile ALTER COLUMN printer_machine_profile_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.printer_machine_profile_printer_machine_profile_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: qr_link; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qr_link (
    qr_link_id uuid NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    name text NOT NULL,
    notes text,
    slug character varying(128) NOT NULL,
    target_path text NOT NULL,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL
);


--
-- Name: qr_scan_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qr_scan_event (
    qr_scan_event_id uuid NOT NULL,
    city_name character varying(128),
    country_code character varying(16),
    country_name character varying(128),
    final_path text NOT NULL,
    is_suspected_bot boolean DEFAULT false NOT NULL,
    resolved_lang character varying(2) NOT NULL,
    scanned_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    visitor_key_hash character varying(64) NOT NULL,
    qr_link_id uuid NOT NULL,
    region_name character varying(128)
);


--
-- Name: quote_line_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quote_line_items (
    quote_line_item_id uuid DEFAULT gen_random_uuid() NOT NULL,
    quote_session_id uuid NOT NULL,
    status text NOT NULL,
    original_filename text NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    color_code text,
    bounding_box_x_mm numeric(10,3),
    bounding_box_y_mm numeric(10,3),
    bounding_box_z_mm numeric(10,3),
    print_time_seconds integer,
    material_grams numeric(12,2),
    unit_price_chf numeric(12,2),
    pricing_breakdown jsonb,
    error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    stored_path text,
    filament_variant_id bigint,
    infill_pattern text,
    infill_percent integer,
    layer_height_mm numeric(6,3),
    material_code text,
    nozzle_diameter_mm numeric(5,2),
    quality text,
    supports_enabled boolean,
    display_name text,
    line_item_type text DEFAULT 'PRINT_FILE'::text NOT NULL,
    shop_product_name text,
    shop_product_slug text,
    shop_variant_color_hex text,
    shop_variant_color_name text,
    shop_variant_label text,
    shop_product_id uuid,
    shop_product_variant_id uuid,
    requires_split_printing boolean DEFAULT false,
    CONSTRAINT quote_line_items_material_grams_check CHECK ((material_grams >= (0)::numeric)),
    CONSTRAINT quote_line_items_print_time_seconds_check CHECK ((print_time_seconds >= 0)),
    CONSTRAINT quote_line_items_quantity_check CHECK ((quantity >= 1)),
    CONSTRAINT quote_line_items_status_check CHECK ((status = ANY (ARRAY['CALCULATING'::text, 'READY'::text, 'FAILED'::text]))),
    CONSTRAINT quote_line_items_unit_price_chf_check CHECK ((unit_price_chf >= (0)::numeric))
);


--
-- Name: quote_session_totals; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.quote_session_totals AS
SELECT
    NULL::uuid AS quote_session_id,
    NULL::numeric AS total_chf;


--
-- Name: quote_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quote_sessions (
    quote_session_id uuid DEFAULT gen_random_uuid() NOT NULL,
    status text NOT NULL,
    pricing_version text NOT NULL,
    material_code text NOT NULL,
    nozzle_diameter_mm numeric(5,2),
    layer_height_mm numeric(6,3),
    infill_pattern text,
    infill_percent integer,
    supports_enabled boolean DEFAULT false NOT NULL,
    notes text,
    setup_cost_chf numeric(12,2) DEFAULT 0.00 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    converted_order_id uuid,
    cad_hourly_rate_chf numeric(10,2),
    cad_hours numeric(10,2),
    source_request_id uuid,
    session_type text DEFAULT 'PRINT_QUOTE'::text NOT NULL,
    CONSTRAINT quote_sessions_infill_percent_check CHECK (((infill_percent >= 0) AND (infill_percent <= 100))),
    CONSTRAINT quote_sessions_status_check CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'CAD_ACTIVE'::text, 'EXPIRED'::text, 'CONVERTED'::text])))
);


--
-- Name: shop_category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shop_category (
    shop_category_id uuid NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    description text,
    indexable boolean DEFAULT true NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    name text NOT NULL,
    og_description text,
    og_title text,
    seo_description text,
    seo_title text,
    slug text NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    parent_category_id uuid,
    description_de text,
    description_en text,
    description_fr text,
    description_it text,
    name_de text,
    name_en text,
    name_fr text,
    name_it text,
    seo_description_de text,
    seo_description_en text,
    seo_description_fr text,
    seo_description_it text,
    seo_title_de text,
    seo_title_en text,
    seo_title_fr text,
    seo_title_it text
);


--
-- Name: shop_product; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shop_product (
    shop_product_id uuid NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    description text,
    excerpt text,
    indexable boolean DEFAULT true NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    is_featured boolean DEFAULT false NOT NULL,
    name text NOT NULL,
    og_description text,
    og_title text,
    seo_description text,
    seo_title text,
    slug text NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    shop_category_id uuid NOT NULL,
    description_de text,
    description_en text,
    description_fr text,
    description_it text,
    excerpt_de text,
    excerpt_en text,
    excerpt_fr text,
    excerpt_it text,
    name_de text,
    name_en text,
    name_fr text,
    name_it text,
    seo_description_de text,
    seo_description_en text,
    seo_description_fr text,
    seo_description_it text,
    seo_title_de text,
    seo_title_en text,
    seo_title_fr text,
    seo_title_it text
);


--
-- Name: shop_product_model_asset; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shop_product_model_asset (
    shop_product_model_asset_id uuid NOT NULL,
    bounding_box_x_mm numeric(10,3),
    bounding_box_y_mm numeric(10,3),
    bounding_box_z_mm numeric(10,3),
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    file_size_bytes bigint,
    mime_type text,
    original_filename text NOT NULL,
    sha256_hex text,
    stored_filename text NOT NULL,
    stored_relative_path text NOT NULL,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    shop_product_id uuid NOT NULL
);


--
-- Name: shop_product_variant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shop_product_variant (
    shop_product_variant_id uuid NOT NULL,
    color_hex text,
    color_name text NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    internal_material_code text NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    price_chf numeric(12,2) DEFAULT 0.00 NOT NULL,
    sku text,
    sort_order integer DEFAULT 0 NOT NULL,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    variant_label text NOT NULL,
    shop_product_id uuid NOT NULL,
    color_label_de text,
    color_label_en text,
    color_label_fr text,
    color_label_it text
);


--
-- Name: filament_material_type filament_material_type_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.filament_material_type ALTER COLUMN filament_material_type_id SET DEFAULT nextval('public.filament_material_type_filament_material_type_id_seq'::regclass);


--
-- Name: filament_variant filament_variant_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.filament_variant ALTER COLUMN filament_variant_id SET DEFAULT nextval('public.filament_variant_filament_variant_id_seq'::regclass);


--
-- Name: infill_pattern infill_pattern_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infill_pattern ALTER COLUMN infill_pattern_id SET DEFAULT nextval('public.infill_pattern_infill_pattern_id_seq'::regclass);


--
-- Name: layer_height_option layer_height_option_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.layer_height_option ALTER COLUMN layer_height_option_id SET DEFAULT nextval('public.layer_height_option_layer_height_option_id_seq'::regclass);


--
-- Name: layer_height_profile layer_height_profile_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.layer_height_profile ALTER COLUMN layer_height_profile_id SET DEFAULT nextval('public.layer_height_profile_layer_height_profile_id_seq'::regclass);


--
-- Name: nozzle_option nozzle_option_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nozzle_option ALTER COLUMN nozzle_option_id SET DEFAULT nextval('public.nozzle_option_nozzle_option_id_seq'::regclass);


--
-- Name: pricing_policy pricing_policy_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pricing_policy ALTER COLUMN pricing_policy_id SET DEFAULT nextval('public.pricing_policy_pricing_policy_id_seq'::regclass);


--
-- Name: pricing_policy_machine_hour_tier pricing_policy_machine_hour_tier_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pricing_policy_machine_hour_tier ALTER COLUMN pricing_policy_machine_hour_tier_id SET DEFAULT nextval('public.pricing_policy_machine_hour_t_pricing_policy_machine_hour_t_seq'::regclass);


--
-- Name: printer_machine printer_machine_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.printer_machine ALTER COLUMN printer_machine_id SET DEFAULT nextval('public.printer_machine_printer_machine_id_seq'::regclass);


--
-- Name: custom_quote_request_attachments custom_quote_request_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_quote_request_attachments
    ADD CONSTRAINT custom_quote_request_attachments_pkey PRIMARY KEY (attachment_id);


--
-- Name: custom_quote_requests custom_quote_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_quote_requests
    ADD CONSTRAINT custom_quote_requests_pkey PRIMARY KEY (request_id);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (customer_id);


--
-- Name: filament_material_type filament_material_type_material_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.filament_material_type
    ADD CONSTRAINT filament_material_type_material_code_key UNIQUE (material_code);


--
-- Name: filament_material_type filament_material_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.filament_material_type
    ADD CONSTRAINT filament_material_type_pkey PRIMARY KEY (filament_material_type_id);


--
-- Name: filament_variant filament_variant_filament_material_type_id_variant_display__key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.filament_variant
    ADD CONSTRAINT filament_variant_filament_material_type_id_variant_display__key UNIQUE (filament_material_type_id, variant_display_name);


--
-- Name: filament_variant_orca_override filament_variant_orca_override_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.filament_variant_orca_override
    ADD CONSTRAINT filament_variant_orca_override_pkey PRIMARY KEY (filament_variant_orca_override_id);


--
-- Name: filament_variant filament_variant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.filament_variant
    ADD CONSTRAINT filament_variant_pkey PRIMARY KEY (filament_variant_id);


--
-- Name: gridfinity_generation_job gridfinity_generation_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gridfinity_generation_job
    ADD CONSTRAINT gridfinity_generation_job_pkey PRIMARY KEY (gridfinity_generation_job_id);


--
-- Name: gridfinity_generation_review gridfinity_generation_review_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gridfinity_generation_review
    ADD CONSTRAINT gridfinity_generation_review_pkey PRIMARY KEY (job_id);


--
-- Name: home_project home_project_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.home_project
    ADD CONSTRAINT home_project_pkey PRIMARY KEY (home_project_id);


--
-- Name: infill_pattern infill_pattern_pattern_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infill_pattern
    ADD CONSTRAINT infill_pattern_pattern_code_key UNIQUE (pattern_code);


--
-- Name: infill_pattern infill_pattern_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infill_pattern
    ADD CONSTRAINT infill_pattern_pkey PRIMARY KEY (infill_pattern_id);


--
-- Name: layer_height_option layer_height_option_layer_height_mm_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.layer_height_option
    ADD CONSTRAINT layer_height_option_layer_height_mm_key UNIQUE (layer_height_mm);


--
-- Name: layer_height_option layer_height_option_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.layer_height_option
    ADD CONSTRAINT layer_height_option_pkey PRIMARY KEY (layer_height_option_id);


--
-- Name: layer_height_profile layer_height_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.layer_height_profile
    ADD CONSTRAINT layer_height_profile_pkey PRIMARY KEY (layer_height_profile_id);


--
-- Name: layer_height_profile layer_height_profile_profile_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.layer_height_profile
    ADD CONSTRAINT layer_height_profile_profile_name_key UNIQUE (profile_name);


--
-- Name: material_orca_profile_map material_orca_profile_map_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_orca_profile_map
    ADD CONSTRAINT material_orca_profile_map_pkey PRIMARY KEY (material_orca_profile_map_id);


--
-- Name: media_asset media_asset_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_asset
    ADD CONSTRAINT media_asset_pkey PRIMARY KEY (media_asset_id);


--
-- Name: media_usage media_usage_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_usage
    ADD CONSTRAINT media_usage_pkey PRIMARY KEY (media_usage_id);


--
-- Name: media_variant media_variant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_variant
    ADD CONSTRAINT media_variant_pkey PRIMARY KEY (media_variant_id);


--
-- Name: nozzle_layer_height_option nozzle_layer_height_option_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nozzle_layer_height_option
    ADD CONSTRAINT nozzle_layer_height_option_pkey PRIMARY KEY (nozzle_layer_height_option_id);


--
-- Name: nozzle_option nozzle_option_nozzle_diameter_mm_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nozzle_option
    ADD CONSTRAINT nozzle_option_nozzle_diameter_mm_key UNIQUE (nozzle_diameter_mm);


--
-- Name: nozzle_option nozzle_option_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nozzle_option
    ADD CONSTRAINT nozzle_option_pkey PRIMARY KEY (nozzle_option_id);


--
-- Name: order_deliverable_files order_deliverable_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_deliverable_files
    ADD CONSTRAINT order_deliverable_files_pkey PRIMARY KEY (order_deliverable_file_id);


--
-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (order_item_id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (order_id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (payment_id);


--
-- Name: pricing_policy_machine_hour_tier pricing_policy_machine_hour_tier_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pricing_policy_machine_hour_tier
    ADD CONSTRAINT pricing_policy_machine_hour_tier_pkey PRIMARY KEY (pricing_policy_machine_hour_tier_id);


--
-- Name: pricing_policy pricing_policy_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pricing_policy
    ADD CONSTRAINT pricing_policy_pkey PRIMARY KEY (pricing_policy_id);


--
-- Name: printer_machine printer_machine_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.printer_machine
    ADD CONSTRAINT printer_machine_pkey PRIMARY KEY (printer_machine_id);


--
-- Name: printer_machine printer_machine_printer_display_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.printer_machine
    ADD CONSTRAINT printer_machine_printer_display_name_key UNIQUE (printer_display_name);


--
-- Name: printer_machine_profile printer_machine_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.printer_machine_profile
    ADD CONSTRAINT printer_machine_profile_pkey PRIMARY KEY (printer_machine_profile_id);


--
-- Name: qr_link qr_link_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qr_link
    ADD CONSTRAINT qr_link_pkey PRIMARY KEY (qr_link_id);


--
-- Name: qr_scan_event qr_scan_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qr_scan_event
    ADD CONSTRAINT qr_scan_event_pkey PRIMARY KEY (qr_scan_event_id);


--
-- Name: quote_line_items quote_line_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quote_line_items
    ADD CONSTRAINT quote_line_items_pkey PRIMARY KEY (quote_line_item_id);


--
-- Name: quote_sessions quote_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quote_sessions
    ADD CONSTRAINT quote_sessions_pkey PRIMARY KEY (quote_session_id);


--
-- Name: shop_category shop_category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_category
    ADD CONSTRAINT shop_category_pkey PRIMARY KEY (shop_category_id);


--
-- Name: shop_product_model_asset shop_product_model_asset_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_product_model_asset
    ADD CONSTRAINT shop_product_model_asset_pkey PRIMARY KEY (shop_product_model_asset_id);


--
-- Name: shop_product shop_product_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_product
    ADD CONSTRAINT shop_product_pkey PRIMARY KEY (shop_product_id);


--
-- Name: shop_product_variant shop_product_variant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_product_variant
    ADD CONSTRAINT shop_product_variant_pkey PRIMARY KEY (shop_product_variant_id);


--
-- Name: qr_link uk1d6jkdcfaqfwe82sf0q8wk7al; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qr_link
    ADD CONSTRAINT uk1d6jkdcfaqfwe82sf0q8wk7al UNIQUE (slug);


--
-- Name: shop_product uk84goy8ritx1rclixwn6ssbyx4; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_product
    ADD CONSTRAINT uk84goy8ritx1rclixwn6ssbyx4 UNIQUE (slug);


--
-- Name: shop_category uk90p7apjlsdgfvemsd81rghrto; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_category
    ADD CONSTRAINT uk90p7apjlsdgfvemsd81rghrto UNIQUE (slug);


--
-- Name: home_project ukdqjueoesefyfbg5tcy99qhegg; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.home_project
    ADD CONSTRAINT ukdqjueoesefyfbg5tcy99qhegg UNIQUE (slug);


--
-- Name: shop_product_variant ukeandwd24k6757h3d9s14hrjdl; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_product_variant
    ADD CONSTRAINT ukeandwd24k6757h3d9s14hrjdl UNIQUE (sku);


--
-- Name: shop_product_model_asset ukgucg5wnah0wclaiphlo7om4bb; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_product_model_asset
    ADD CONSTRAINT ukgucg5wnah0wclaiphlo7om4bb UNIQUE (shop_product_id);


--
-- Name: media_asset ukhm2lsmsq9wu13tnung3669uij; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_asset
    ADD CONSTRAINT ukhm2lsmsq9wu13tnung3669uij UNIQUE (storage_key);


--
-- Name: media_variant ukm5cosvfxr9cehse1vs9909yc5; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_variant
    ADD CONSTRAINT ukm5cosvfxr9cehse1vs9909yc5 UNIQUE (storage_key);


--
-- Name: media_variant uq_media_variant_asset_name_format; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_variant
    ADD CONSTRAINT uq_media_variant_asset_name_format UNIQUE (media_asset_id, variant_name, format);


--
-- Name: filament_variant_orca_override ux_filament_variant_orca_override_variant_machine; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.filament_variant_orca_override
    ADD CONSTRAINT ux_filament_variant_orca_override_variant_machine UNIQUE (filament_variant_id, printer_machine_profile_id);


--
-- Name: material_orca_profile_map ux_material_orca_profile_map_machine_material; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_orca_profile_map
    ADD CONSTRAINT ux_material_orca_profile_map_machine_material UNIQUE (printer_machine_profile_id, filament_material_type_id);


--
-- Name: nozzle_layer_height_option ux_nozzle_layer_height_option_nozzle_layer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nozzle_layer_height_option
    ADD CONSTRAINT ux_nozzle_layer_height_option_nozzle_layer UNIQUE (nozzle_diameter_mm, layer_height_mm);


--
-- Name: printer_machine_profile ux_printer_machine_profile_machine_nozzle; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.printer_machine_profile
    ADD CONSTRAINT ux_printer_machine_profile_machine_nozzle UNIQUE (printer_machine_id, nozzle_diameter_mm);


--
-- Name: idx_pricing_policy_validity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pricing_policy_validity ON public.pricing_policy USING btree (valid_from, valid_to);


--
-- Name: idx_pricing_tier_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pricing_tier_lookup ON public.pricing_policy_machine_hour_tier USING btree (pricing_policy_id, tier_start_hours);


--
-- Name: ix_custom_quote_attachments_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_custom_quote_attachments_request ON public.custom_quote_request_attachments USING btree (request_id);


--
-- Name: ix_custom_quote_requests_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_custom_quote_requests_email ON public.custom_quote_requests USING btree (lower(email));


--
-- Name: ix_custom_quote_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_custom_quote_requests_status ON public.custom_quote_requests USING btree (status);


--
-- Name: ix_gridfinity_generation_job_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_gridfinity_generation_job_created_at ON public.gridfinity_generation_job USING btree (created_at);


--
-- Name: ix_gridfinity_generation_job_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_gridfinity_generation_job_status ON public.gridfinity_generation_job USING btree (status);


--
-- Name: ix_home_project_active_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_home_project_active_sort ON public.home_project USING btree (is_active, sort_order);


--
-- Name: ix_media_asset_status_visibility_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_media_asset_status_visibility_created_at ON public.media_asset USING btree (status, visibility, created_at);


--
-- Name: ix_media_usage_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_media_usage_scope ON public.media_usage USING btree (usage_type, usage_key, is_active, sort_order);


--
-- Name: ix_media_variant_asset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_media_variant_asset ON public.media_variant USING btree (media_asset_id);


--
-- Name: ix_order_deliverable_files_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_order_deliverable_files_order ON public.order_deliverable_files USING btree (order_id);


--
-- Name: ix_order_items_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_order_items_order ON public.order_items USING btree (order_id);


--
-- Name: ix_orders_customer_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_orders_customer_email ON public.orders USING btree (lower(customer_email));


--
-- Name: ix_orders_source_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_orders_source_request ON public.orders USING btree (source_request_id);


--
-- Name: ix_orders_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_orders_status ON public.orders USING btree (status);


--
-- Name: ix_payments_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_payments_order ON public.payments USING btree (order_id);


--
-- Name: ix_payments_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_payments_reference ON public.payments USING btree (payment_reference);


--
-- Name: ix_qr_link_active_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_qr_link_active_updated ON public.qr_link USING btree (is_active, updated_at);


--
-- Name: ix_qr_link_slug; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_qr_link_slug ON public.qr_link USING btree (slug);


--
-- Name: ix_qr_scan_event_link_bot_scanned; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_qr_scan_event_link_bot_scanned ON public.qr_scan_event USING btree (qr_link_id, is_suspected_bot, scanned_at);


--
-- Name: ix_qr_scan_event_link_scanned; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_qr_scan_event_link_scanned ON public.qr_scan_event USING btree (qr_link_id, scanned_at);


--
-- Name: ix_quote_line_items_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_quote_line_items_session ON public.quote_line_items USING btree (quote_session_id);


--
-- Name: ix_quote_sessions_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_quote_sessions_expires_at ON public.quote_sessions USING btree (expires_at);


--
-- Name: ix_quote_sessions_source_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_quote_sessions_source_request ON public.quote_sessions USING btree (source_request_id);


--
-- Name: ix_quote_sessions_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_quote_sessions_status ON public.quote_sessions USING btree (status);


--
-- Name: ix_shop_category_active_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_shop_category_active_sort ON public.shop_category USING btree (is_active, sort_order);


--
-- Name: ix_shop_category_parent_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_shop_category_parent_sort ON public.shop_category USING btree (parent_category_id, sort_order);


--
-- Name: ix_shop_product_category_active_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_shop_product_category_active_sort ON public.shop_product USING btree (shop_category_id, is_active, sort_order);


--
-- Name: ix_shop_product_featured_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_shop_product_featured_sort ON public.shop_product USING btree (is_featured, is_active, sort_order);


--
-- Name: ix_shop_product_model_asset_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_shop_product_model_asset_product ON public.shop_product_model_asset USING btree (shop_product_id);


--
-- Name: ix_shop_product_variant_product_active_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_shop_product_variant_product_active_sort ON public.shop_product_variant USING btree (shop_product_id, is_active, sort_order);


--
-- Name: ix_shop_product_variant_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_shop_product_variant_sku ON public.shop_product_variant USING btree (sku);


--
-- Name: ux_customers_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_customers_email ON public.customers USING btree (lower(email));


--
-- Name: quote_session_totals _RETURN; Type: RULE; Schema: public; Owner: -
--

CREATE OR REPLACE VIEW public.quote_session_totals AS
 SELECT qs.quote_session_id,
    (qs.setup_cost_chf + COALESCE(sum((qli.unit_price_chf * (qli.quantity)::numeric)), 0.00)) AS total_chf
   FROM (public.quote_sessions qs
     LEFT JOIN public.quote_line_items qli ON (((qli.quote_session_id = qs.quote_session_id) AND (qli.status = 'READY'::text))))
  GROUP BY qs.quote_session_id;


--
-- Name: custom_quote_request_attachments custom_quote_request_attachments_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_quote_request_attachments
    ADD CONSTRAINT custom_quote_request_attachments_request_id_fkey FOREIGN KEY (request_id) REFERENCES public.custom_quote_requests(request_id) ON DELETE CASCADE;


--
-- Name: filament_variant filament_variant_filament_material_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.filament_variant
    ADD CONSTRAINT filament_variant_filament_material_type_id_fkey FOREIGN KEY (filament_material_type_id) REFERENCES public.filament_material_type(filament_material_type_id);


--
-- Name: shop_product_variant fk1682phbs972xg3vkn9qqnn3f3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_product_variant
    ADD CONSTRAINT fk1682phbs972xg3vkn9qqnn3f3 FOREIGN KEY (shop_product_id) REFERENCES public.shop_product(shop_product_id);


--
-- Name: quote_line_items fk2ek4gpvi811dbmqmhxt4cx8o5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quote_line_items
    ADD CONSTRAINT fk2ek4gpvi811dbmqmhxt4cx8o5 FOREIGN KEY (filament_variant_id) REFERENCES public.filament_variant(filament_variant_id);


--
-- Name: order_items fk3wmcw1qcoh8w01ln3q1ymjblv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fk3wmcw1qcoh8w01ln3q1ymjblv FOREIGN KEY (shop_product_variant_id) REFERENCES public.shop_product_variant(shop_product_variant_id);


--
-- Name: shop_product_model_asset fk5qwksa0hmi1ly1uxj90dnn4jn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_product_model_asset
    ADD CONSTRAINT fk5qwksa0hmi1ly1uxj90dnn4jn FOREIGN KEY (shop_product_id) REFERENCES public.shop_product(shop_product_id);


--
-- Name: orders fk_orders_source_request; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk_orders_source_request FOREIGN KEY (source_request_id) REFERENCES public.custom_quote_requests(request_id);


--
-- Name: quote_sessions fk_quote_sessions_source_request; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quote_sessions
    ADD CONSTRAINT fk_quote_sessions_source_request FOREIGN KEY (source_request_id) REFERENCES public.custom_quote_requests(request_id);


--
-- Name: media_variant fkabe0mri3gecf9q3qpro0m9ug; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_variant
    ADD CONSTRAINT fkabe0mri3gecf9q3qpro0m9ug FOREIGN KEY (media_asset_id) REFERENCES public.media_asset(media_asset_id) ON DELETE CASCADE;


--
-- Name: media_usage fkaj0pgi00b3v9usq9jxo1fgwnh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_usage
    ADD CONSTRAINT fkaj0pgi00b3v9usq9jxo1fgwnh FOREIGN KEY (media_asset_id) REFERENCES public.media_asset(media_asset_id) ON DELETE CASCADE;


--
-- Name: order_items fkavrup5uns0vopt0fqemkxtgru; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkavrup5uns0vopt0fqemkxtgru FOREIGN KEY (filament_variant_id) REFERENCES public.filament_variant(filament_variant_id);


--
-- Name: quote_line_items fkbichsi4r7tsiqkq2m601dtof1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quote_line_items
    ADD CONSTRAINT fkbichsi4r7tsiqkq2m601dtof1 FOREIGN KEY (shop_product_variant_id) REFERENCES public.shop_product_variant(shop_product_variant_id);


--
-- Name: filament_variant_orca_override fkbwqutlu0x6q9caq2y3hhbly2r; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.filament_variant_orca_override
    ADD CONSTRAINT fkbwqutlu0x6q9caq2y3hhbly2r FOREIGN KEY (filament_variant_id) REFERENCES public.filament_variant(filament_variant_id);


--
-- Name: order_items fkd7e69hkcwo21n58c3hposlcbs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkd7e69hkcwo21n58c3hposlcbs FOREIGN KEY (shop_product_id) REFERENCES public.shop_product(shop_product_id);


--
-- Name: qr_scan_event fkdu5igpau3f1c1l46vmys679cp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qr_scan_event
    ADD CONSTRAINT fkdu5igpau3f1c1l46vmys679cp FOREIGN KEY (qr_link_id) REFERENCES public.qr_link(qr_link_id) ON DELETE CASCADE;


--
-- Name: material_orca_profile_map fkf1k4r0dr64qavg5pgv42gwj49; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_orca_profile_map
    ADD CONSTRAINT fkf1k4r0dr64qavg5pgv42gwj49 FOREIGN KEY (filament_material_type_id) REFERENCES public.filament_material_type(filament_material_type_id);


--
-- Name: quote_line_items fkfou8jgo9wvn4b88eks4a80i2j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quote_line_items
    ADD CONSTRAINT fkfou8jgo9wvn4b88eks4a80i2j FOREIGN KEY (shop_product_id) REFERENCES public.shop_product(shop_product_id);


--
-- Name: filament_variant_orca_override fkg2wefbogmlqggwt1v9cehye8x; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.filament_variant_orca_override
    ADD CONSTRAINT fkg2wefbogmlqggwt1v9cehye8x FOREIGN KEY (printer_machine_profile_id) REFERENCES public.printer_machine_profile(printer_machine_profile_id);


--
-- Name: shop_category fkkcu8u7aa6cah6w9gramv8vmlp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_category
    ADD CONSTRAINT fkkcu8u7aa6cah6w9gramv8vmlp FOREIGN KEY (parent_category_id) REFERENCES public.shop_category(shop_category_id);


--
-- Name: printer_machine_profile fklup2b6ic4cb3r1w9af4pd5hp6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.printer_machine_profile
    ADD CONSTRAINT fklup2b6ic4cb3r1w9af4pd5hp6 FOREIGN KEY (printer_machine_id) REFERENCES public.printer_machine(printer_machine_id);


--
-- Name: gridfinity_generation_review fknk37716ge765ssre2venk4y3q; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gridfinity_generation_review
    ADD CONSTRAINT fknk37716ge765ssre2venk4y3q FOREIGN KEY (job_id) REFERENCES public.gridfinity_generation_job(gridfinity_generation_job_id);


--
-- Name: shop_product fkosunf67niub9pd1ercfp0wnoq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_product
    ADD CONSTRAINT fkosunf67niub9pd1ercfp0wnoq FOREIGN KEY (shop_category_id) REFERENCES public.shop_category(shop_category_id);


--
-- Name: order_deliverable_files fkpq65ty9fhve81cr1tub94oqiv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_deliverable_files
    ADD CONSTRAINT fkpq65ty9fhve81cr1tub94oqiv FOREIGN KEY (order_id) REFERENCES public.orders(order_id) ON DELETE CASCADE;


--
-- Name: material_orca_profile_map fkskoi9v4m7w2f6fnkwhbr3hx9v; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_orca_profile_map
    ADD CONSTRAINT fkskoi9v4m7w2f6fnkwhbr3hx9v FOREIGN KEY (printer_machine_profile_id) REFERENCES public.printer_machine_profile(printer_machine_profile_id);


--
-- Name: order_items order_items_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(order_id) ON DELETE CASCADE;


--
-- Name: orders orders_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id);


--
-- Name: orders orders_source_quote_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_source_quote_session_id_fkey FOREIGN KEY (source_quote_session_id) REFERENCES public.quote_sessions(quote_session_id);


--
-- Name: payments payments_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(order_id) ON DELETE CASCADE;


--
-- Name: pricing_policy_machine_hour_tier pricing_policy_machine_hour_tier_pricing_policy_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pricing_policy_machine_hour_tier
    ADD CONSTRAINT pricing_policy_machine_hour_tier_pricing_policy_id_fkey FOREIGN KEY (pricing_policy_id) REFERENCES public.pricing_policy(pricing_policy_id);


--
-- Name: quote_line_items quote_line_items_quote_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quote_line_items
    ADD CONSTRAINT quote_line_items_quote_session_id_fkey FOREIGN KEY (quote_session_id) REFERENCES public.quote_sessions(quote_session_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--
