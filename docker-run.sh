#!/bin/bash
# Radio Shell - Docker Başlatıcı
# Kullanım: ./docker-run.sh [build]

set -e

IMAGE="radio-shell:1.0.0"
CONTAINER="radio-shell"
DATA_DIR="$HOME/.radio-shell"

# Veri dizinini oluştur
mkdir -p "$DATA_DIR"

# Build parametresi verilmişse veya image yoksa build et
if [[ "$1" == "build" ]] || ! docker image inspect "$IMAGE" &>/dev/null; then
    echo "🔨 Docker image build ediliyor..."
    docker build -t "$IMAGE" "$(dirname "$0")"
    echo "✓ Build tamamlandı."
fi

# Çalışan container varsa durdur
if docker ps -q -f name="$CONTAINER" | grep -q .; then
    echo "⚠ Çalışan '$CONTAINER' durduruluyor..."
    docker stop "$CONTAINER" >/dev/null
fi

echo ""
echo "♬ Radio Shell başlatılıyor..."
echo ""

# Platform tespiti
OS="$(uname -s)"

if [[ "$OS" == "Linux" ]]; then
    # Linux: PulseAudio socket + ALSA cihazı
    PULSE_SOCKET="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/pulse/native"
    PULSE_COOKIE="${HOME}/.config/pulse/cookie"

    docker run --rm -it \
        --name "$CONTAINER" \
        --device /dev/snd:/dev/snd \
        -v "$PULSE_SOCKET:/run/pulse/native" \
        -v "$PULSE_COOKIE:/root/.config/pulse/cookie:ro" \
        -e PULSE_SERVER=unix:/run/pulse/native \
        -v "$DATA_DIR:/root/.radio-shell" \
        -e TERM=xterm-256color \
        "$IMAGE"

elif [[ "$OS" == "Darwin" ]]; then
    # macOS: PulseAudio gerekmez — BlackHole veya VirtualBox Audio ile çalışır
    # En basit yol: ses desteği olmadan çalıştır (stream URL'lerini kontrol için)
    # Tam ses için: brew install pulseaudio ve aşağıdaki socket'i aktif edin
    echo "ℹ macOS: Ses için host makinenizde PulseAudio kurulu olması gerekir."
    echo "  Alternatif: Doğrudan terminalde './radio.sh' ile çalıştırın."
    echo ""

    PULSE_SOCKET="${HOME}/.config/pulse/native"

    if [[ -S "$PULSE_SOCKET" ]]; then
        docker run --rm -it \
            --name "$CONTAINER" \
            -v "$PULSE_SOCKET:/run/pulse/native" \
            -e PULSE_SERVER=unix:/run/pulse/native \
            -v "$DATA_DIR:/root/.radio-shell" \
            -e TERM=xterm-256color \
            "$IMAGE"
    else
        # Ses olmadan sadece UI modunda çalıştır
        docker run --rm -it \
            --name "$CONTAINER" \
            -v "$DATA_DIR:/root/.radio-shell" \
            -e TERM=xterm-256color \
            "$IMAGE"
    fi
else
    # Genel
    docker run --rm -it \
        --name "$CONTAINER" \
        -v "$DATA_DIR:/root/.radio-shell" \
        -e TERM=xterm-256color \
        "$IMAGE"
fi
