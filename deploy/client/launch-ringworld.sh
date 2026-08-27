#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DATA="$ROOT/.prism-data"
SOURCE="$ROOT/instance"
LAUNCHER_DIR="$ROOT/.launcher/linux"
VERSION="11.0.3"
LOADER_FILE="$ROOT/RINGWORLD-LOADER.txt"
LOADER=$(tr -d '\r\n' < "$LOADER_FILE" 2>/dev/null || true)
case "$LOADER" in
    fabric) INSTANCE_ID="RingWorld-Test" ;;
    neoforge) INSTANCE_ID="RingWorld-NeoForge" ;;
    *) echo "The RingWorld bundle is incomplete. Download a fresh package."; exit 1 ;;
esac
INSTANCE="$DATA/instances/$INSTANCE_ID"
MODS="$INSTANCE/.minecraft/mods"

mkdir -p "$DATA/logs"

FRESH_INSTANCE=false
if [ ! -f "$INSTANCE/mmc-pack.json" ]; then
    rm -rf "$INSTANCE"
    mkdir -p "$DATA/instances"
    cp -R "$SOURCE" "$INSTANCE"
    FRESH_INSTANCE=true
fi

# Refresh only bundle-managed files. Accounts, saves, options, screenshots,
# resource packs, and user-edited instance settings remain untouched.
mkdir -p "$MODS" "$INSTANCE/.minecraft/config"
if [ "$LOADER" = "fabric" ]; then
    RINGWORLD_JAR=$(find "$SOURCE/.minecraft/mods" -maxdepth 1 -type f \
        -name 'ringworld-*.jar' ! -name 'ringworld-neoforge-*.jar' -print -quit)
else
    RINGWORLD_JAR=$(find "$SOURCE/.minecraft/mods" -maxdepth 1 -type f \
        -name 'ringworld-neoforge-*.jar' -print -quit)
fi
if [ -z "$RINGWORLD_JAR" ]; then
    echo "The RingWorld bundle is incomplete. Download a fresh package."
    exit 1
fi
cp -f "$RINGWORLD_JAR" "$MODS/"
find "$MODS" -maxdepth 1 -type f -name 'ringworld-*.jar' \
    ! -name "$(basename "$RINGWORLD_JAR")" -delete
if [ "$LOADER" = "fabric" ]; then
    FABRIC_API_JAR=$(find "$SOURCE/.minecraft/mods" -maxdepth 1 -type f \
        -name 'fabric-api-*.jar' -print -quit)
    if [ -z "$FABRIC_API_JAR" ]; then
        echo "The Fabric RingWorld bundle is incomplete. Download a fresh package."
        exit 1
    fi
    cp -f "$FABRIC_API_JAR" "$MODS/"
    find "$MODS" -maxdepth 1 -type f -name 'fabric-api-*.jar' \
        ! -name "$(basename "$FABRIC_API_JAR")" -delete
elif [ "$FRESH_INSTANCE" = true ]; then
    # A different-loader bundle may have been extracted over the same outer
    # directory. Remove that stale packaged dependency only from a newly
    # created NeoForge instance; never manage user mods on later launches.
    find "$MODS" -maxdepth 1 -type f -name 'fabric-api-*.jar' -delete
fi
cp -f "$SOURCE/mmc-pack.json" "$INSTANCE/mmc-pack.json"
if [ "$LOADER" = "neoforge" ]; then
    if [ -f "$SOURCE/ringworld-managed-neoforge-patch.txt" ]; then
        mkdir -p "$INSTANCE/patches"
        cp -f "$SOURCE/patches/net.neoforged.json" "$INSTANCE/patches/net.neoforged.json"
        cp -f "$SOURCE/ringworld-managed-neoforge-patch.txt" "$INSTANCE/"
    elif [ -f "$INSTANCE/ringworld-managed-neoforge-patch.txt" ]; then
        # Retire only our own loader patch when returning to official metadata.
        rm -f "$INSTANCE/patches/net.neoforged.json" "$INSTANCE/ringworld-managed-neoforge-patch.txt"
    fi
fi
if [ ! -f "$INSTANCE/.minecraft/config/ringworld.properties" ]; then
    cp "$SOURCE/.minecraft/config/ringworld.properties" \
        "$INSTANCE/.minecraft/config/ringworld.properties"
fi
# Minecraft 26.1.2 requires Java 25. Preserve every other instance setting,
# but let Prism replace a stale Java 21 path from an older RingWorld bundle.
for setting in "AutomaticJava=true" "OverrideJavaLocation=false"; do
    key=${setting%%=*}
    value=${setting#*=}
    temporary="$INSTANCE/instance.cfg.ringworld.tmp"
    awk -v key="$key" -v value="$value" '
        BEGIN { found = 0 }
        index($0, key "=") == 1 { print key "=" value; found = 1; next }
        { print }
        END { if (!found) print key "=" value }
    ' "$INSTANCE/instance.cfg" > "$temporary"
    mv "$temporary" "$INSTANCE/instance.cfg"
done
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

APPIMAGE_EXTRACT_AND_RUN=1 exec "$PRISM" -d "$DATA" -l "$INSTANCE_ID"
