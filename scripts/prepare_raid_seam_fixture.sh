#!/usr/bin/env bash
# Creates only the ignored disposable world used by the two-phase seam-raid gate.
set -euo pipefail

loader="${1:-}"
case "$loader" in
  fabric) root="run-raid-seam" ;;
  neoforge) root="neoforge/run-raid-seam" ;;
  *) echo "usage: $0 fabric|neoforge" >&2; exit 64 ;;
esac

server="$root/server"
eula="$server/eula.txt"
mkdir -p "$server"
if [[ ! -f "$eula" ]]; then
  printf '# Review https://aka.ms/MinecraftEULA before changing this value.\neula=false\n' > "$eula"
fi
if ! grep -qx 'eula=true' "$eula"; then
  echo "Set eula=true in $eula after reviewing Mojang's EULA." >&2
  exit 1
fi

rm -rf "$server/world" "$server/logs" "$server/ringworld-cache" "$server/crash-reports" \
  "$root/client-a/logs" "$root/client-a/screenshots" "$root/client-a/ringworld-cache" "$root/client-a/crash-reports" \
  "$root/client-b/logs" "$root/client-b/screenshots" "$root/client-b/ringworld-cache" "$root/client-b/crash-reports"
mkdir -p "$server/config" "$root/client-a" "$root/client-b"

cat > "$server/config/ringworld.properties" <<'EOF'
widthBlocks=416
circumferenceBlocks=2048
wallHeightBlocks=160
testMode=false
testViewDistanceChunks=2
pregenerateTerrainAtlas=false
EOF

cat > "$server/server.properties" <<'EOF'
server-port=25567
online-mode=false
gamemode=creative
difficulty=normal
level-name=world
level-seed=ringworld-raid-seam
view-distance=5
simulation-distance=5
motd=RingWorld disposable seam-raid fixture
EOF

if [[ "$loader" == "neoforge" ]]; then
  for client in client-a client-b; do
    mkdir -p "$root/$client/config"
    cat > "$root/$client/config/fml.toml" <<'EOF'
# The automated clients launch without NeoForge's separate early splash window.
# Minecraft's normal game window still opens and performs the graphical test.
earlyWindowControl = false
EOF
  done
fi

echo "Prepared $loader seam-raid fixture at $root."
