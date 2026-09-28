# Atlas development checkpoint — 2026-09-06

Compact runtime evidence retained in Git alongside the source and screenshot bank. Full local JFR files and build logs remain in ignored `logs/`; these files do not claim to archive those recordings remotely.

- FPS: 30 one-second counters per level on the same 16,384×256 ring at 165° village separation, Fabric 26.2, complete one-block Atlas, normal simulation. Temporary cap 260 FPS and idle-limiter override; original options restored. Ranges are counters, not 1% lows. Names are the existing six-level runtime commands.
- Pose verification: unchanged camera across all six FPS windows.
- Async refresh: three forced same-content rebuilds settled at Max with no pending job.
- Lifecycle: abandoned pending Max build, settled Low, then restored Max.

See `../../ATLAS_STUTTER_2026-09-06.md` for before/after limits and remaining stutter sources, and `../../media/village-lod-day-night/README.md` for the six original images, labelled arrays and proposed three-level name mapping. Runtime command/UI simplification and the ambiguous default-on toggle remain unfinished; screenshot labels do not imply those code changes shipped.

All four version/loader source cells passed 423 JVM tests each; Python passed 429 with two platform skips. This is an unreleased development checkpoint. Owner release hold remains until stable Minecraft 26.3.
