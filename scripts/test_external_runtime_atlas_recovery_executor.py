#!/usr/bin/env python3
"""No-network fake-process tests for external Atlas-recovery assembly/evidence."""

from __future__ import annotations

from dataclasses import replace
import gzip
import hashlib
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from external_runtime_atlas_recovery_executor import (  # noqa: E402
    AtlasRecoveryExecutionError,
    AtlasRecoveryStageResult,
    _read_regular,
    execute_external_runtime_atlas_recovery,
)
from external_runtime_atlas_recovery_plan import QuickTerminalEvidenceInput, external_runtime_atlas_recovery_plan  # noqa: E402
from external_runtime_executor import ExecutedCommand  # noqa: E402
from external_runtime_smoke import CandidateJar, RuntimeDownload  # noqa: E402
from minecraft_atlas_recovery_qualification import (  # noqa: E402
    ATLAS_FORMAT_VERSION,
    ATLAS_SAMPLE_STEP_BLOCKS,
    EXPECTED_ATLAS_COLUMNS,
    EXPECTED_ATLAS_ROWS,
    EXPECTED_TOTAL_CELLS,
    EXPECTED_TOTAL_CHUNKS,
    atlas_world_hash,
    layout_fingerprint,
    TimedMarker,
)
from minecraft_frozen_candidate import FrozenCandidateInspection  # noqa: E402
from minecraft_atlas_recovery_persistence import ATLAS_MAGIC  # noqa: E402
from minecraft_qualification_model import QualificationPaths, Verdict  # noqa: E402
from test_minecraft_qualification_evidence import RANGES, canonical_cells, passing_record  # noqa: E402


def sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


EXECUTION_PROVENANCE = {
    "commit": "a" * 40,
    "branch": "codex/qualification-test",
    "upstream": "a" * 40,
    "origin": "https://github.com/Delaser/RingWorld.git",
    "manifest_sha256": "b" * 64,
    "gradle_wrapper_sha256": "c" * 64,
    "java_version": 'openjdk version "25.0.4"',
}


class Response:
    def __init__(self, url: str, body: bytes) -> None:
        self.url, self.body, self.offset = url, body, 0

    def read(self, amount: int = -1) -> bytes:
        if amount < 0:
            amount = len(self.body) - self.offset
        value = self.body[self.offset:self.offset + amount]
        self.offset += len(value)
        return value

    def geturl(self) -> str:
        return self.url

    def close(self) -> None:
        pass


def nbt_string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def settings_bytes(seed: int = 12345) -> bytes:
    entries = (("width", 3, 416), ("circumference", 3, 2048), ("seed", 4, seed),
               ("wallHeight", 3, 160), ("surfaceReferenceY", 3, 64),
               ("terrainNoiseMapping", 3, 4), ("format", 3, 3))
    data = bytearray(b"\x0a" + nbt_string(""))
    data.extend(b"\x0a" + nbt_string("data"))
    for name, kind, value in entries:
        data.append(kind)
        data.extend(nbt_string(name))
        data.extend(struct.pack(">q" if kind == 4 else ">i", value))
    data.extend(b"\x00\x00")
    return gzip.compress(bytes(data), mtime=0)


def atlas_bytes(world_hash: int, *, complete: bool, revision: int) -> bytes:
    cells = EXPECTED_ATLAS_COLUMNS * EXPECTED_ATLAS_ROWS
    payload = bytearray(struct.pack(">IIQIIIIIQ", ATLAS_MAGIC, ATLAS_FORMAT_VERSION, world_hash, 416, 2048,
                                   ATLAS_SAMPLE_STEP_BLOCKS, EXPECTED_ATLAS_COLUMNS, EXPECTED_ATLAS_ROWS, revision))
    for index in range(cells):
        present = complete or index in {0, 1, EXPECTED_ATLAS_COLUMNS, EXPECTED_ATLAS_COLUMNS + 1}
        payload.extend(bytes((1 if present else 0,)) + struct.pack(">hI", 64, 0x00AA00))
    return gzip.compress(bytes(payload), mtime=0)


class ExternalRuntimeAtlasRecoveryExecutorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        matrix = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        cls.cells = {item["id"]: item for item in matrix["cells"]}

    def make_plan(self, root: Path, cell_id: str):
        cell = self.cells[cell_id]
        paths = QualificationPaths.from_cell(root, cell, "20260812T120000Z-0123456789ab")
        frozen_root = root / "frozen" / cell["loader"]
        frozen_root.mkdir(parents=True)
        candidate_path = frozen_root / "ringworld.jar"
        candidate_path.write_bytes(b"candidate-" + cell_id.encode("ascii"))
        candidate = CandidateJar(candidate_path, sha256(candidate_path.read_bytes()), cell["loader"])
        paths.evidence_directory.mkdir(parents=True)
        quick_path = paths.evidence_directory / "strict-terminal-evidence.json"
        record = passing_record()
        loader = cell["loader"]
        expected_cell = canonical_cells()[cell_id]
        record["cell"] = {key: expected_cell[key] for key in (
            "id", "minecraft_version", "loader", "port", "world_config",
        )}
        record["frozen_candidate"]["source_sha256"] = candidate.sha256
        record["frozen_candidate"]["installed_sha256"] = candidate.sha256
        record["runtime_inventory"][0]["sha256"] = candidate.sha256
        record["same_file"] = {
            "group": f"26.1.x-{loader}", "sha256": candidate.sha256,
            "cell_ids": [f"26.1-{loader}", f"26.1.1-{loader}", f"26.1.2-{loader}"],
        }
        if loader == "neoforge":
            installer_identity = canonical_cells()[cell_id]["runtime_install"]
            record["installer"] = {
                "name": installer_identity["name"], "url": installer_identity["url"],
                "path": "cache/installer.jar",
                "sha256": "a" * 64,
                "installed_sha256": "a" * 64,
            }
            record["runtime_inventory"] = [record["runtime_inventory"][0]]
            record["frozen_candidate"]["minecraft_range"] = "[26.1,26.1.2]"
            record["frozen_candidate"]["loader_range"] = "[26.1.0.19-beta,26.1.2.87]"
        quick_path.write_text(json.dumps(record), encoding="utf-8")
        quick = QuickTerminalEvidenceInput(quick_path, sha256(quick_path.read_bytes()))
        plan = external_runtime_atlas_recovery_plan(
            cell, candidate, paths, quick, frozen_candidate_root=frozen_root,
        )
        server = b"mojang-" + cell_id.encode("ascii")
        installer = b"installer-" + cell_id.encode("ascii")
        downloads = []
        mods = list(plan.smoke.mods)
        bodies: dict[str, bytes] = {}
        changed_server = replace(plan.smoke.minecraft_server, url="https://test.invalid/server.jar",
                                 checksum=hashlib.sha1(server).hexdigest())
        bodies[changed_server.url] = server
        for index, entry in enumerate(plan.smoke.downloads):
            body = installer if index == 0 else b"fabric-api-" + cell_id.encode("ascii")
            changed = RuntimeDownload(entry.name, f"https://test.invalid/{index}.jar", "sha256", sha256(body), entry.destination)
            downloads.append(changed)
            bodies[changed.url] = body
            if index > 0:
                mods[1] = replace(mods[1], source=changed.destination, sha256=changed.checksum)
        plan = replace(plan, smoke=replace(plan.smoke, minecraft_server=changed_server, downloads=tuple(downloads), mods=tuple(mods)))
        return paths, plan, bodies

    @staticmethod
    def opener(bodies):
        return lambda url, *, timeout: Response(url, bodies[url])

    @staticmethod
    def installer(plan):
        def run(record, paths, *, ordinal: int):
            assert ordinal == 1
            assert (plan.runtime_root / "server.jar").read_bytes() == b"mojang-" + plan.smoke.cell_id.encode("ascii")
            if plan.smoke.loader == "fabric":
                assert plan.smoke.layout.fabric_server_jar is not None
                plan.smoke.layout.fabric_server_jar.write_bytes(b"launcher")
            else:
                assert plan.smoke.layout.neoforge_run_script is not None
                assert plan.smoke.layout.neoforge_user_jvm_args is not None
                plan.smoke.layout.neoforge_run_script.write_text("#!/bin/sh\n", encoding="utf-8")
                plan.smoke.layout.neoforge_run_script.chmod(0o700)
                plan.smoke.layout.neoforge_user_jvm_args.write_text("-Xmx1G\n", encoding="utf-8")
                installed_server = (
                    plan.runtime_root / "libraries" / "net" / "minecraft" / "server"
                    / plan.smoke.minecraft_version
                )
                installed_server.mkdir(parents=True)
                (installed_server / f"server-{plan.smoke.minecraft_version}.jar").write_bytes(
                    b"mojang-" + plan.smoke.cell_id.encode("ascii")
                )
            return ExecutedCommand("DEDICATED_SMOKE", Verdict.PASS, record.argv, 0, "now", 0.0, "", "")
        return run

    @staticmethod
    def stage_runner(plan):
        settings = settings_bytes()
        from minecraft_atlas_recovery_persistence import parse_persisted_ring_settings
        observed = parse_persisted_ring_settings(settings, plan.settings_path)
        world_hash, layout = int(atlas_world_hash(observed)), layout_fingerprint(observed)

        def run(stage, whole, paths):
            del whole
            stage.runtime_report_path.parent.mkdir(parents=True, exist_ok=True)
            plan.settings_path.parent.mkdir(parents=True, exist_ok=True)
            plan.settings_path.write_bytes(settings)
            if stage.name == "interrupted":
                raw_atlas, chunks, cells, revision = atlas_bytes(world_hash, complete=False, revision=1), 1, 4, 1
                markers = ("atlas-started", "atlas-interrupted")
            else:
                raw_atlas, chunks, cells, revision = atlas_bytes(world_hash, complete=True, revision=2), EXPECTED_TOTAL_CHUNKS, EXPECTED_TOTAL_CELLS, 2
                markers = ("atlas-restarted", "atlas-recovered", "atlas-complete", "fixture-pass")
            plan.atlas_path.write_bytes(raw_atlas)
            report = {
                "schemaVersion": 2, "status": stage.expected_status, "identityAvailable": True,
                "worldHash": str(world_hash), "layoutFingerprint": layout, "terrainNoiseMapping": 4,
                "circumferenceBlocks": 2048, "widthBlocks": 416, "completedChunks": chunks,
                "totalChunks": EXPECTED_TOTAL_CHUNKS, "completedCells": cells,
                "totalCells": EXPECTED_TOTAL_CELLS, "elapsedMillis": revision,
                "atlasPath": "world/dimensions/minecraft/overworld/data/ringworld/terrain-atlas.rwat.gz",
                "failureReason": "controlled interruption" if stage.name == "interrupted" else None,
            }
            stage.runtime_report_path.write_text(json.dumps(report), encoding="utf-8")
            base = 100 if stage.name == "interrupted" else 200
            events = tuple(TimedMarker(name, base + index) for index, name in enumerate(markers))
            log = paths.logs_directory / f"fake-{stage.name}.log"
            log.parent.mkdir(parents=True, exist_ok=True)
            log.write_text("fake stage log\n", encoding="utf-8")
            return AtlasRecoveryStageResult(0, events, stage.name == "interrupted", stage.name == "recovery", str(log))
        return run

    @staticmethod
    def candidate_inspector(plan):
        def inspect(path, loader):
            return FrozenCandidateInspection(
                str(path), loader, plan.smoke.candidate.sha256,
                "0.0.0-qualification+mc26.1", f"qualification-26.1-{loader}",
                ">=26.1 <=26.1.2" if loader == "fabric" else "[26.1,26.1.2]",
                None if loader == "fabric" else "[26.1.0.19-beta,26.1.2.87]",
                ("26.1", "26.1.1", "26.1.2"),
            )
        return inspect

    def test_fabric_and_neoforge_capture_a_real_parsed_two_stage_contract_under_fakes(self) -> None:
        for cell_id in ("26.1-fabric", "26.1-neoforge"):
            with self.subTest(cell_id=cell_id), tempfile.TemporaryDirectory() as directory:
                paths, plan, bodies = self.make_plan(Path(directory), cell_id)
                result = execute_external_runtime_atlas_recovery(
                    plan, paths, paths.run_id,
                    canonical_cells=canonical_cells(), range_identities=RANGES,
                    opener=self.opener(bodies), command_executor=self.installer(plan), stage_runner=self.stage_runner(plan),
                    candidate_inspector=self.candidate_inspector(plan), execution_source_provenance=EXECUTION_PROVENANCE,
                )
                self.assertEqual(Verdict.PASS, result.verdict)
                self.assertTrue((plan.evidence_root / "terminal.json").is_file())
                self.assertTrue((plan.evidence_root / "settings.dat").is_file())
                self.assertEqual((plan.evidence_root / "interrupted-atlas.rwat.gz").read_bytes(),
                                 (plan.evidence_root / "recovery-input-atlas.rwat.gz").read_bytes())
                terminal = json.loads((plan.evidence_root / "terminal.json").read_text(encoding="utf-8"))
                self.assertEqual(plan.smoke.candidate.sha256, terminal["qualification"]["frozenCandidateSha256"])
                self.assertEqual(plan.quick_terminal_evidence.sha256,
                                 terminal["qualification"]["quickTerminalEvidenceSha256"])
                self.assertIn("completeAtlas", terminal["qualification"]["captures"])
                self.assertEqual(EXECUTION_PROVENANCE, terminal["qualification"]["executionSourceProvenance"])
                if cell_id.endswith("neoforge"):
                    assert plan.smoke.layout.neoforge_user_jvm_args is not None
                    args = plan.smoke.layout.neoforge_user_jvm_args.read_text(encoding="utf-8")
                    self.assertIn("-Dringworld.headlessPrewarm=true", args)
                    self.assertIn("-Dringworld.headlessPrewarmReport=result.json", args)

    def test_default_runner_cannot_claim_a_runtime_pass(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, bodies = self.make_plan(Path(directory), "26.1-fabric")
            with self.assertRaises(AtlasRecoveryExecutionError):
                execute_external_runtime_atlas_recovery(
                    plan, paths, paths.run_id,
                    canonical_cells=canonical_cells(), range_identities=RANGES,
                    opener=self.opener(bodies), command_executor=self.installer(plan),
                    candidate_inspector=self.candidate_inspector(plan), execution_source_provenance=EXECUTION_PROVENANCE,
                )

    def test_rejects_report_atlas_path_or_recovery_byte_change(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, bodies = self.make_plan(Path(directory), "26.1-fabric")
            runner = self.stage_runner(plan)
            def wrong(stage, whole, received_paths):
                result = runner(stage, whole, received_paths)
                if stage.name == "interrupted":
                    data = json.loads(stage.runtime_report_path.read_text(encoding="utf-8"))
                    data["atlasPath"] = "outside.rwat.gz"
                    stage.runtime_report_path.write_text(json.dumps(data), encoding="utf-8")
                return result
            with self.assertRaises(AtlasRecoveryExecutionError):
                execute_external_runtime_atlas_recovery(
                    plan, paths, paths.run_id,
                    canonical_cells=canonical_cells(), range_identities=RANGES,
                    opener=self.opener(bodies), command_executor=self.installer(plan), stage_runner=wrong,
                    candidate_inspector=self.candidate_inspector(plan), execution_source_provenance=EXECUTION_PROVENANCE,
                )

    def test_rejects_semantically_wrong_quick_record_before_download(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, bodies = self.make_plan(Path(directory), "26.1-fabric")
            record = json.loads(plan.quick_terminal_evidence.path.read_text(encoding="utf-8"))
            record["cell"]["minecraft_version"] = "26.1.2"
            raw = json.dumps(record).encode("utf-8")
            plan.quick_terminal_evidence.path.write_bytes(raw)
            plan = replace(plan, quick_terminal_evidence=replace(
                plan.quick_terminal_evidence, sha256=sha256(raw),
            ))
            downloads: list[str] = []
            with self.assertRaises(AtlasRecoveryExecutionError):
                execute_external_runtime_atlas_recovery(
                    plan, paths, paths.run_id,
                    canonical_cells=canonical_cells(), range_identities=RANGES,
                    opener=lambda url, **kwargs: downloads.append(url),  # type: ignore[arg-type]
                    command_executor=self.installer(plan), stage_runner=self.stage_runner(plan),
                    candidate_inspector=self.candidate_inspector(plan), execution_source_provenance=EXECUTION_PROVENANCE,
                )
            self.assertEqual([], downloads)

    def test_regular_reader_rejects_intermediate_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as outside:
            root = Path(directory)
            external = Path(outside) / "data"
            external.mkdir()
            (external / "settings.dat").write_bytes(b"not allowed")
            (root / "dimensions").symlink_to(external, target_is_directory=True)
            with self.assertRaises(AtlasRecoveryExecutionError):
                _read_regular(root / "dimensions/settings.dat", root, "settings")


if __name__ == "__main__":
    unittest.main()
