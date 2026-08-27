#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DATA="$ROOT/.prism-data"
SOURCE="$ROOT/instance"
LAUNCHER_DIR="$ROOT/.launcher/macos"
VERSION="11.0.3"
URL="https://github.com/PrismLauncher/PrismLauncher/releases/download/$VERSION/PrismLauncher-macOS-$VERSION.zip"
LOADER_FILE="$ROOT/RINGWORLD-LOADER.txt"
LOADER="$(tr -d '\r\n' < "$LOADER_FILE" 2>/dev/null || true)"
case "$LOADER" in
    fabric) INSTANCE_ID="RingWorld-Test" ;;
    neoforge) INSTANCE_ID="RingWorld-NeoForge" ;;
    *)
        echo "The RingWorld bundle is incomplete. Download a fresh package."
        read -r -p "Press Return to close..."
        exit 1
        ;;
esac
INSTANCE="$DATA/instances/$INSTANCE_ID"
MODS="$INSTANCE/.minecraft/mods"

mkdir -p "$DATA/logs"

FRESH_INSTANCE=false
if [[ ! -f "$INSTANCE/mmc-pack.json" ]]; then
    rm -rf "$INSTANCE"
    mkdir -p "$DATA/instances"
    cp -R "$SOURCE" "$INSTANCE"
    FRESH_INSTANCE=true
fi

# Refresh only bundle-managed files. Accounts, saves, options, screenshots,
# resource packs, and user-edited instance settings remain untouched.
mkdir -p "$MODS" "$INSTANCE/.minecraft/config"
if [[ "$LOADER" == "fabric" ]]; then
    RINGWORLD_JAR="$(find "$SOURCE/.minecraft/mods" -maxdepth 1 -type f \
        -name 'ringworld-*.jar' ! -name 'ringworld-neoforge-*.jar' -print -quit)"
else
    RINGWORLD_JAR="$(find "$SOURCE/.minecraft/mods" -maxdepth 1 -type f \
        -name 'ringworld-neoforge-*.jar' -print -quit)"
fi
if [[ -z "$RINGWORLD_JAR" ]]; then
    echo "The RingWorld bundle is incomplete. Download a fresh package."
    read -r -p "Press Return to close..."
    exit 1
fi
cp -f "$RINGWORLD_JAR" "$MODS/"
find "$MODS" -maxdepth 1 -type f -name 'ringworld-*.jar' \
    ! -name "$(basename "$RINGWORLD_JAR")" -delete
if [[ "$LOADER" == "fabric" ]]; then
    FABRIC_API_JAR="$(find "$SOURCE/.minecraft/mods" -maxdepth 1 -type f \
        -name 'fabric-api-*.jar' -print -quit)"
    if [[ -z "$FABRIC_API_JAR" ]]; then
        echo "The Fabric RingWorld bundle is incomplete. Download a fresh package."
        read -r -p "Press Return to close..."
        exit 1
    fi
    cp -f "$FABRIC_API_JAR" "$MODS/"
    find "$MODS" -maxdepth 1 -type f -name 'fabric-api-*.jar' \
        ! -name "$(basename "$FABRIC_API_JAR")" -delete
elif [[ "$FRESH_INSTANCE" == true ]]; then
    # A different-loader bundle may have been extracted over the same outer
    # directory. Remove that stale packaged dependency only from a newly
    # created NeoForge instance; never manage user mods on later launches.
    find "$MODS" -maxdepth 1 -type f -name 'fabric-api-*.jar' -delete
fi
cp -f "$SOURCE/mmc-pack.json" "$INSTANCE/mmc-pack.json"
if [[ "$LOADER" == "neoforge" ]]; then
    if [[ -f "$SOURCE/ringworld-managed-neoforge-patch.txt" ]]; then
        mkdir -p "$INSTANCE/patches"
        cp -f "$SOURCE/patches/net.neoforged.json" "$INSTANCE/patches/net.neoforged.json"
        cp -f "$SOURCE/ringworld-managed-neoforge-patch.txt" "$INSTANCE/"
    elif [[ -f "$INSTANCE/ringworld-managed-neoforge-patch.txt" ]]; then
        # Retire only our own loader patch when returning to official metadata.
        rm -f "$INSTANCE/patches/net.neoforged.json" "$INSTANCE/ringworld-managed-neoforge-patch.txt"
    fi
fi
if [[ ! -f "$INSTANCE/.minecraft/config/ringworld.properties" ]]; then
    cp "$SOURCE/.minecraft/config/ringworld.properties" \
        "$INSTANCE/.minecraft/config/ringworld.properties"
fi
set_instance_setting() {
    setting="$1"
    key="${setting%%=*}"
    value="${setting#*=}"
    temporary="$INSTANCE/instance.cfg.ringworld.tmp"
    awk -v key="$key" -v value="$value" '
        BEGIN { found = 0 }
        index($0, key "=") == 1 { print key "=" value; found = 1; next }
        { print }
        END { if (!found) print key "=" value }
    ' "$INSTANCE/instance.cfg" > "$temporary"
    mv "$temporary" "$INSTANCE/instance.cfg"
}

# Minecraft 26.1.2 requires Java 25. Prefer an existing valid Java 25 runtime
# so a fresh portable Prism data tree does not stall at first-run Java
# selection. Fall back to Prism's automatic Java management when none is
# installed in a standard macOS, Homebrew, SDK, or prior-instance location.
CONFIGURED_JAVA="$(awk -F= '$1 == "JavaPath" { sub(/^JavaPath=/, ""); print; exit }' \
    "$INSTANCE/instance.cfg")"
JAVA_HOME_25="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
JAVA25=""
for candidate in \
    "$CONFIGURED_JAVA" \
    "$(command -v java 2>/dev/null || true)" \
    "$JAVA_HOME_25/bin/java" \
    "$HOME"/.local/jdks/*/Contents/Home/bin/java \
    /Library/Java/JavaVirtualMachines/*/Contents/Home/bin/java \
    /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home/bin/java; do
    if [[ -x "$candidate" ]] && "$candidate" -version 2>&1 | head -n 1 | \
            grep -Eq '(java|openjdk) version "25([."]|$)'; then
        JAVA25="$candidate"
        break
    fi
done

if [[ -n "$JAVA25" ]]; then
    set_instance_setting "JavaPath=$JAVA25"
    set_instance_setting "AutomaticJava=false"
    set_instance_setting "OverrideJavaLocation=true"
    echo "Using detected Java 25 runtime."
else
    set_instance_setting "AutomaticJava=true"
    set_instance_setting "OverrideJavaLocation=false"
    echo "Java 25 was not found locally; Prism will install or select it."
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

exec "$PRISM" -d "$DATA" -l "$INSTANCE_ID"
