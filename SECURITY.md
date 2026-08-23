# Security policy

Please do not disclose a suspected security vulnerability in a public issue.
Use GitHub's **Report a vulnerability** option on the repository Security page
so maintainers can investigate before details are published.

Include the affected RingWorld version and Minecraft version, whether the
problem affects clients, dedicated servers, or both, reproduction steps, and
the smallest safe supporting log or configuration excerpt. Remove access
tokens, account files, server passwords, player personal data, and unrelated
world data before attaching anything.

The stable security-maintenance family begins at Minecraft 26.1, with
Minecraft 26.1.2 the current verified stable RingWorld release. The separate
Minecraft 1.21.1 Fabric/NeoForge `1.0.0-beta.1+mc1.21.1` backport is also an
active security-reporting target while its Beta files are available. Report
the exact Minecraft version, loader, RingWorld artifact hash, and whether the
issue reproduces without third-party mods.

Minecraft 26.1, 26.1.1, later stable versions, and other 1.21.x versions enter
supported status only after their exact dual-loader qualification passes.
Historical 1.21.11 test builds are retained as validation evidence and are not
supported releases. See
[`docs/MINECRAFT_VERSION_SUPPORT_PLAN.md`](docs/MINECRAFT_VERSION_SUPPORT_PLAN.md)
and the
[`1.21.1 backport record`](versions/mc1.21.1/README.md).
