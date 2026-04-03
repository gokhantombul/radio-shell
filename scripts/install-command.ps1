$ErrorActionPreference = 'Stop'

$projectDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$userBin = Join-Path $HOME 'bin'
$cmdPath = Join-Path $userBin 'radio.cmd'

New-Item -ItemType Directory -Force -Path $userBin | Out-Null

$cmdContent = @"
@echo off
setlocal
set "PROJECT_DIR=$projectDir"
set "JAR_FILE=%PROJECT_DIR%\target\radio-shell-1.0.0.jar"

if not exist "%JAR_FILE%" (
  echo JAR dosyasi bulunamadi. Once proje klasorunde su komutu calistirin:
  echo   mvn clean package -DskipTests
  exit /b 1
)

where ffplay >nul 2>nul
if errorlevel 1 (
  echo ffplay bulunamadi. Lutfen ffmpeg yukleyin ve PATH'e ekleyin.
  exit /b 1
)

java --enable-native-access=ALL-UNNAMED -XX:TieredStopAtLevel=1 -Dspring.main.lazy-initialization=true -jar "%JAR_FILE%"
"@

Set-Content -Path $cmdPath -Value $cmdContent -Encoding ascii

Write-Host "✅ Komut olusturuldu: $cmdPath"
Write-Host "PATH'e su klasoru ekleyin (Windows Environment Variables): $userBin"
Write-Host "Sonrasinda terminalde dogrudan 'radio' calisacaktir."
