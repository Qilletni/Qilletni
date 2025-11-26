#!/bin/bash

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

INSTALL_DIR="$HOME/.qilletni/bin"
mkdir -p "$INSTALL_DIR"

if [ -n "$USE_SNAPSHOT" ]; then
  TOOLCHAIN_VERSION="snapshot"
  QPM_VERSION="snapshot"
else
  TOOLCHAIN_VERSION="v1.0.0"
  QPM_VERSION="v1.0.0"
fi

# Install Qilletni Toolchain
url=$(curl -s https://api.github.com/repos/Qilletni/QilletniToolchain/releases/tags/${TOOLCHAIN_VERSION} | grep "browser_download_url.*tar.gz" | cut -d'"' -f4)
curl -L "$url" -o /tmp/qilletni.tar.gz
tar -xzf /tmp/qilletni.tar.gz -C "$INSTALL_DIR" && rm /tmp/qilletni.tar.gz

# Install QPM
url=$(curl -s https://api.github.com/repos/Qilletni/QPMCLI/releases/tags/${QPM_VERSION} | grep "browser_download_url.*tar.gz" | cut -d'"' -f4)
curl -L "$url" -o /tmp/qpm.tar.gz
tar -xzf /tmp/qpm.tar.gz -C "$INSTALL_DIR" && rm /tmp/qpm.tar.gz

chmod -R 755 "$INSTALL_DIR"

if ! grep -q 'export PATH="$HOME/.qilletni/bin:$PATH"' "$HOME/.bashrc"; then
  echo 'export PATH="$HOME/.qilletni/bin:$PATH"' >> "$HOME/.bashrc"
fi

export PATH="$INSTALL_DIR:$PATH"

echo "Installed Qilletni, please reload your terminal or run 'source ~/.bashrc'"
echo "Verify install with:"
echo -e "  ${BOLD}qilletni --version${RESET}"
echo -e "  ${BOLD}qpm --version${RESET}"
