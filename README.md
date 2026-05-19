# ♬ Radio Shell

**Terminal üzerinden Türkiye ve Dünya FM radyo istasyonlarını dinleyin.**

```
  ╔══════════════════════════════════════════════════════════════╗
  ║   ♬  ░░░ RADIO SHELL ░░░  ♬                                  ║
  ║   Terminal FM Radio Player - Türkiye & Dünya                 ║
  ║   v2.0.0 | Spring Boot 4 + Java 21                           ║
  ╚══════════════════════════════════════════════════════════════╝
```

---

## Gereksinimler

| Araç | Sürüm | Kurulum |
|------|-------|---------|
| Java JDK | 21+ | `brew install openjdk` |
| Apache Maven | 3.9+ | `brew install maven` |
| ffmpeg | Herhangi | `brew install ffmpeg` |
| Docker (isteğe bağlı) | 20+ | [docker.com](https://docker.com) |

---

## Kurulum ve Çalıştırma

### Yöntem 1 — Doğrudan Terminal (Önerilen)

```bash
# Projeyi derle
cd radio-shell
mvn clean package -DskipTests

# Başlat
./radio.sh
```

### Yöntem 1.5 — `radio` Komutunu Global Yap (macOS/Linux/Windows)

Bu adımla projeyi her terminalden sadece `radio` yazarak açabilirsiniz.

```bash
# 1) JAR üret
mvn clean package -DskipTests

# 2) Global komutu kur (macOS / Linux)
./scripts/install-command.sh

# 3) Yeni terminal aç ve çalıştır
radio
```

**Windows PowerShell:**

```powershell
# 1) JAR üret
mvn clean package -DskipTests

# 2) Global komutu kur
.\scripts\install-command.ps1

# 3) Yeni terminal aç ve çalıştır
radio
```

> Not: Windows'ta `install-command.ps1`, `%USERPROFILE%\bin\radio.cmd` oluşturur.
> `%USERPROFILE%\bin` PATH'e eklendiğinde komut her terminalden çalışır.

### Yöntem 2 — Docker

```bash
# Tek komutla build & çalıştır
./docker-run.sh

# Veya docker compose ile
docker compose up --build

# Veya manuel
docker build -t radio-shell:1.0.0 .
docker run --rm -it \
  -v ~/.radio-shell:/root/.radio-shell \
  radio-shell:1.0.0
```

> **macOS + Docker Ses Notu:** macOS'ta Docker container'ı ses cihazına doğrudan erişemez.
> Tam ses desteği için `./radio.sh` ile doğrudan terminalde çalıştırmanız önerilir.
> Linux'ta Docker içinden ses sorunsuz çalışır (`/dev/snd` mount ile).

---

## Komut Referansı

### İstasyon Listeleme

| Komut | Açıklama |
|-------|----------|
| `listele` | Tüm 90+ istasyonu tabloda listeler |
| `turkiye` | Sadece Türkiye istasyonlarını listeler |
| `ulkeler` | Mevcut ülkeleri ve istasyon sayılarını gösterir |
| `ulke -i <ülke>` | Belirli bir ülkenin istasyonları |
| `turler` | Mevcut müzik türlerini listeler |
| `tur -i <tür>` | Belirli bir türdeki istasyonlar |
| `ara -s <kelime>` | İsim, ülke veya türe göre arama |

### Oynatma

| Komut | Açıklama |
|-------|----------|
| `cal -i <id>` | İstasyonu çalar (ID veya isim) |
| `son` | Son çalınan istasyonu tekrar çalar |
| `dur` | Çalmayı durdurur |
| `durum` | Şu an çalan istasyonu ve varsa şarkı bilgisini gösterir |
| `ses -s <0-100>` | Ses seviyesini ayarlar |
| `sonraki` | Son listede bir sonraki istasyona geçer |
| `onceki` | Son listede bir önceki istasyona geçer |
| `karistir [-u ülke] [-t tür] [-f]` | Rastgele istasyon çalar |
| `uyku -d <dakika>` | Süre sonunda oynatmayı durdurur |
| `uyku iptal` | Uyku zamanlayıcısını iptal eder |
| `gecmis [-n adet]` | Son görülen şarkı bilgilerini listeler |

### Kayıt

| Komut | Açıklama |
|-------|----------|
| `kaydet` | Çalan yayını MP3 olarak kaydetmeye başlar |
| `kayitdur` | Aktif kaydı durdurur |

### Favoriler

| Komut | Açıklama |
|-------|----------|
| `favori -i <id>` | İstasyonu favorilere ekler/çıkarır (★) |
| `favoriler` | Favori istasyon listesi |

### Yönetim

| Komut | Açıklama |
|-------|----------|
| `kontrol [-i <id>]` | Akış URL'lerini kontrol eder |
| `ekle --id <id> --isim <isim> --ulke <ülke> --tur <tür> --url <url>` | Yeni istasyon ekler |
| `duzenle --id <id> [--isim ...] [--ulke ...] [--tur ...] [--url ...]` | Özel istasyonu düzenler |
| `iceaktar -d <dosya.m3u\|dosya.pls> [-u ülke] [-t tür] [-p önek]` | Playlist dosyasından özel istasyon ekler |
| `sil --id <id>` | Özel istasyonu siler |
| `tema [-i <tema>]` | Renk temasını listeler veya değiştirir |
| `sistem` | Radyo Shell, ffplay, CPU ve RAM kaynak kullanımını gösterir |

### Diğer

| Komut | Açıklama |
|-------|----------|
| `help` / `?` | Yardım menüsünü gösterir |
| `exit` / `q` | Uygulamadan çıkar |

---

## Kullanım Örnekleri

```bash
# Power FM'i çal
radio> cal -i tr-powerfm

# TRT FM'i çal
radio> cal -i tr-trt-fm

# Jazz istasyonu ara
radio> ara -s jazz

# BBC Radio listele
radio> ulke -i UK

# Ses seviyesini 70'e ayarla
radio> ses -s 70

# Son çalınan istasyonu yeniden başlat
radio> son

# 30 dakika sonra otomatik durdur
radio> uyku -d 30

# M3U playlist içe aktar
radio> iceaktar -d ~/Downloads/radyolar.m3u -u Özel -t Karma

# Power FM'i favorilere ekle
radio> favori -i tr-powerfm

# Özel istasyon ekle
radio> ekle --id benim-radyom --isim "Benim Radyom" --ulke Türkiye --tur Pop --url http://stream.example.com/live

# Özel istasyonu düzenle
radio> duzenle --id benim-radyom --url https://stream.example.com/live

# Durdur
radio> dur
```

---

## Dahili İstasyonlar (90+ Adet)

### Türkiye
| İstasyon | Tür | ID |
|----------|-----|-----|
| TRT FM | Pop/Türkçe | `tr-trt-fm` |
| TRT Radyo 1 | Haber/Kültür | `tr-trt-radyo1` |
| TRT Radyo 3 | Klasik/Caz | `tr-trt-radyo3` |
| TRT Nağme | Türk Halk Müziği | `tr-trt-nagme` |
| TRT Kurdî | Kürtçe | `tr-trt-kurdi` |
| PowerTürk | Türkçe Pop | `tr-powerturk` |
| Power FM | Pop/Dance | `tr-powerfm` |
| Kral FM | Türkçe Pop | `tr-kralmuzik` |
| Kral Pop | Pop | `tr-kralpop` |
| SlowTürk | Slow/Romantik | `tr-slowturk` |
| JoyTürk | Rock/Alternatif | `tr-joyturk` |
| Joy FM | Yabancı Pop | `tr-joyfm` |
| Süper FM | Türkçe Pop | `tr-superfm` |
| Best FM | Pop | `tr-bestfm` |
| Metro FM | Dance/Electronic | `tr-metro` |
| Radyo D | Pop | `tr-radyod` |
| Number One FM | Pop/Dance | `tr-numberone` |
| Radyo Eksen | Rock/Indie | `tr-radyoeksen` |
| Virgül Radyo | Alternatif | `tr-virgulradyo` |
| Açık Radyo | Karma/Kültür | `tr-acikmuzik` |

### Dünya
| İstasyon | Ülke | Tür | ID |
|----------|------|-----|-----|
| BBC Radio 1-4 | UK | Çeşitli | `uk-bbc-radio1` ... |
| Classic FM | UK | Klasik | `uk-classicfm` |
| KEXP 90.3 FM | USA | Indie/Alt | `us-kexp` |
| WFMU | USA | Freeform | `us-wfmu` |
| Jazz24 | USA | Jazz | `us-jazz24` |
| WQXR Classical | USA | Klasik | `us-klassik` |
| SomaFM Groove Salad | USA | Ambient/Chill | `us-soma-groovesalad` |
| SomaFM DEF CON | USA | Electronic | `us-soma-defcon` |
| SomaFM Drone Zone | USA | Ambient/Space | `us-soma-dronezone` |
| FIP Radio | Fransa | Eclectic | `fr-fip` |
| France Musique | Fransa | Klasik | `fr-francemusique` |
| France Inter | Fransa | Talk/Kültür | `fr-franceinter` |
| SWR3 | Almanya | Pop/Rock | `de-swr3` |
| Bayern 3 | Almanya | Pop | `de-bayern3` |
| Deutschlandfunk Kultur | Almanya | Kültür/Klasik | `de-dlfkultur` |
| RAI Radio 2 | İtalya | Pop | `it-rai-radio2` |
| Los 40 Principales | İspanya | Pop | `es-los40` |
| Radio 538 | Hollanda | Pop/Dance | `nl-radio538` |
| J-Wave | Japonya | Pop/Urban | `jp-jwave` |
| Antena 1 | Brezilya | Classic Hits | `br-antena1` |

---

## Yeni İstasyon Nasıl Eklenir?

**Yöntem 1 — Uygulama içinden:**
```bash
radio> ekle --id my-station --isim "Benim Radyom" --ulke Türkiye --tur Pop --url http://stream.example.com/radio.mp3
```

**Yöntem 2 — JSON dosyası:**

`~/.radio-shell/custom-stations.json` dosyasını düzenleyin:

```json
{
  "stations": [
    {
      "id": "my-station",
      "name": "Benim Radyom",
      "country": "Türkiye",
      "genre": "Pop",
      "url": "http://stream.example.com/radio.mp3",
      "favorite": false
    }
  ]
}
```

---

## Proje Yapısı

```
radio-shell/
├── Dockerfile                          # Multi-stage Docker build
├── docker-compose.yml                  # Docker Compose konfigürasyonu
├── docker-run.sh                       # Platform-aware Docker başlatıcı
├── radio.sh                            # Doğrudan terminal başlatıcı
├── pom.xml                             # Maven bağımlılıkları
└── src/main/
    ├── java/com/radio/
    │   ├── RadioShellApplication.java  # Uygulama girişi
    │   ├── command/RadioCommands.java  # Shell komutları
    │   ├── config/
    │   │   ├── RadioConfig.java        # Konfigürasyon özellikleri
    │   │   └── ShellConfig.java        # Temizleme (cleanup)
    │   ├── model/
    │   │   ├── RadioStation.java       # İstasyon veri modeli (record)
    │   │   ├── StationList.java        # JSON wrapper
    │   │   └── UserSettings.java       # Kalıcı kullanıcı ayarları
    │   ├── player/AudioPlayer.java     # ffplay süreç yönetimi
    │   ├── service/StationService.java # İstasyon CRUD & arama
    │   ├── service/SettingsService.java # Ses ve son istasyon ayarları
    │   └── shell/InteractiveShell.java # İnteraktif döngü
    └── resources/
        ├── application.properties      # Uygulama ayarları
        └── stations.json               # 90+ dahili istasyon
```

---

## Teknoloji Stack

| Katman | Teknoloji |
|--------|-----------|
| Dil | Java 21 |
| Framework | Spring Boot 4.0.5 |
| Shell | Spring Shell 4.0.1 |
| Ses Oynatıcı | ffplay (ffmpeg) |
| Veri Formatı | JSON (Jackson) |
| Build | Apache Maven 3.9 |
| Container | Docker (multi-stage) |

---

## Kalıcı Veri

Favoriler ve özel istasyonlar `~/.radio-shell/` dizininde saklanır:

```
~/.radio-shell/
├── favorites.json        # Favori istasyon ID'leri
├── custom-stations.json  # Kullanıcı tarafından eklenen istasyonlar
├── settings.json         # Ses seviyesi ve son çalınan istasyon
├── theme                 # Seçili renk teması
└── recordings/           # MP3 kayıtları
```

Docker kullanırken bu dizin volume olarak mount edilir, verileriniz container yeniden başlatmalarında korunur.

---

---

# ♬ Radio Shell — English

**Listen to FM radio stations from Turkey and around the world, directly from your terminal.**

---

## Requirements

| Tool | Version | Install |
|------|---------|---------|
| Java JDK | 21+ | `brew install openjdk` |
| Apache Maven | 3.9+ | `brew install maven` |
| ffmpeg | Any | `brew install ffmpeg` |
| Docker (optional) | 20+ | [docker.com](https://docker.com) |

---

## Setup & Running

### Method 1 — Direct Terminal (Recommended)

```bash
# Build the project
cd radio-shell
mvn clean package -DskipTests

# Start
./radio.sh
```

### Method 2 — Docker

```bash
# Build & run in one command
./docker-run.sh

# Or with docker compose
docker compose up --build

# Or manually
docker build -t radio-shell:1.0.0 .
docker run --rm -it \
  -v ~/.radio-shell:/root/.radio-shell \
  radio-shell:1.0.0
```

> **macOS + Docker Audio Note:** Docker on macOS cannot directly access the audio device.
> For full audio support, run directly in terminal with `./radio.sh`.
> On Linux, audio works inside Docker containers (via `/dev/snd` mount).

---

## Command Reference

### Listing Stations

| Command | Description |
|---------|-------------|
| `listele` | List all 90+ stations in a table |
| `turkiye` | List Turkish stations only |
| `ulkeler` | Show available countries and station counts |
| `ulke -i <country>` | Stations from a specific country |
| `turler` | List available music genres |
| `tur -i <genre>` | Stations of a specific genre |
| `ara -s <query>` | Search by name, country, or genre |

### Playback

| Command | Description |
|---------|-------------|
| `cal -i <id>` | Play a station (by ID or name) |
| `son` | Play the last station again |
| `dur` | Stop playback |
| `durum` | Show currently playing station and song metadata when available |
| `ses -s <0-100>` | Set volume level |
| `sonraki` | Move to the next station in the last list |
| `onceki` | Move to the previous station in the last list |
| `karistir [-u country] [-t genre] [-f]` | Play a random station |
| `uyku -d <minutes>` | Stop playback after a delay |
| `uyku iptal` | Cancel the sleep timer |
| `gecmis [-n count]` | Show recent song metadata |

### Recording

| Command | Description |
|---------|-------------|
| `kaydet` | Start recording the current stream as MP3 |
| `kayitdur` | Stop the active recording |

### Favorites

| Command | Description |
|---------|-------------|
| `favori -i <id>` | Toggle station as favorite (★) |
| `favoriler` | Show favorite stations list |

### Management

| Command | Description |
|---------|-------------|
| `kontrol [-i <id>]` | Check stream URLs |
| `ekle --id <id> --isim <name> --ulke <country> --tur <genre> --url <url>` | Add custom station |
| `duzenle --id <id> [--isim ...] [--ulke ...] [--tur ...] [--url ...]` | Edit a custom station |
| `iceaktar -d <file.m3u\|file.pls> [-u country] [-t genre] [-p prefix]` | Import custom stations from a playlist |
| `sil --id <id>` | Remove a custom station |
| `tema [-i <theme>]` | List or change color theme |

### Other

| Command | Description |
|---------|-------------|
| `help` / `?` | Show help menu |
| `exit` / `q` | Quit the application |

---

## Usage Examples

```bash
# Play Power FM
radio> cal -i tr-powerfm

# Play BBC Radio 1
radio> cal -i uk-bbc-radio1

# Search for jazz stations
radio> ara -s jazz

# List UK stations
radio> ulke -i UK

# Set volume to 70
radio> ses -s 70

# Play the last station again
radio> son

# Stop automatically after 30 minutes
radio> uyku -d 30

# Import an M3U playlist
radio> iceaktar -d ~/Downloads/stations.m3u -u Custom -t Mixed

# Add Power FM to favorites
radio> favori -i tr-powerfm

# Add a custom station
radio> ekle --id my-station --isim "My Radio" --ulke Turkey --tur Pop --url http://stream.example.com/live

# Edit a custom station
radio> duzenle --id my-station --url https://stream.example.com/live

# Stop playback
radio> dur
```

---

## Adding New Stations

**Method 1 — From within the app:**
```bash
radio> ekle --id my-station --isim "My Radio" --ulke Turkey --tur Pop --url http://stream.example.com/radio.mp3
```

**Method 2 — JSON file:**

Edit `~/.radio-shell/custom-stations.json`:

```json
{
  "stations": [
    {
      "id": "my-station",
      "name": "My Radio",
      "country": "Turkey",
      "genre": "Pop",
      "url": "http://stream.example.com/radio.mp3",
      "favorite": false
    }
  ]
}
```

---

## Project Structure

```
radio-shell/
├── Dockerfile                          # Multi-stage Docker build
├── docker-compose.yml                  # Docker Compose configuration
├── docker-run.sh                       # Platform-aware Docker launcher
├── radio.sh                            # Direct terminal launcher
├── pom.xml                             # Maven dependencies
└── src/main/
    ├── java/com/radio/
    │   ├── RadioShellApplication.java  # Application entry point
    │   ├── command/RadioCommands.java  # Shell commands
    │   ├── config/
    │   │   ├── RadioConfig.java        # Configuration properties
    │   │   └── ShellConfig.java        # Cleanup on shutdown
    │   ├── model/
    │   │   ├── RadioStation.java       # Station data model (record)
    │   │   ├── StationList.java        # JSON wrapper
    │   │   └── UserSettings.java       # Persistent user settings
    │   ├── player/AudioPlayer.java     # ffplay process management
    │   ├── service/StationService.java # Station CRUD & search
    │   ├── service/SettingsService.java # Volume and last station settings
    │   └── shell/InteractiveShell.java # Interactive shell loop
    └── resources/
        ├── application.properties      # Application settings
        └── stations.json               # 90+ built-in stations
```

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.5 |
| Shell | Spring Shell 4.0.1 |
| Audio Player | ffplay (ffmpeg) |
| Data Format | JSON (Jackson) |
| Build Tool | Apache Maven 3.9 |
| Container | Docker (multi-stage) |

---

## Persistent Data

Favorites, custom stations, settings, theme, and recordings are stored in `~/.radio-shell/`:

```
~/.radio-shell/
├── favorites.json        # Favorite station IDs
├── custom-stations.json  # User-added stations
├── settings.json         # Volume and last station
├── theme                 # Selected color theme
└── recordings/           # MP3 recordings
```

When using Docker, this directory is mounted as a volume so your data persists across container restarts.

---

## License

MIT License — free to use, modify, and distribute.
