"""Package the optional Distant Horizons verification fixture separately from RingWorld."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
from zipfile import ZIP_DEFLATED, ZipFile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath-file", required=True, type=Path,
                        help="Compiled RingWorld client, Minecraft, and Mixin dependencies, one per line")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--large-probe", action="store_true",
                        help="Use the historical 26.2 Large-world frame-time recorder")
    args = parser.parse_args()
    proof = Path(__file__).resolve().parent / "proof"
    if args.large_probe:
        sources = [proof / name for name in ("LargeView.java", "Seen.java", "SeenDraw.java")]
        mixins = json.loads((proof / "large-handoff.mixins.json").read_text())
    else:
        sources = [proof / "DhProbe.java"]
        mixins = dict(required=True, package="rw.dhqa", compatibilityLevel="JAVA_25", client=["DhProbe"])
    classpath = os.pathsep.join(args.classpath_file.read_text().splitlines())
    javac = Path(os.environ["JAVA_HOME"], "bin", "javac") if "JAVA_HOME" in os.environ else "javac"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory() as tmp:
        subprocess.run([javac, "-proc:none", "--release", "25", "-cp", classpath, "-d", tmp, *sources], check=True)
        with ZipFile(args.output, "w", ZIP_DEFLATED) as jar:
            for path in Path(tmp).rglob("*.class"):
                jar.write(path, path.relative_to(tmp))
            jar.writestr("dhprobe.mixins.json", json.dumps(mixins))
            jar.writestr("fabric.mod.json", json.dumps(dict(schemaVersion=1, id="ringdhprobe",
                         version="1", environment="client", mixins=["dhprobe.mixins.json"])))
            jar.writestr("META-INF/neoforge.mods.toml",
                         'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\n'
                         'modId="ringdhprobe"\nversion="1"\ndisplayName="RingWorld DH QA"\n'
                         '[[mixins]]\nconfig="dhprobe.mixins.json"\n')


if __name__ == "__main__":
    main()
