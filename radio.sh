#!/usr/bin/env bash
set -euo pipefail

# Radio Shell - Terminal FM Radio Player
# Kullanım: ./radio.sh

resolve_script_dir() {
    local src="${BASH_SOURCE[0]}"
    while [ -h "$src" ]; do
        local dir
        dir="$(cd -P "$(dirname "$src")" && pwd)"
        src="$(readlink "$src")"
        [[ "$src" != /* ]] && src="$dir/$src"
    done
    cd -P "$(dirname "$src")" && pwd
}

SCRIPT_DIR="$(resolve_script_dir)"

if ! command -v ffplay >/dev/null 2>&1; then
    echo "⚠ ffplay bulunamadı. Lütfen ffmpeg yükleyin."
    echo "  macOS: brew install ffmpeg"
    echo "  Ubuntu/Debian: sudo apt install ffmpeg"
    echo "  Fedora: sudo dnf install ffmpeg"
    exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
    echo "⚠ python3 bulunamadı. Lütfen Python yükleyin."
    exit 1
fi

cd "$SCRIPT_DIR"

if [ ! -d "venv" ]; then
    echo "ℹ Python sanal ortamı (venv) oluşturuluyor..."
    python3 -m venv venv
    ./venv/bin/pip install -r requirements.txt -q
fi

# Set PYTHONPATH so absolute imports like src.radio.* work
export PYTHONPATH="$SCRIPT_DIR"

exec ./venv/bin/python3 -m src.radio.main
