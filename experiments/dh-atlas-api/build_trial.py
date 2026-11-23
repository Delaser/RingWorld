"""Build the standalone Fabric client trial using the RingWorld dev classpath."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath-file", required=True, type=Path,
                        help="RingWorld client development classpath, one entry per line")
    parser.add_argument("--dh-jar", required=True, type=Path)
    parser.add_argument("--loader-jar", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--large-probe", action="store_true",
                        help="Build the disposable Large-world measurement jar instead of the player add-on")
    args = parser.parse_args()
    source = Path(__file__).resolve().parent
    classpath = args.classpath_file.read_text().splitlines()
    classpath.extend(str(path.resolve(strict=True)) for path in (args.dh_jar, args.loader_jar))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    java_home = os.environ.get("JAVA_HOME")
    bin_dir = Path(java_home, "bin") if java_home else Path()
    with TemporaryDirectory() as classes:
        sources = sorted((source / "java").rglob("*.java"))
        if args.large_probe:
            sources.extend(sorted((source / "proof").glob("*.java")))
        subprocess.run([str(bin_dir / "javac"), "-proc:none", "--release", "25",
                        "-cp", os.pathsep.join(classpath), "-d", classes,
                        *sources], check=True)
        classes_dir = Path(classes)
        meta = json.loads((source / "resources/fabric.mod.json").read_text())
        if args.large_probe:
            config = source / "proof/large-handoff.mixins.json"
            (classes_dir / config.name).write_bytes(config.read_bytes())
            meta["mixins"].append(config.name)
            meta["name"] += " (disposable Large-world probe)"
        (classes_dir / "fabric.mod.json").write_text(json.dumps(meta))
        subprocess.run([str(bin_dir / "jar"), "cf", args.output,
                        "-C", classes, ".", "-C", source / "resources", "surface.mixins.json"], check=True)


if __name__ == "__main__":
    main()
