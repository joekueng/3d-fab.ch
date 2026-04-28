#!/bin/sh
set -eu

raw="${raw:-${SSH_ORIGINAL_COMMAND:-}}"

# Prefer args if present, otherwise parse raw/SSH_ORIGINAL_COMMAND
if [ "$#" -eq 0 ]; then
  set -- $raw
fi

if [ "$#" -eq 0 ]; then
  echo "Usage: deploy-print-calculator.sh [deploy|setenv|setcompose] <dev|int|prod> | setcommon"
  exit 2
fi

case "$1" in
  deploy|setenv|setcompose)
    if [ "$#" -lt 2 ]; then
      echo "Usage: deploy-print-calculator.sh [deploy|setenv|setcompose] <dev|int|prod> | setcommon"
      exit 2
    fi
    action="$1"
    environment_name="$2"
    ;;
  setcommon)
    action="setcommon"
    environment_name=""
    ;;
  prod|int|dev)
    action="deploy"
    environment_name="$1"
    ;;
  *)
    echo "Usage: deploy-print-calculator.sh [deploy|setenv|setcompose] <dev|int|prod> | setcommon"
    exit 2
    ;;
esac

if [ "${action}" != "setcommon" ]; then
  case "${environment_name}" in
    prod|int|dev) ;;
    *) echo "Invalid env. Use: prod | int | dev"; exit 2 ;;
  esac
fi

# Detect base_dir (cache preferred)
if [ -d "/mnt/cache/appdata/print-calculator" ]; then
  base_dir="/mnt/cache/appdata/print-calculator"
elif [ -d "/mnt/user/appdata/print-calculator" ]; then
  base_dir="/mnt/user/appdata/print-calculator"
else
  echo "Missing base_dir for print-calculator"
  exit 3
fi

compose_file="${base_dir}/docker-compose-deploy.yml"
common_env="${base_dir}/common.env"
env_file="${base_dir}/${environment_name}/.env"
project="print-calculator-${environment_name}"

case "${action}" in
  setcommon)
    umask 077
    mkdir -p "${base_dir}"
    cat > "${common_env}"
    exit 0
    ;;
  setenv)
    umask 077
    mkdir -p "$(dirname "${env_file}")"
    cat > "${env_file}"
    exit 0
    ;;
  setcompose)
    umask 077
    cat > "${compose_file}"
    exit 0
    ;;
  deploy)
    if [ ! -f "${compose_file}" ]; then echo "Missing ${compose_file}"; exit 3; fi
    if [ ! -f "${common_env}" ]; then 
       echo "WARN: Missing ${common_env}, creating empty one"
       touch "${common_env}"
    fi
    if [ ! -f "${env_file}" ]; then echo "Missing ${env_file}"; exit 3; fi

    # Merge env files
    cat "${common_env}" "${env_file}" | tr -d '\r' > "${base_dir}/.env"
    printf '\nENV=%s\n' "${environment_name}" >> "${base_dir}/.env"

    echo "Pulling images..."
    docker compose --env-file "${base_dir}/.env" -p "${project}" -f "${compose_file}" pull

    # Load env vars needed for image/volume naming
    set -a
    . "${base_dir}/.env"
    set +a

    ENV="${ENV:-${environment_name}}"
    if [ "${ENV}" != "${environment_name}" ]; then
      echo "WARN: ENV from .env was '${ENV}', forcing '${environment_name}'"
      ENV="${environment_name}"
    fi

    if [ -z "${REGISTRY_URL:-}" ] || [ -z "${REPO_OWNER:-}" ] || [ -z "${TAG:-}" ]; then
      echo "Missing REGISTRY_URL/REPO_OWNER/TAG in merged env"
      exit 3
    fi

    BACKEND_IMAGE="${REGISTRY_URL}/${REPO_OWNER}/print-calculator-backend:${TAG}"
    PROFILES_VOL="${project}_backend_profiles_${ENV}"
    
    echo "Validating FFmpeg support in ${BACKEND_IMAGE}..."
    docker run --rm --entrypoint /bin/sh "${BACKEND_IMAGE}" -c '\
      ffmpeg_bin="${MEDIA_FFMPEG_PATH:-/usr/local/bin/ffmpeg-media}"; \
      command -v "$ffmpeg_bin" >/dev/null 2>&1 || { echo "Missing FFmpeg: $ffmpeg_bin" >&2; exit 1; }; \
      "$ffmpeg_bin" -hide_banner -encoders | grep -Eq "[[:space:]]mjpeg[[:space:]]" || { echo "Missing JPEG encoder" >&2; exit 1; }; \
      "$ffmpeg_bin" -hide_banner -encoders | grep -Eq "[[:space:]](libwebp|webp)[[:space:]]" || { echo "Missing WebP encoder" >&2; exit 1; }; \
      "$ffmpeg_bin" -hide_banner -encoders | grep -Eq "[[:space:]](libaom-av1|librav1e|libsvtav1)[[:space:]]" || { echo "Missing AVIF-capable encoder" >&2; exit 1; }; \
      "$ffmpeg_bin" -hide_banner -muxers | grep -Eq "[[:space:]]avif([[:space:]]|,|$)" || { echo "Missing AVIF muxer" >&2; exit 1; }'

    echo "Syncing profiles to volume ${PROFILES_VOL} (using safe copy)..."
    docker volume create "${PROFILES_VOL}" >/dev/null
    
    # Use create + cp + rm instead of run to avoid starting the container
    TMP_CONTAINER="tmp_profiles_sync_${ENV}"
    docker rm -f "${TMP_CONTAINER}" >/dev/null 2>&1 || true
    docker create --name "${TMP_CONTAINER}" "${BACKEND_IMAGE}"
    
    # Create a temporary local directory to help with the sync if needed, 
    # but docker cp can work directly with volumes in some versions.
    # To be universal, we copy to a temp path then into volume.
    docker run --rm -v "${PROFILES_VOL}:/dest" "${BACKEND_IMAGE}" sh -c "rm -rf /dest/*"
    
    # Alternative safe sync: run a minimal busybox/sh container to do the copy
    docker run --rm \
      -v "${PROFILES_VOL}:/profiles-volume" \
      "${BACKEND_IMAGE}" \
      /bin/sh -c "cp -a /app/profiles/. /profiles-volume/" || \
    docker run --rm \
      -v "${PROFILES_VOL}:/profiles-volume" \
      --entrypoint "/bin/sh" \
      "${BACKEND_IMAGE}" \
      -c "cp -a /app/profiles/. /profiles-volume/"

    docker rm -f "${TMP_CONTAINER}" >/dev/null 2>&1 || true

    echo "Starting services..."
    docker compose --env-file "${base_dir}/.env" -p "${project}" -f "${compose_file}" up -d --remove-orphans --force-recreate
    
    echo "Cleaning up obsolete images..."
    docker image prune -f
    ;;
  *)
    echo "Invalid action. Use: deploy | setenv | setcompose | setcommon"
    exit 2
    ;;
esac
