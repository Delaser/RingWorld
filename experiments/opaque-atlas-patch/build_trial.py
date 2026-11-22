"""Build the optional MC 26.2 experiment from a development classpath. No production source set includes it."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def build(classpath, out, javac, proof):
    sources = sorted((HERE / "java").rglob("*.java"))
    configs = {"patch": ("mixin", ["PatchMixin", "PatchDrawRangeMixin", "BlockPipelineAccess", "RenderTypeAccess", "AtlasBuffersAccess", "FullSkyMixin", "PackTargetAccess", "PackTerrainProjection", "PackProjectionUniforms"])}
    if proof:
        sources += sorted((HERE / "proof").rglob("*.java"))
        configs["proof"] = ("fixture.mixin", ["FinalFrame", "HoldView"])
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as classes:
        subprocess.run([javac, "-proc:none", "-cp", classpath, "-d", classes, *sources], check=True)
        with ZipFile(out / "opaque-patch.jar", "w", ZIP_DEFLATED) as jar:
            for path in sorted(Path(classes).rglob("*.class")):
                jar.write(path, path.relative_to(classes))
            filenames = [name + ".mixins.json" for name in configs]
            for name, (package, mixins) in configs.items():
                jar.writestr(name + ".mixins.json", json.dumps({
                    "required": True, "package": "dev.ringworld.experiment." + package,
                    "compatibilityLevel": "JAVA_25", "client": mixins
                }))
            jar.writestr("fabric.mod.json", json.dumps({
                "schemaVersion": 1, "id": "opaquepatch", "version": "1", "environment": "client",
                "mixins": filenames, "depends": {"minecraft": "26.2", "ringworld": "*", "iris": "*"}
            }))
            jar.writestr("META-INF/neoforge.mods.toml",
                         'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n'
                         '[[mods]]\nmodId="opaquepatch"\nversion="1"\ndisplayName="Opaque Atlas patch trial"\n'
                         + "".join('[[mixins]]\nconfig="' + name + '"\n' for name in filenames))
    # Give the sampled footprint a single depth owner in the disposable review world.
    shader = "assets/ringworld/shaders/core/ring_surface.fsh"
    source = (ROOT / "src/client/resources" / shader).read_text()
    anchor = "    vec4 previous = texture(Sampler1, surfaceUv);"
    if source.count(anchor) != 1:
        raise ValueError("Review the footprint against the current Atlas shader before rerunning")
    fragment = source.replace(anchor, """    if (RingWorldLayout.y == 2048 && RingWorldLayout.z == 128
            && surfaceUv.x >= 1.0 - 368.0 / 2048.0 && surfaceUv.x <= 1.0 - 208.0 / 2048.0
            && abs(intrinsicWidth) < 18.0) discard;
""" + anchor)
    with ZipFile(out / "atlas-patch-owner.zip", "w", ZIP_DEFLATED) as pack:
        pack.writestr("pack.mcmeta", json.dumps({"pack": {"description": "Opaque Atlas patch test footprint",
                                                            "min_format": 88, "max_format": 88}}))
        pack.writestr(shader, fragment)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath-file", type=Path, required=True, help="One compiler classpath entry per line")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--proof", action="store_true", help="Include the disposable integrated-world capture fixture")
    args = parser.parse_args()
    build(os.pathsep.join(args.classpath_file.read_text().splitlines()), args.output, args.javac, args.proof)
