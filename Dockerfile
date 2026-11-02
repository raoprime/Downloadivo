# Downloadivo - High-Performance Universal Media Downloader
# Universal 24/7 Cloud Docker Image (Hugging Face Spaces, Oracle Cloud, Render, VPS)
FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PORT=7860 \
    DOWNLOAD_DIR=/app/downloads \
    HOME=/home/user

# Install system dependencies: FFmpeg, Node.js (for YouTube JS signature solver), and curl
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    nodejs \
    npm \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Set up non-root user (mandatory for Hugging Face Spaces & security)
RUN useradd -m -u 1000 user
USER user
ENV PATH="/home/user/.local/bin:$PATH"

WORKDIR /app

# Install Python requirements as user
COPY --chown=user requirements.txt /app/
RUN pip install --no-cache-dir --user -r requirements.txt

# Copy source code and UI assets
COPY --chown=user main.py index.html /app/

# Create persistent download cache directory
RUN mkdir -p /app/downloads /tmp/downloads

# Expose default cloud container port 7860 and fallback 8899
EXPOSE 7860 8899

# Start production server dynamically on $PORT (defaults to 7860)
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-7860} --workers 2 --proxy-headers --forwarded-allow-ips '*'"]
