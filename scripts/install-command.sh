#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAUNCHER="$PROJECT_DIR/radio.sh"

if [ ! -x "$LAUNCHER" ]; then
  echo "radio.sh bulunamadı veya çalıştırılabilir değil: $LAUNCHER"
  exit 1
fi

install_link() {
  local target_dir="$1"
  mkdir -p "$target_dir"
  ln -sfn "$LAUNCHER" "$target_dir/radio"
  echo "✅ Komut oluşturuldu: $target_dir/radio -> $LAUNCHER"
}

if [ -w "/usr/local/bin" ]; then
  install_link "/usr/local/bin"
else
  install_link "$HOME/.local/bin"
  case ":${PATH}:" in
    *":$HOME/.local/bin:"*)
      ;;
    *)
      echo
      echo "ℹ PATH güncellemesi gerekli. Shell profilinize şunu ekleyin:"
      echo '  export PATH="$HOME/.local/bin:$PATH"'
      ;;
  esac
fi

echo "Artık terminalde doğrudan 'radio' yazarak başlatabilirsiniz."
