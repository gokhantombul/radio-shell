#!/bin/bash
# Radio Shell - Terminal FM Radio Player
# Kullanım: ./radio.sh

# Sembolik linkin asıl hedefini bularak gerçek proje dizinini alır
SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
JAR_FILE="$SCRIPT_DIR/target/radio-shell-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "JAR dosyası bulunamadı. Derlemek için:"
    echo "  cd $SCRIPT_DIR && mvn clean package -DskipTests"
    exit 1
fi

if ! command -v ffplay &> /dev/null; then
    echo "⚠ ffplay bulunamadı. Lütfen ffmpeg yükleyin: brew install ffmpeg"
    exit 1
fi

exec java --enable-native-access=ALL-UNNAMED -XX:TieredStopAtLevel=1 -Dspring.main.lazy-initialization=true -jar "$JAR_FILE"
