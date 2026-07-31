#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DATA="$ROOT/.prism-data"
SOURCE="$ROOT/instance"
INSTANCE="$DATA/instances/RingWorld-Test"
MODS="$INSTANCE/.minecraft/mods"
LAUNCHER_DIR="$ROOT/.launcher/linux"
VERSION="11.0.3"

mkdir -p "$DATA/logs"

if [ ! -f "$INSTANCE/mmc-pack.json" ]; then
    rm -rf "$INSTANCE"
    mkdir -p "$DATA/instances"
    cp -R "$SOURCE" "$INSTANCE"
fi

# Refresh only bundle-managed files. Accounts, saves, options, screenshots,
# resource packs, and user-edited instance settings remain untouched.
mkdir -p "$MODS" "$INSTANCE/.minecraft/config"
RINGWORLD_JAR=$(find "$SOURCE/.minecraft/mods" -maxdepth 1 -type f \
    -name 'ringworld-*.jar' -print -quit)
FABRIC_API_JAR=$(find "$SOURCE/.minecraft/mods" -maxdepth 1 -type f \
    -name 'fabric-api-*.jar' -print -quit)
if [ -z "$RINGWORLD_JAR" ] || [ -z "$FABRIC_API_JAR" ]; then
    echo "The RingWorld bundle is incomplete. Download a fresh package."
    exit 1
fi
cp -f "$RINGWORLD_JAR" "$MODS/"
cp -f "$FABRIC_API_JAR" "$MODS/"
find "$MODS" -maxdepth 1 -type f -name 'ringworld-*.jar' \
    ! -name "$(basename "$RINGWORLD_JAR")" -delete
find "$MODS" -maxdepth 1 -type f -name 'fabric-api-*.jar' \
    ! -name "$(basename "$FABRIC_API_JAR")" -delete
cp -f "$SOURCE/mmc-pack.json" "$INSTANCE/mmc-pack.json"
if [ ! -f "$INSTANCE/.minecraft/config/ringworld.properties" ]; then
    cp "$SOURCE/.minecraft/config/ringworld.properties" \
        "$INSTANCE/.minecraft/config/ringworld.properties"
fi
echo "RingWorld client files are current."

case "$(uname -m)" in
    aarch64|arm64) ASSET="PrismLauncher-Linux-aarch64.AppImage" ;;
    x86_64|amd64) ASSET="PrismLauncher-Linux-x86_64.AppImage" ;;
    *) echo "Unsupported Linux architecture: $(uname -m)"; exit 1 ;;
esac

PRISM="$LAUNCHER_DIR/$ASSET"
if [ ! -x "$PRISM" ]; then
    echo "Downloading official Prism Launcher $VERSION..."
    mkdir -p "$LAUNCHER_DIR"
    curl -fL --retry 3 \
        "https://github.com/PrismLauncher/PrismLauncher/releases/download/$VERSION/$ASSET" \
        -o "$PRISM"
    chmod +x "$PRISM"
fi

APPIMAGE_EXTRACT_AND_RUN=1 exec "$PRISM" -d "$DATA" -l RingWorld-Test
