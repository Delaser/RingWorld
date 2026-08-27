#!/usr/bin/env python3
"""No-network integration tests for the external worldgen executor."""

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
if str(ROOT / "scripts") not in sys.path:
    sys.path.insert(0, str(ROOT / "scripts"))

from external_runtime_atlas_recovery_plan import QuickTerminalEvidenceInput  # noqa: E402
from external_runtime_executor import ExecutedCommand  # noqa: E402
from external_runtime_smoke import CandidateJar, RuntimeDownload  # noqa: E402
from external_runtime_worldgen_executor import execute_external_runtime_worldgen  # noqa: E402
from external_runtime_worldgen_executor import (  # noqa: E402
    ForwardUpgradeSource, execute_external_runtime_forward_upgrade,
)
from external_runtime_worldgen_plan import (  # noqa: E402
    external_runtime_worldgen_plan, external_runtime_worldgen_resume_stage,
)
from external_runtime_worldgen_stage_runner import (  # noqa: E402
    ExternalRuntimeWorldgenStageError, ExternalRuntimeWorldgenStageObservation,
    WorldgenStageMarkerEvent,
)
from minecraft_frozen_candidate import FrozenCandidateInspection  # noqa: E402
from minecraft_qualification_model import QualificationPaths, Verdict  # noqa: E402
from test_minecraft_qualification_evidence import RANGES, canonical_cells, passing_record  # noqa: E402


PROVENANCE = {
    "commit": "a" * 40, "branch": "codex/test", "upstream": "a" * 40,
    "origin": "https://github.com/Delaser/RingWorld.git",
    "manifest_sha256": "b" * 64, "gradle_wrapper_sha256": "c" * 64,
    "java_version": 'openjdk version "25.0.1"',
}


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def nbt_string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def settings_bytes(width: int, circumference: int, seed: int) -> bytes:
    entries = (("width", 3, width), ("circumference", 3, circumference), ("seed", 4, seed),
               ("wallHeight", 3, 160), ("surfaceReferenceY", 3, 64),
               ("terrainNoiseMapping", 3, 4), ("format", 3, 3))
    data = bytearray(b"\x0a" + nbt_string("") + b"\x0a" + nbt_string("data"))
    for name, kind, value in entries:
        data.append(kind)
        data.extend(nbt_string(name))
        data.extend(struct.pack(">q" if kind == 4 else ">i", value))
    data.extend(b"\x00\x00")
    return gzip.compress(bytes(data), mtime=0)


class Response:
    def __init__(self, url: str, body: bytes) -> None:
        self.url, self.body, self.offset = url, body, 0

    def read(self, amount: int = -1) -> bytes:
        amount = len(self.body) - self.offset if amount < 0 else amount
        value = self.body[self.offset:self.offset + amount]
        self.offset += len(value)
        return value

    def geturl(self) -> str:
        return self.url

    def close(self) -> None:
        pass


class ExternalRuntimeWorldgenExecutorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        matrix = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        cls.cells = {cell["id"]: cell for cell in matrix["cells"]}

    def make_plan(self, root: Path, cell_id: str):
        cell = self.cells[cell_id]
        paths = QualificationPaths.from_cell(root, cell, "20260813T120000Z-0123456789ab")
        frozen = root / "frozen" / cell["loader"]
        frozen.mkdir(parents=True)
        candidate_path = frozen / "ringworld.jar"
        candidate_path.write_bytes(("candidate-" + cell_id).encode())
        candidate = CandidateJar(candidate_path, sha256(candidate_path.read_bytes()), cell["loader"])
        paths.evidence_directory.mkdir(parents=True)
        quick_path = paths.evidence_directory / "strict-terminal-evidence.json"
        record = passing_record()
        expected = canonical_cells()[cell_id]
        record["cell"] = {key: expected[key] for key in ("id", "minecraft_version", "loader", "port", "world_config")}
        record["frozen_candidate"]["source_sha256"] = candidate.sha256
        record["frozen_candidate"]["installed_sha256"] = candidate.sha256
        record["runtime_inventory"][0]["sha256"] = candidate.sha256
        record["same_file"] = {
            "group": f"26.1.x-{cell['loader']}", "sha256": candidate.sha256,
            "cell_ids": [f"26.1-{cell['loader']}", f"26.1.1-{cell['loader']}", f"26.1.2-{cell['loader']}"],
        }
        if cell["loader"] == "neoforge":
            install = canonical_cells()[cell_id]["runtime_install"]
            record["installer"] = {"name": install["name"], "url": install["url"], "path": "cache/installer.jar", "sha256": "a" * 64, "installed_sha256": "a" * 64}
            record["runtime_inventory"] = [record["runtime_inventory"][0]]
            record["frozen_candidate"]["minecraft_range"] = "[26.1,26.1.2]"
            record["frozen_candidate"]["loader_range"] = "[26.1.0.19-beta,26.1.2.87]"
        quick_path.write_text(json.dumps(record), encoding="utf-8")
        quick = QuickTerminalEvidenceInput(quick_path, sha256(quick_path.read_bytes()))
        plan = external_runtime_worldgen_plan(cell, candidate, paths, quick, frozen_candidate_root=frozen)

        server = ("server-" + cell_id).encode()
        installer = ("installer-" + cell_id).encode()
        bodies: dict[str, bytes] = {}
        changed_stages = []
        changed_by_root = {}
        for stage in plan.stages:
            smoke = changed_by_root.get(stage.runtime_root)
            if smoke is None:
                server_download = replace(stage.smoke.minecraft_server, url="https://test.invalid/server.jar", checksum=hashlib.sha1(server).hexdigest())
                bodies[server_download.url] = server
                downloads = []
                mods = list(stage.smoke.mods)
                for index, entry in enumerate(stage.smoke.downloads):
                    body = installer if index == 0 else ("api-" + cell_id).encode()
                    changed = RuntimeDownload(entry.name, f"https://test.invalid/{index}.jar", "sha256", sha256(body), entry.destination)
                    downloads.append(changed)
                    bodies[changed.url] = body
                    if index:
                        mods[1] = replace(mods[1], source=changed.destination, sha256=changed.checksum)
                smoke = replace(stage.smoke, minecraft_server=server_download, downloads=tuple(downloads), mods=tuple(mods))
                changed_by_root[stage.runtime_root] = smoke
            changed_stages.append(replace(stage, smoke=smoke))
        return paths, replace(plan, stages=tuple(changed_stages)), bodies, server

    @staticmethod
    def inspector(plan):
        def inspect(path, loader):
            return FrozenCandidateInspection(
                str(path), loader, plan.candidate.sha256, "0.0.0-qualification+mc26.1",
                "qualification", ">=26.1 <=26.1.2" if loader == "fabric" else "[26.1,26.1.2]",
                None if loader == "fabric" else "[26.1.0.19-beta,26.1.2.87]",
                ("26.1", "26.1.1", "26.1.2"),
            )
        return inspect

    @staticmethod
    def installer(plan, server):
        by_root = {stage.runtime_root: stage.smoke for stage in plan.stages}

        def execute(record, paths, *, ordinal):
            smoke = next(value for value in by_root.values() if str(value.layout.root) in record.argv)
            if smoke.loader == "fabric":
                smoke.layout.fabric_server_jar.write_bytes(b"launcher")  # type: ignore[union-attr]
            else:
                smoke.layout.neoforge_run_script.write_text("#!/bin/sh\n", encoding="utf-8")  # type: ignore[union-attr]
                smoke.layout.neoforge_run_script.chmod(0o700)  # type: ignore[union-attr]
                smoke.layout.neoforge_user_jvm_args.write_text("-Xmx1G\n", encoding="utf-8")  # type: ignore[union-attr]
                installed_server = (
                    smoke.layout.root / "libraries" / "net" / "minecraft" / "server"
                    / smoke.minecraft_version
                )
                installed_server.mkdir(parents=True)
                (installed_server / f"server-{smoke.minecraft_version}.jar").write_bytes(server)
            (smoke.layout.root / "server.jar").write_bytes(server)
            return ExecutedCommand("DEDICATED_SMOKE", Verdict.PASS, record.argv, 0, "now", 0.0, "", "")
        return execute

    @staticmethod
    def stage_runner(plan):
        stage_by_name = {stage.name: stage for stage in plan.stages}
        families = {
            "production-fresh": "badlands, beach, cave, desert, forest",
            "production-resume": "badlands, beach, cave, desert, forest",
            "seam-crossing": "jungle, mountain, ocean, plains, river, savanna",
            "terminal-policy": "snowy, swamp, taiga",
        }
        numeric = {"production-fresh": 11, "production-resume": 11, "seam-crossing": 22, "terminal-policy": 33}

        def run(process_plan, *, cell_root, logs_directory):
            stage = stage_by_name[process_plan.name]
            stage.world_root.mkdir(parents=True, exist_ok=True)
            settings = stage.world_root / "dimensions/minecraft/overworld/data/ringworld/settings.dat"
            settings.parent.mkdir(parents=True, exist_ok=True)
            settings.write_bytes(settings_bytes(stage.width_blocks, stage.circumference_blocks, numeric[stage.name]))
            crossing = 1 if stage.name == "seam-crossing" else 0
            status = "UNSATISFIED" if stage.name == "terminal-policy" else "SATISFIED"
            spawn = 1 if stage.name == "seam-crossing" else 0
            matrix = (
                f"[worldgen-matrix] seed={numeric[stage.name]} layout={stage.circumference_blocks}x{stage.width_blocks} "
                f"biomeFamilies=[{families[stage.name]}] biomeIds=[minecraft:plains] chunks=10 caveAir=1 ores=1 "
                f"logs=1 starts=1 structureIds=[minecraft:village] crossingStarts={crossing} "
                f"crossingStructureIds=[{'minecraft:village' if crossing else ''}] references=1 lootContainers=1 "
                f"structuresWithSpawnOverrides={spawn} spawnOverrideStructureIds=[{'minecraft:ocean_monument' if spawn else ''}]\n"
            )
            monument = f"[worldgen-matrix] monumentStatus={status} monumentReason=reason monumentCandidate=0,0 spawnOverrideEntries={spawn}\n"
            raw = ("Done (0.1s)!\n" + matrix + monument + "[stronghold-test] PASS\n").encode()
            log = logs_directory / f"fake-{stage.name}.log"
            log.parent.mkdir(parents=True, exist_ok=True)
            log.write_bytes(raw)
            events = tuple(WorldgenStageMarkerEvent(name, 100 + index) for index, name in enumerate(("server-ready", "worldgen-record", "monument-record", "fixture-pass")))
            return ExternalRuntimeWorldgenStageObservation(stage.name, process_plan.argv, str(stage.runtime_root), 0, 1, 2, 1, str(log), sha256(raw), events, True)
        return run

    def test_fabric_and_neoforge_complete_four_stage_contract_under_fakes(self):
        for cell_id in ("26.1-fabric", "26.1-neoforge"):
            with self.subTest(cell=cell_id), tempfile.TemporaryDirectory() as directory:
                paths, plan, bodies, server = self.make_plan(Path(directory), cell_id)
                result = execute_external_runtime_worldgen(
                    plan, paths, paths.run_id, canonical_cells=canonical_cells(), range_identities=RANGES,
                    opener=lambda url, *, timeout: Response(url, bodies[url]),
                    command_executor=self.installer(plan, server), stage_runner=self.stage_runner(plan),
                    candidate_inspector=self.inspector(plan), execution_source_provenance=PROVENANCE,
                )
                self.assertEqual(Verdict.PASS, result.verdict, result.reason)
                self.assertEqual(3, len(result.assemblies))
                terminal = json.loads((plan.evidence_root / "terminal.json").read_text(encoding="utf-8"))
                self.assertEqual(plan.candidate.sha256, terminal["qualification"]["frozenCandidateSha256"])
                self.assertEqual(4, len(terminal["qualification"]["stages"]))

    def test_stage_process_failure_records_terminal_fail_instead_of_escaping(self):
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, bodies, server = self.make_plan(Path(directory), "26.1-fabric")

            def fail_stage(*_args, **_kwargs):
                raise ExternalRuntimeWorldgenStageError("fixture child exited before PASS")

            result = execute_external_runtime_worldgen(
                plan, paths, paths.run_id, canonical_cells=canonical_cells(), range_identities=RANGES,
                opener=lambda url, *, timeout: Response(url, bodies[url]),
                command_executor=self.installer(plan, server), stage_runner=fail_stage,
                candidate_inspector=self.inspector(plan), execution_source_provenance=PROVENANCE,
            )
            self.assertEqual(Verdict.FAIL, result.verdict)
            self.assertEqual("WORLDGEN_STAGE:fixture child exited before PASS", result.reason)
            terminal = json.loads((plan.evidence_root / "terminal.json").read_text(encoding="utf-8"))
            self.assertEqual("FAIL", terminal["verdict"])
            self.assertEqual(result.reason, terminal["reason"])

    def test_forward_upgrade_copies_only_source_world_and_runs_existing_resume_stage_under_fakes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths, base_plan, bodies, server = self.make_plan(root, "26.1.1-fabric")
            cell = self.cells["26.1.1-fabric"]
            fixture = paths.run_directory / "nightly/05-world-upgrade"
            evidence = paths.evidence_directory / "nightly/05-world-upgrade"
            stage = external_runtime_worldgen_resume_stage(
                cell, base_plan.candidate, paths, frozen_candidate_root=base_plan.frozen_candidate_root,
                fixture_root=fixture, evidence_root=evidence,
            )
            # Reuse the fake official-runtime inventory already used by the
            # ordinary worldgen executor test.
            production = base_plan.stages[0].smoke
            stage = replace(stage, smoke=replace(
                stage.smoke, minecraft_server=production.minecraft_server,
                downloads=production.downloads,
                mods=(stage.smoke.mods[0], replace(
                    stage.smoke.mods[1], source=production.mods[1].source, sha256=production.mods[1].sha256,
                )),
            ))
            source_root = root / "source/26.1-fabric"
            source_world = source_root / "run/nightly/02-worldgen-seam-structures/production/runtime/world"
            settings = source_world / "dimensions/minecraft/overworld/data/ringworld/settings.dat"
            settings.parent.mkdir(parents=True)
            settings.write_bytes(settings_bytes(256, 16384, 11))
            raw = (
                "Done (0.1s)!\n"
                "[worldgen-matrix] seed=11 layout=16384x256 biomeFamilies=[badlands, beach, cave, desert, forest] "
                "biomeIds=[minecraft:plains] chunks=10 caveAir=1 ores=1 logs=1 starts=1 "
                "structureIds=[minecraft:village] crossingStarts=0 crossingStructureIds=[] references=1 lootContainers=1 "
                "structuresWithSpawnOverrides=0 spawnOverrideStructureIds=[]\n"
                "[worldgen-matrix] monumentStatus=SATISFIED monumentReason=reason monumentCandidate=0,0 spawnOverrideEntries=0\n"
                "[stronghold-test] PASS\n"
            ).encode()
            source_evidence = source_root / "evidence/nightly/02-worldgen-seam-structures"
            source_evidence.mkdir(parents=True)
            source_log = source_evidence / "production-resume.log"
            source_log.write_bytes(raw)
            terminal = source_evidence / "terminal.json"
            terminal.write_text(json.dumps({
                "fixture": "worldgen-seam-structures", "cell_id": "26.1-fabric", "loader": "fabric",
                "minecraft_version": "26.1", "verdict": "PASS", "qualification": {
                    "frozenCandidateSha256": base_plan.candidate.sha256,
                    "stages": ["production-fresh", "production-resume", "seam-crossing", "terminal-policy"],
                    "captures": {"production-resume": {"logSha256": sha256(raw)}},
                },
                "assemblies": [{"runtimeRoot": str(source_world.parent)}],
            }), encoding="utf-8")
            source = ForwardUpgradeSource(
                "26.1-fabric", "fabric", "26.1", source_root, terminal, sha256(terminal.read_bytes()),
                base_plan.candidate.sha256, source_world, source_log, sha256(raw),
            )
            fake_plan = type("ResumeOnly", (), {"stages": (stage,)})()
            result = execute_external_runtime_forward_upgrade(
                source, cell, base_plan.candidate, base_plan.quick_terminal_evidence, stage, paths, paths.run_id,
                frozen_candidate_root=base_plan.frozen_candidate_root, quick_evidence_root=paths.cell_root,
                fixture_root=fixture, evidence_root=evidence, canonical_cells=canonical_cells(), range_identities=RANGES,
                opener=lambda url, *, timeout: Response(url, bodies[url]), command_executor=self.installer(fake_plan, server),
                stage_runner=self.stage_runner(fake_plan), candidate_inspector=self.inspector(base_plan),
                execution_source_provenance=PROVENANCE,
            )
            self.assertEqual(Verdict.PASS, result.verdict, result.reason)
            self.assertTrue((stage.world_root / "dimensions/minecraft/overworld/data/ringworld/settings.dat").is_file())
            terminal_result = json.loads((evidence / "terminal.json").read_text(encoding="utf-8"))
            self.assertEqual("PASS", terminal_result["verdict"])
            self.assertEqual("26.1-fabric", terminal_result["source_cell_id"])

    def test_forward_upgrade_rejects_intermediate_source_world_symlink(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source_root = root / "source"
            outside = root / "outside"
            outside.mkdir()
            (outside / "world").mkdir()
            source_root.mkdir()
            (source_root / "linked").symlink_to(outside, target_is_directory=True)
            from external_runtime_worldgen_executor import _regular_tree
            with self.assertRaisesRegex(Exception, "contained regular directory|symlink"):
                _regular_tree(source_root / "linked/world", source_root,
                              "source upgrade world")


if __name__ == "__main__":
    unittest.main()
