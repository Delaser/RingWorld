"""Pure tests for the external dedicated-runtime smoke-plan model."""

from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]


def load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


MODEL = load("minecraft_qualification_model", ROOT / "scripts/minecraft_qualification_model.py")
SMOKE = load("external_runtime_smoke", ROOT / "scripts/external_runtime_smoke.py")


class ExternalRuntimeSmokePlanTest(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        self.cells = {cell["id"]: cell for cell in self.manifest["cells"]}
        self.frozen_root = ROOT / "dist/qualification/frozen-candidates"

    def candidate(self, loader: str, name: str = "ringworld.jar"):
        return SMOKE.CandidateJar(self.frozen_root / loader / name, "a" * 64, loader, ">=26.1 <26.2")

    def plan(self, cell_id: str):
        cell = self.cells[cell_id]
        paths = MODEL.QualificationPaths.from_cell(ROOT, cell, "external-plan")
        return SMOKE.external_runtime_smoke_plan(cell, self.candidate(cell["loader"]), paths,
                                                 frozen_candidate_root=self.frozen_root)

    def test_every_cell_has_isolated_safe_small_plan_and_stable_lock(self) -> None:
        lock_by_cell: dict[str, Path] = {}
        for cell_id, cell in self.cells.items():
            plan = self.plan(cell_id)
            paths = MODEL.QualificationPaths.from_cell(ROOT, cell, "other-run")
            lock_by_cell[cell_id] = plan.lock_path
            self.assertTrue(MODEL.is_within(plan.layout.root, plan.layout.root.parents[3]), cell_id)
            self.assertTrue(MODEL.is_within(plan.layout.root, plan.lock_path.parents[1]), cell_id)
            self.assertEqual(plan.lock_path, paths.lock_path, cell_id)
            self.assertIn("server-ip=127.0.0.1", plan.files[1].contents)
            self.assertIn(f"server-port={cell['profile']['server_port']}", plan.files[1].contents)
            self.assertIn("view-distance=6", plan.files[1].contents)
            self.assertIn("simulation-distance=4", plan.files[1].contents)
            self.assertIn("eula=true", plan.files[0].contents)
            self.assertIn("circumferenceBlocks=2048", plan.files[2].contents)
            self.assertIn("widthBlocks=416", plan.files[2].contents)
            self.assertIn("wallHeightBlocks=160", plan.files[2].contents)
            self.assertIn("pregenerateTerrainAtlas=false", plan.files[2].contents)
            self.assertIn("requestOceanMonument=false", plan.files[2].contents)
            self.assertTrue(all(MODEL.is_within(path, plan.layout.root.parents[2]) for path in (
                plan.layout.root, plan.layout.mods_directory, plan.layout.config_directory,
                plan.layout.eula_path, plan.layout.server_properties_path, plan.layout.ringworld_properties_path,
                plan.layout.log_path,
            )), cell_id)
            self.assertTrue(all(MODEL.is_within(item.destination, plan.layout.root.parents[2])
                                for item in plan.downloads), cell_id)
            self.assertTrue(MODEL.is_within(plan.minecraft_server.destination, plan.layout.root.parents[2]), cell_id)
            self.assertIn("server.jar", plan.minecraft_server.url, cell_id)
            self.assertTrue(all(MODEL.is_within(item.destination, plan.layout.root)
                                for item in plan.mods), cell_id)
            self.assertTrue(all(MODEL.is_within(item.path, plan.layout.root)
                                for item in plan.files), cell_id)
        self.assertEqual(6, len(lock_by_cell))

    def test_fabric_exact_inventory_and_official_installer_command(self) -> None:
        for cell_id in ("26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"):
            plan = self.plan(cell_id)
            self.assertEqual(["RingWorld", "Fabric API"], [entry.name for entry in plan.mods])
            self.assertEqual(["Fabric Installer", "Fabric API"], [item.name for item in plan.downloads])
            self.assertEqual("server", plan.minecraft_server.name)
            self.assertEqual("fabric", plan.installer.loader)
            self.assertIn("server", plan.installer.argv)
            self.assertIn("-mcversion", plan.installer.argv)
            self.assertIn("-loader", plan.installer.argv)
            self.assertIn("-downloadMinecraft", plan.installer.argv)
            self.assertIn("-dir", plan.installer.argv)
            self.assertIn("fabric-server-launch.jar", " ".join(plan.launch.argv))
            self.assertIsNone(plan.generated_run_script)
            self.assertEqual("loader-bootstrap", plan.expected_log_markers[0].name)
            self.assertEqual("Fabric Loader", plan.expected_log_markers[0].required_substring)

    def test_neoforge_inventory_has_no_fabric_api_and_uses_generated_script_contract(self) -> None:
        for cell_id in ("26.1-neoforge", "26.1.1-neoforge", "26.1.2-neoforge"):
            plan = self.plan(cell_id)
            self.assertEqual(["RingWorld"], [entry.name for entry in plan.mods])
            self.assertEqual(["NeoForge Installer"], [item.name for item in plan.downloads])
            self.assertEqual("server", plan.minecraft_server.name)
            self.assertEqual(("--installServer", str(plan.layout.root)), plan.installer.argv[-2:])
            self.assertEqual(("./run.sh", "nogui"), plan.launch.argv)
            self.assertIsNotNone(plan.generated_run_script)
            assert plan.generated_run_script is not None
            self.assertEqual(plan.layout.neoforge_run_script, plan.generated_run_script.path)
            self.assertEqual(plan.layout.neoforge_user_jvm_args, plan.generated_run_script.required_sibling)
            self.assertEqual("RingWorld NeoForge platform bootstrap active", plan.expected_log_markers[0].required_substring)

    def test_one_frozen_candidate_is_reusable_for_all_same_loader_cells_without_metadata_claim(self) -> None:
        candidate = self.candidate("fabric")
        plans = []
        for cell_id in ("26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"):
            cell = self.cells[cell_id]
            paths = MODEL.QualificationPaths.from_cell(ROOT, cell, "frozen")
            plans.append(SMOKE.external_runtime_smoke_plan(cell, candidate, paths, frozen_candidate_root=self.frozen_root))
        self.assertEqual({candidate.path.resolve(strict=False)}, {plan.mods[0].source for plan in plans})
        self.assertTrue(all(plan.candidate_origin == "frozen-candidate" for plan in plans))
        self.assertTrue(all("metadata range" in " ".join(plan.future_validations).lower() for plan in plans))

    def test_candidate_rejects_escape_loader_mismatch_and_non_disposable_paths(self) -> None:
        cell = self.cells["26.1-fabric"]
        paths = MODEL.QualificationPaths.from_cell(ROOT, cell, "reject")
        with self.assertRaises(MODEL.InvocationError):
            SMOKE.external_runtime_smoke_plan(cell, self.candidate("neoforge"), paths, frozen_candidate_root=self.frozen_root)
        escaped = SMOKE.CandidateJar(ROOT / "outside.jar", "a" * 64, "fabric")
        with self.assertRaises(MODEL.InvocationError):
            SMOKE.external_runtime_smoke_plan(cell, escaped, paths, frozen_candidate_root=self.frozen_root)
        with self.assertRaises(MODEL.InvocationError):
            SMOKE.external_runtime_smoke_plan(cell, self.candidate("fabric", "not-a-jar.txt"), paths,
                                              frozen_candidate_root=self.frozen_root)
        malformed = copy.deepcopy(cell)
        malformed["id"] = "other-cell"
        with self.assertRaises(MODEL.InvocationError):
            SMOKE.external_runtime_smoke_plan(malformed, self.candidate("fabric"), paths,
                                              frozen_candidate_root=self.frozen_root)

    def test_cell_build_candidate_is_allowed_without_a_frozen_root(self) -> None:
        cell = self.cells["26.1.2-neoforge"]
        paths = MODEL.QualificationPaths.from_cell(ROOT, cell, "build-candidate")
        candidate = SMOKE.CandidateJar(paths.build_directory / "libs/ringworld.jar", "b" * 64, "neoforge")
        plan = SMOKE.external_runtime_smoke_plan(cell, candidate, paths)
        self.assertEqual("cell-build", plan.candidate_origin)


if __name__ == "__main__":
    unittest.main()
