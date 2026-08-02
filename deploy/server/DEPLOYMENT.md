# RingWorld dedicated server template

The checked-in files are generic deployment examples for Minecraft 26.1.2
Fabric and NeoForge servers. They do not describe or manage any particular
hosted service. Package assembly selects the loader-specific deployment and
systemd templates; this source file is only the shared reference.

Current development versions:

- Minecraft Java 26.1.2
- Fabric Loader 0.19.3
- Fabric API 0.155.2+26.1.2
- RingWorld 0.2.0+mc26.1.2
- Java 25

Copy `server.properties.example` to `server.properties` only in the installed
server directory. Keep the deployed file untracked because it may contain an
RCON password or other local operational values.

Copy `config/ringworld.properties` before the first Overworld load. The saved
geometry becomes authoritative after world creation and cannot be resized in
place. The production defaults are a 16,384-block circumference and a
256-block width; smaller validated layouts are useful for development.

The supplied `ringworld.service` is an example systemd unit. Adjust its user,
group, paths, memory limits, and service name for the target host.

Typical service operations after installation:

```sh
systemctl status ringworld
journalctl -u ringworld -f
systemctl restart ringworld
systemctl stop ringworld
```

Before replacing the mod jar:

1. stop the server cleanly;
2. back up the world, configuration, and existing jar;
3. install the client-identical RingWorld jar and matching Fabric API;
4. start the server and inspect mixin, protocol, and atlas messages;
5. connect two matching clients;
6. validate geometry acknowledgement and atlas recovery;
7. test seam movement, interaction, vehicles, combat, and reconnect;
8. retain a tested rollback until the new build is accepted.

Every distributed RingWorld jar must declare `MPL-2.0`, embed
`LICENSE-RINGWORLD.txt`, and identify the corresponding public source
revision. Run `scripts/verify_distribution_license.py` against generated
packages before distribution.

## Optional server overlay

`scripts/prepare_release_packages.py` creates a reproducible
loader-labelled `Server-Overlay.zip` containing the verified RingWorld jar and,
only for Fabric, its required Fabric API jar. It selects matching loader
deployment and systemd templates, records the loader in its manifest, and
deliberately excludes Minecraft server binaries, worlds, player data, logs,
credentials, and live configuration. Treat it as a staging input, not a
deployment: obtain the matching loader runtime from official sources and
perform isolated Java 25 launch tests before any owner-approved release. The
overlay ships with `eula=false`; each server operator must read Mojang's EULA
and record their own acceptance before the first real start.

Atlas administration commands are:

```text
/ringworld atlas status
/ringworld atlas pause
/ringworld atlas resume
```

For offline preparation, use a separately prepared disposable server directory
and add `-Dringworld.headlessPrewarm=true` only for that one launch. It rejects
accepted joins immediately, writes `world/ringworld-prewarm/progress.json` and
`result.json`, then stops after the atlas is verified and the world is saved.
Do not put this JVM flag into `ringworld.service`, and do not point it at an
authoritative source or live world. The development wrapper
`./gradlew runHeadlessPrewarmServer` checks the terminal JSON because Minecraft
may return exit code zero after a recorded prewarm failure.
