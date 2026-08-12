#!/usr/bin/env bash
# Creates only the ignored disposable world used by the two-phase seam-raid gate.
set -euo pipefail

usage() {
  echo "usage: $0 [--qualification-cell-root PATH] fabric|neoforge" >&2
  exit 64
}

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
qualification_cell_root="${RINGWORLD_QUALIFICATION_CELL_ROOT:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --qualification-cell-root)
      [[ $# -ge 2 ]] || usage
      if [[ -n "$qualification_cell_root" && "$qualification_cell_root" != "$2" ]]; then
        echo "RINGWORLD_QUALIFICATION_CELL_ROOT and --qualification-cell-root disagree" >&2
        exit 64
      fi
      qualification_cell_root="$2"
      shift 2
      ;;
    --help|-h)
      usage
      ;;
    --*)
      usage
      ;;
    *)
      break
      ;;
  esac
done

loader="${1:-}"
[[ $# -eq 1 ]] || usage
case "$loader" in
  fabric) root="run-raid-seam" ;;
  neoforge) root="neoforge/run-raid-seam" ;;
  *) usage ;;
esac

# A qualification cell is intentionally below the same reviewed root that
# Gradle accepts. Resolve before creating anything so a symlink or traversal
# cannot turn a disposable fixture invocation into a live-world deletion.
if [[ -n "$qualification_cell_root" ]]; then
  root="$(python3 - "$repository_root" "$qualification_cell_root" <<'PY'
import os
from pathlib import Path
import re
import sys

repository = Path(sys.argv[1]).resolve()
requested = Path(sys.argv[2])
if ".." in requested.parts:
    raise SystemExit("qualification cell root must not contain path traversal")
candidate_input = repository / requested if not requested.is_absolute() else requested
candidate = Path(os.path.abspath(candidate_input))
allowed = repository / "dist" / "qualification"
try:
    candidate.relative_to(allowed)
except ValueError:
    raise SystemExit("qualification cell root must be below " + str(allowed))
if candidate == allowed or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}", candidate.name):
    raise SystemExit(
        "qualification cell root must have one safe cell identifier below " + str(allowed)
    )

fixture = candidate / "run" / "run-raid-seam"
managed_paths = [
    fixture,
    fixture / "server" / "eula.txt",
    fixture / "server" / "world",
    fixture / "server" / "logs",
    fixture / "server" / "ringworld-cache",
    fixture / "server" / "crash-reports",
    fixture / "server" / "config" / "ringworld.properties",
    fixture / "server" / "server.properties",
]
for client in ("client-a", "client-b"):
    managed_paths.extend(
        [
            fixture / client / "logs",
            fixture / client / "screenshots",
            fixture / client / "ringworld-cache",
            fixture / client / "crash-reports",
            fixture / client / "config" / "fml.toml",
        ]
    )

for managed in managed_paths:
    current = repository
    for component in managed.relative_to(repository).parts:
        current = current / component
        if current.is_symlink():
            raise SystemExit(
                "qualification fixture path contains a symlink: " + str(current)
            )

print(fixture)
PY
)" || exit $?
fi

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
