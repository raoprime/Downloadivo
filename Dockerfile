# Downloadivo - High-Performance Universal Media Downloader
# Render.com / Cloud Docker Image
FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PORT=10000 \
    DOWNLOAD_DIR=/tmp/downloads \
    EPHEMERAL_STORAGE=1

# Install system dependencies: FFmpeg + curl
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python requirements
COPY requirements.txt /app/
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code and UI assets
COPY main.py index.html /app/
COPY assets /app/assets

# Create download cache directory
RUN mkdir -p /tmp/downloads

EXPOSE 10000

CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-10000} --workers 1 --proxy-headers --forwarded-allow-ips '*'"]
