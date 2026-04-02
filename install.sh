#!/bin/bash
set -e

# Renkler
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}♬ Radio Shell - Jet Kurulum Başlatılıyor...${NC}"

# 1. Gereksinim Kontrolleri
echo -e "${YELLOW}[1/4] Sistem gereksinimleri kontrol ediliyor...${NC}"

if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java bulunamadı. Lütfen Java 25 yükleyin.${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo -e "${RED}❌ Java sürümü yetersiz (Bulunan: $JAVA_VERSION). Java 21+ (Önerilen 25) gereklidir.${NC}"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven bulunamadı. Lütfen 'brew install maven' veya uygun komutla yükleyin.${NC}"
    exit 1
fi

if ! command -v ffplay &> /dev/null; then
    echo -e "${YELLOW}⚠ ffplay bulunamadı. Radyo çalmak için ffmpeg gereklidir.${NC}"
    echo -e "Yüklemek için: brew install ffmpeg"
fi

# 2. Derleme
echo -e "${YELLOW}[2/4] Uygulama native binary olarak derleniyor (Bu işlem birkaç dakika sürebilir)...${NC}"
# Native profile'ı pom.xml'de değil buildtools plugininde tanımlı olduğu için doğrudan compile yeterli
mvn clean native:compile -DskipTests

# 3. Kurulum
echo -e "${YELLOW}[3/4] Kurulum yapılıyor...${NC}"
BINARY_PATH="target/radio-shell"
INSTALL_DEST="/usr/local/bin/radio"

if [ ! -f "$BINARY_PATH" ]; then
    echo -e "${RED}❌ Derleme başarısız oldu, binary dosyası bulunamadı.${NC}"
    exit 1
fi

echo -e "${BLUE}Dosya kopyalanıyor: $INSTALL_DEST${NC}"
if [ -w "/usr/local/bin" ]; then
    cp "$BINARY_PATH" "$INSTALL_DEST"
else
    echo -e "${YELLOW}Şifreniz istenebilir (sudo)...${NC}"
    sudo cp "$BINARY_PATH" "$INSTALL_DEST"
fi

sudo chmod +x "$INSTALL_DEST"

# 4. Sonuç
echo -e "${GREEN}[4/4] Kurulum tamamlandı!${NC}"
echo -e "${BLUE}Artık terminalde herhangi bir yerden ${GREEN}radio${BLUE} yazarak uygulamayı jet hızında başlatabilirsiniz.${NC}"
echo -e "${YELLOW}Not: İlk açılışta işletim sistemi güvenlik onayı isteyebilir.${NC}"
