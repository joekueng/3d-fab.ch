FROM --platform=linux/amd64 python:3.10-slim-bookworm

# Install system dependencies for OrcaSlicer (AppImage)
RUN apt-get update && apt-get install -y \
    wget \
    p7zip-full \
    libgl1 \
    libglib2.0-0 \
    libgtk-3-0 \
    libdbus-1-3 \
    libwebkit2gtk-4.1-0 \
    libwebkit2gtk-4.0-37 \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Download and extract OrcaSlicer
# Using v2.2.0 as a stable recent release
# We extract the AppImage to run it without FUSE
RUN wget -q https://github.com/SoftFever/OrcaSlicer/releases/download/v2.2.0/OrcaSlicer_Linux_V2.2.0.AppImage -O OrcaSlicer.AppImage \
    && 7z x OrcaSlicer.AppImage -o/opt/orcaslicer \
    && chmod -R +x /opt/orcaslicer \
    && rm OrcaSlicer.AppImage

# Add OrcaSlicer to PATH
ENV PATH="/opt/orcaslicer/usr/bin:${PATH}"

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Create directories for app and temp files
RUN mkdir -p /app/temp /app/profiles

# Copy application code
COPY . .

# Expose port
EXPOSE 8000

# Run the application
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
