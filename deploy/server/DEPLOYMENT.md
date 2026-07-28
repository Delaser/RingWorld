# AndWhatNot RingWorld server

- Host: `andwhatnotstudio.com:25565`
- Minecraft: Java 1.21.11
- Fabric Loader: 0.19.3
- Fabric API: 0.141.4+1.21.11
- Geometry: 2,048-block circumference by 416-block width (128 by 26 chunks)
- Default mode: survival / peaceful
- Service: `ringworld.service`
- Install directory: `/opt/ringworld-server`

The public server was rebuilt on 27 July 2026 with the validated safe-small
layout. The retired 1,600-by-320 save is retained only as a rollback backup
under `/opt/ringworld-server/backups/cutover-20260727T055423Z/`; it must not be
resized in place. Future geometry changes still require an explicit backup and
a fresh world because saved dimensions are immutable.

The matching public client archives and checksums are served from:

```text
https://andwhatnotstudio.com/ringworld/
```

RingWorld is proprietary. The installed mod jar must declare
`LicenseRef-RingWorld-Evaluation-1.0` and contain
`LICENSE-RINGWORLD.txt`. The MIT-labelled server jar was withdrawn on
28 July 2026 and retained only at
`/opt/ringworld-server/backups/license-correction-20260728T123000Z/`.
Do not restore or redistribute it.

Meridian is a separate `meridian.service`. It is world-agnostic: its managed
`meridian-proximity` datapack is restored into the active world's `datapacks/`
directory when needed, then it reloads the pack and recreates its five internal
scoreboard objectives. It also retries that repair when it attaches to a new
Minecraft log or observes an unreadable proximity result. The service needs
write access only to that datapack directory; do not grant it general world or
server-config write access. Its managed source is
`/opt/meridian/managed-datapacks/meridian-proximity`.

The format-4 black-atlas incident and preceding jar/atlas are retained under
`/opt/ringworld-server/backups/atlas-color-fix-20260727T064758Z/`. Format 5
forces a clean atlas rebuild and supplies dedicated-server-safe grass and
foliage colour fallback.

Administrative commands:

```sh
systemctl status ringworld
journalctl -u ringworld -f
systemctl restart ringworld
systemctl stop ringworld
```

In-game or console atlas commands:

```text
/ringworld atlas status
/ringworld atlas pause
/ringworld atlas resume
```
