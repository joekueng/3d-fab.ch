#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "${DOCKER_CONTEXT:-}" ]]; then
  docker_endpoint="$(docker context inspect "$DOCKER_CONTEXT" --format '{{.Endpoints.docker.Host}}')"
else
  docker_endpoint="${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}')}"
fi
if [[ "$docker_endpoint" != unix://* ]]; then
  echo 'Refusing to configure X2D through a remote Docker endpoint.' >&2
  exit 2
fi
compose() { docker compose --project-name print-calculator -f "$repo_root/docker-compose.yml" "$@"; }
db_container="$(compose ps -q db)"
if [[ -z "$db_container" ]] || [[ "$(docker inspect --format '{{.Name}}' "$db_container")" != /print-calculator-db ]]; then
  echo 'Expected the local print-calculator-db container.' >&2
  exit 2
fi
if [[ "$(compose exec -T db psql -X -U printcalc -d printcalc -tAc 'select current_database()')" != printcalc ]]; then
  echo 'Refusing to configure an unexpected database.' >&2
  exit 2
fi

# Local catalogue fixtures only. Preserve all existing material/variant values.
compose exec -T db psql -X -v ON_ERROR_STOP=1 -U printcalc -d printcalc <<'SQL'
begin;
insert into filament_material_type (material_code, is_flexible, is_technical, technical_type_label)
select f.code, false, true, f.label
from (values ('PET-CF', 'High temperature resistance'), ('ASA', 'UV resistance')) f(code, label)
where not exists (select 1 from filament_material_type m where upper(m.material_code) = f.code);

-- Copy the existing local PA12-CF test cost for a PET-CF fixture; this is not a selling price.
insert into filament_variant (
    filament_material_type_id, variant_display_name, color_name, color_hex, finish_type,
    color_label_it, color_label_en, color_label_de, color_label_fr,
    cost_chf_per_kg, stock_spools, spool_net_kg, is_active
)
select m.filament_material_type_id, 'PET-CF Nero (test locale)', 'Nero', '#000000', 'MATTE',
       'Nero', 'Black', 'Schwarz', 'Noir',
       (select v.cost_chf_per_kg from filament_variant v join filament_material_type mt using (filament_material_type_id)
        where mt.material_code = 'PA12-CF' and v.is_active order by v.filament_variant_id limit 1),
       1.000, 1.000, true
from filament_material_type m
where m.material_code = 'PET-CF'
  and not exists (select 1 from filament_variant v where v.filament_material_type_id = m.filament_material_type_id and v.is_active);
commit;
SQL

compose exec -T db psql -X -v ON_ERROR_STOP=1 -U printcalc -d printcalc \
  < "$repo_root/scripts/prepare-x2d-asa-supports.sql"

# Local backend must already use the checked-in 2.4.2 executable/profiles.
compose exec -T db psql -X -v ON_ERROR_STOP=1 -U printcalc -d printcalc <<'SQL'
update printer_machine set is_active = true
where replace(lower(printer_display_name), ' ', '') = 'bambulabx2d';
select printer_display_name, power_watts, fleet_weight, is_active
from printer_machine order by printer_machine_id;
SQL
