# Stage 1: Build Java JAR
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
# Download dependencies first to cache them
RUN ./gradlew dependencies --no-daemon
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Runtime Environment
FROM eclipse-temurin:21-jre-jammy

# Install system dependencies for OrcaSlicer (same as before)
RUN apt-get update && apt-get install -y \
    wget \
    p7zip-full \
    libgl1 \
    libglib2.0-0 \
    libgtk-3-0 \
    libdbus-1-3 \
    libwebkit2gtk-4.0-37 \
    && rm -rf /var/lib/apt/lists/*

# Install OrcaSlicer
WORKDIR /opt
RUN wget -q https://github.com/SoftFever/OrcaSlicer/releases/download/v2.2.0/OrcaSlicer_Linux_V2.2.0.AppImage -O OrcaSlicer.AppImage \
    && 7z x OrcaSlicer.AppImage -o/opt/orcaslicer \
    && chmod -R +x /opt/orcaslicer \
    && rm OrcaSlicer.AppImage

ENV PATH="/opt/orcaslicer/usr/bin:${PATH}"
# Set Slicer Path env variable for Java app
ENV SLICER_PATH="/opt/orcaslicer/AppRun"

WORKDIR /app
# Copy JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar
# Copy profiles
COPY profiles ./profiles

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
