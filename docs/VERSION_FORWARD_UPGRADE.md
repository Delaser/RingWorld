# Forward world-upgrade qualification

`scripts/run_world_upgrade_qualification.py` validates one copied RingWorld
save from an exact source cell to a numerically later exact target cell on the
same loader. It is a qualification mechanism, not a runtime-support or
release claim.

By default, source and target cells are selected from `--manifest`. For a new
candidate group, pass its reviewed target manifest with `--manifest` and the
older reviewed source candidate-group file with `--source-manifest`:

```sh
python3 scripts/run_world_upgrade_qualification.py \
  --source-manifest config/minecraft-version-matrix.json \
  --source-cell 26.1-fabric \
  --source-worldgen-run-id <passed-source-worldgen-run> \
  --manifest config/minecraft-version-matrix-26.2.json \
  --target-cell 26.2-fabric \
  --target-quick-run-id <passed-target-quick-run>
```

Both manifests are independently parsed as complete candidate groups. The
source worldgen terminal (including its frozen-candidate hash) and copied
source world remain independent inputs; the target quick evidence and frozen
candidate are revalidated separately. The terminal records both candidate
hashes as distinct provenance.
Only stable numeric forward ordering is accepted. Same-version and reverse
paths are rejected, the source world is never modified, and a passing report
does not broaden advertised compatibility.

The eight reviewed 26.1.x patch and 26.1.2-to-26.2 routes now pass; exact
run IDs are in the [current checkpoint](QUALIFICATION_26_2_CHECKPOINT_2026-08-27.md).
Source validation uses the independently selected source manifest, never a
lookup in the target manifest's cell map.

RingWorld settings and the fixture's saved-worldgen counts, structures and loot
remain strict. Its biome/family lists instead sample the current generator's
`BiomeSource`; they are not persisted chunk-biome palettes. Same-stable-line
upgrades keep exact equality, while cross-stable-line upgrades record explicit
before/after/add/remove sample deltas. For 26.1.2-to-26.2, the observed delta is
the addition of `minecraft:sulfur_caves` with unchanged family sets. Empty or
malformed sample lists still fail. This is not a complete block, biome-palette
or Atlas inventory audit, and passing it does not authorize publication.
