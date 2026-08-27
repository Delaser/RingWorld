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
