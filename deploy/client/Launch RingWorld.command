#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DATA="$ROOT/.prism-data"
SOURCE="$ROOT/instance"
INSTANCE="$DATA/instances/RingWorld-Test"
MODS="$INSTANCE/.minecraft/mods"
LAUNCHER_DIR="$ROOT/.launcher/macos"
VERSION="11.0.3"
URL="https://github.com/PrismLauncher/PrismLauncher/releases/download/$VERSION/PrismLauncher-macOS-$VERSION.zip"

mkdir -p "$DATA/logs"

if [[ ! -f "$INSTANCE/mmc-pack.json" ]]; then
    rm -rf "$INSTANCE"
    mkdir -p "$DATA/instances"
    cp -R "$SOURCE" "$INSTANCE"
fi

# Refresh only bundle-managed files. Accounts, saves, options, screenshots,
# resource packs, and user-edited instance settings remain untouched.
mkdir -p "$MODS" "$INSTANCE/.minecraft/config"
RINGWORLD_JAR="$(find "$SOURCE/.minecraft/mods" -maxdepth 1 -type f \
    -name 'ringworld-*.jar' -print -quit)"
FABRIC_API_JAR="$(find "$SOURCE/.minecraft/mods" -maxdepth 1 -type f \
    -name 'fabric-api-*.jar' -print -quit)"
if [[ -z "$RINGWORLD_JAR" || -z "$FABRIC_API_JAR" ]]; then
    echo "The RingWorld bundle is incomplete. Download a fresh package."
    read -r -p "Press Return to close..."
    exit 1
fi
cp -f "$RINGWORLD_JAR" "$MODS/"
cp -f "$FABRIC_API_JAR" "$MODS/"
find "$MODS" -maxdepth 1 -type f -name 'ringworld-*.jar' \
    ! -name "$(basename "$RINGWORLD_JAR")" -delete
find "$MODS" -maxdepth 1 -type f -name 'fabric-api-*.jar' \
    ! -name "$(basename "$FABRIC_API_JAR")" -delete
cp -f "$SOURCE/mmc-pack.json" "$INSTANCE/mmc-pack.json"
if [[ ! -f "$INSTANCE/.minecraft/config/ringworld.properties" ]]; then
    cp "$SOURCE/.minecraft/config/ringworld.properties" \
        "$INSTANCE/.minecraft/config/ringworld.properties"
fi
echo "RingWorld client files are current."

PRISM=""
for app in \
    "$LAUNCHER_DIR/Prism Launcher.app" \
    "/Applications/Prism Launcher.app" \
    "$HOME/Applications/Prism Launcher.app"; do
    if [[ -x "$app/Contents/MacOS/prismlauncher" ]]; then
        PRISM="$app/Contents/MacOS/prismlauncher"
        break
    fi
done

if [[ -z "$PRISM" ]]; then
    echo "Downloading official Prism Launcher $VERSION..."
    mkdir -p "$LAUNCHER_DIR"
    ARCHIVE="$LAUNCHER_DIR/PrismLauncher.zip"
    curl -fL --retry 3 "$URL" -o "$ARCHIVE"
    ditto -x -k "$ARCHIVE" "$LAUNCHER_DIR"
    rm -f "$ARCHIVE"
    PRISM="$(find "$LAUNCHER_DIR" -type f -path '*/Contents/MacOS/prismlauncher' -print -quit)"
fi

if [[ -z "$PRISM" || ! -x "$PRISM" ]]; then
    echo "Could not locate Prism Launcher after installation."
    read -r -p "Press Return to close..."
    exit 1
fi

exec "$PRISM" -d "$DATA" -l RingWorld-Test
