FROM python:3.12-slim

LABEL maintainer="Radio Shell"
LABEL description="Terminal FM Radio Player - Türkiye & Dünya"

# ffmpeg (ffplay içerir) + PulseAudio client yükle
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg pulseaudio-utils && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy requirements and install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source code
COPY src ./src

# Favori ve özel istasyon verisi için volume
VOLUME ["/root/.radio-shell"]

# İnteraktif terminal gerekli
ENV TERM=xterm-256color
ENV PYTHONPATH=/app

ENTRYPOINT ["python3", "-m", "src.radio.main"]
