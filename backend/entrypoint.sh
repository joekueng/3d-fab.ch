#!/bin/sh
set -e

# In container default to the ffmpeg selected during image build.
if [ -z "${MEDIA_FFMPEG_PATH:-}" ]; then
  MEDIA_FFMPEG_PATH="/usr/local/bin/ffmpeg-media"
fi
export MEDIA_FFMPEG_PATH

validate_ffmpeg_support() {
  ffmpeg_bin="$1"
  if ! command -v "$ffmpeg_bin" >/dev/null 2>&1; then
    echo "ERROR: FFmpeg executable not found: ${ffmpeg_bin}" >&2
    exit 11
  fi

  encoders="$(mktemp)"
  muxers="$(mktemp)"
  trap 'rm -f "$encoders" "$muxers"' EXIT

  "$ffmpeg_bin" -hide_banner -encoders > "$encoders" 2>&1 || {
    echo "ERROR: Unable to inspect FFmpeg encoders from ${ffmpeg_bin}" >&2
    cat "$encoders" >&2
    exit 12
  }
  "$ffmpeg_bin" -hide_banner -muxers > "$muxers" 2>&1 || {
    echo "ERROR: Unable to inspect FFmpeg muxers from ${ffmpeg_bin}" >&2
    cat "$muxers" >&2
    exit 13
  }

  grep -Eq '[[:space:]]mjpeg[[:space:]]' "$encoders" || {
    echo "ERROR: FFmpeg '${ffmpeg_bin}' missing JPEG encoder (mjpeg)." >&2
    exit 14
  }
  grep -Eq '[[:space:]](libwebp|webp)[[:space:]]' "$encoders" || {
    echo "ERROR: FFmpeg '${ffmpeg_bin}' missing WebP encoder." >&2
    exit 15
  }
  grep -Eq '[[:space:]](libaom-av1|librav1e|libsvtav1)[[:space:]]' "$encoders" || {
    echo "ERROR: FFmpeg '${ffmpeg_bin}' missing AVIF-capable encoder." >&2
    exit 16
  }
  grep -Eq '[[:space:]]avif([[:space:]]|,|$)' "$muxers" || {
    echo "ERROR: FFmpeg '${ffmpeg_bin}' missing AVIF muxer." >&2
    exit 17
  }
}

validate_ffmpeg_support "$MEDIA_FFMPEG_PATH"

echo "----------------------------------------------------------------"
echo "Starting Backend Application"
echo "DB_URL: $DB_URL"
echo "DB_USERNAME: $DB_USERNAME"
echo "SPRING_DATASOURCE_URL: $SPRING_DATASOURCE_URL"
echo "SLICER_PATH: $SLICER_PATH"
echo "MEDIA_FFMPEG_PATH: $MEDIA_FFMPEG_PATH"
echo "----------------------------------------------------------------"

# Determine which environment variables to use for database connection
# This allows compatibility with different docker-compose configurations
FINAL_DB_URL="${DB_URL:-$SPRING_DATASOURCE_URL}"
FINAL_DB_USER="${DB_USERNAME:-$SPRING_DATASOURCE_USERNAME}"
FINAL_DB_PASS="${DB_PASSWORD:-$SPRING_DATASOURCE_PASSWORD}"

if [ -n "$FINAL_DB_URL" ]; then
  echo "Using database URL: $FINAL_DB_URL"
  exec java -jar app.jar \
    --spring.datasource.url="${FINAL_DB_URL}" \
    --spring.datasource.username="${FINAL_DB_USER}" \
    --spring.datasource.password="${FINAL_DB_PASS}"
else
  echo "No database URL specified in environment, relying on application.properties defaults."
  exec java -jar app.jar
fi
