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
JAR_FILE="$SCRIPT_DIR/target/radio-shell-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "JAR dosyası bulunamadı. Derlemek için:"
    echo "  cd $SCRIPT_DIR && mvn clean package -DskipTests"
    exit 1
fi

if ! command -v ffplay >/dev/null 2>&1; then
    echo "⚠ ffplay bulunamadı. Lütfen ffmpeg yükleyin."
    echo "  macOS: brew install ffmpeg"
    echo "  Ubuntu/Debian: sudo apt install ffmpeg"
    echo "  Fedora: sudo dnf install ffmpeg"
    exit 1
fi

exec java --enable-native-access=ALL-UNNAMED -XX:TieredStopAtLevel=1 -Dspring.main.lazy-initialization=true -jar "$JAR_FILE"
