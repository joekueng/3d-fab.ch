# Stage 1: Build Java JAR
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle gradle.lockfile ./
# Download dependencies first to cache them
RUN ./gradlew dependencies --no-daemon
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Runtime Environment
FROM eclipse-temurin:21-jre-noble
LABEL ch.printcalculator.project="print-calculator"
ARG ORCA_VERSION=2.3.2
ARG ORCA_DOWNLOAD_URL
ARG ORCA_SHA256=c64336ceec37d941766e675cbaaaf5124e184402bf18177fbf81ba5102734ad8
ARG FFMPEG_STATIC_URL=https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz

# Install system dependencies for OrcaSlicer and media processing.
# Prefer system ffmpeg; if AVIF support is incomplete, install a static ffmpeg fallback.
RUN set -eux; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ffmpeg \
    wget \
    xz-utils \
    squashfs-tools \
    ca-certificates \
    assimp-utils \
    libgl1 \
    libopengl0 \
    libglu1-mesa \
    libsm6 \
    libice6 \
    libmspack0t64 \
    libglib2.0-0t64 \
    libgtk-3-0t64 \
    libdbus-1-3 \
    libwebkit2gtk-4.1-0; \
    check_ffmpeg_support() { \
      ffmpeg_bin="$1"; \
      "$ffmpeg_bin" -hide_banner -encoders > /tmp/ffmpeg-encoders.txt 2>&1 || return 1; \
      "$ffmpeg_bin" -hide_banner -muxers > /tmp/ffmpeg-muxers.txt 2>&1 || return 1; \
      grep -Eq '[[:space:]]mjpeg[[:space:]]' /tmp/ffmpeg-encoders.txt || return 1; \
      grep -Eq '[[:space:]](libwebp|webp)[[:space:]]' /tmp/ffmpeg-encoders.txt || return 1; \
      grep -Eq '[[:space:]](libaom-av1|librav1e|libsvtav1)[[:space:]]' /tmp/ffmpeg-encoders.txt || return 1; \
      grep -Eq '[[:space:]]avif([[:space:]]|,|$)' /tmp/ffmpeg-muxers.txt || return 1; \
      return 0; \
    }; \
    if check_ffmpeg_support /usr/bin/ffmpeg; then \
      ln -sf /usr/bin/ffmpeg /usr/local/bin/ffmpeg-media; \
    else \
      echo "System ffmpeg lacks AVIF support, installing static fallback from ${FFMPEG_STATIC_URL}"; \
      wget -q "${FFMPEG_STATIC_URL}" -O /tmp/ffmpeg-static.tar.xz; \
      tar -xJf /tmp/ffmpeg-static.tar.xz -C /tmp; \
      FFMPEG_STATIC_BIN="$(find /tmp -maxdepth 2 -type f -name ffmpeg | head -n 1)"; \
      test -n "${FFMPEG_STATIC_BIN}"; \
      install -m 0755 "${FFMPEG_STATIC_BIN}" /usr/local/bin/ffmpeg-media; \
      check_ffmpeg_support /usr/local/bin/ffmpeg-media; \
    fi; \
    rm -f /tmp/ffmpeg-muxers.txt; \
    rm -f /tmp/ffmpeg-encoders.txt; \
    rm -f /tmp/ffmpeg-static.tar.xz; \
    rm -rf /tmp/ffmpeg-*-amd64-static; \
    rm -rf /var/lib/apt/lists/*

# Install OrcaSlicer
WORKDIR /opt
RUN set -eux; \
    ORCA_URL="${ORCA_DOWNLOAD_URL:-https://github.com/OrcaSlicer/OrcaSlicer/releases/download/v${ORCA_VERSION}/OrcaSlicer_Linux_AppImage_Ubuntu2404_V${ORCA_VERSION}.AppImage}"; \
    wget -q "$ORCA_URL" -O OrcaSlicer.AppImage; \
    echo "$ORCA_SHA256  OrcaSlicer.AppImage" | sha256sum -c - \
    && rm -rf /opt/orcaslicer \
    && offset=''; \
    for candidate in $(LC_ALL=C grep -abo 'hsqs' OrcaSlicer.AppImage | cut -d: -f1); do \
      if unsquashfs -s -o "$candidate" OrcaSlicer.AppImage >/dev/null 2>&1; then \
        offset="$candidate"; \
        break; \
      fi; \
    done; \
    test -n "$offset"; \
    unsquashfs -f -o "$offset" -d /opt/orcaslicer OrcaSlicer.AppImage >/dev/null; \
    test -e /opt/orcaslicer/AppRun; \
    chmod -R +x /opt/orcaslicer; \
    rm OrcaSlicer.AppImage

ENV PATH="/opt/orcaslicer/usr/bin:${PATH}"
# Set Slicer Path env variable for Java app
ENV SLICER_PATH="/opt/orcaslicer/AppRun"
ENV ASSIMP_PATH="assimp"
# Use ffmpeg selected at image build time (system or static fallback) for media generation.
ENV MEDIA_FFMPEG_PATH="/usr/local/bin/ffmpeg-media"

WORKDIR /app
# Copy JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar
# Copy profiles
COPY profiles ./profiles

EXPOSE 8080

COPY entrypoint.sh .
RUN groupadd --gid 10001 printcalc \
    && useradd --uid 10001 --gid 10001 --home-dir /home/printcalc --create-home --shell /usr/sbin/nologin printcalc \
    && install -d -o 10001 -g 10001 /app/temp /app/storage_quotes /app/storage_orders \
        /app/storage_requests /app/storage_media /app/storage_shop \
    && chmod +x entrypoint.sh
ENV HOME=/home/printcalc
USER 10001:10001
ENTRYPOINT ["./entrypoint.sh"]
