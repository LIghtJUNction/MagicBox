"""Promote exact tested bytes. This script does not rebuild or re-sign APKs."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import zipfile

PACKAGES = {"universal": "com.github.lightjunction.magicbox", "ui": "com.github.lightjunction.magicbox.ui"}


def single(root: Path, name: str) -> Path:
    matches = list(root.rglob(name))
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one {name}, found {len(matches)}")
    return matches[0]


def prepare(validated: Path, device: Path, output: Path, sha: str, tag: str) -> dict:
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or not re.fullmatch(r"v\d+\.\d+\.\d+-alpha\.\d+", tag):
        raise ValueError("Invalid immutable SHA or Alpha version")
    source = single(validated, "MagicBox-source.zip")
    with zipfile.ZipFile(source) as archive:
        if archive.comment.decode().strip() != sha:
            raise ValueError("Reviewed source archive does not match the validated commit")
        properties = archive.read("gradle.properties").decode()
        if f"project.version.name={tag[1:]}" not in properties.splitlines():
            raise ValueError("Tag does not match the version embedded in source")
    rows = json.loads(single(validated, "apk-verification.json").read_text())
    if len(rows) != 2 or {row["edition"] for row in rows} != set(PACKAGES):
        raise ValueError("Both independently verified editions are required")
    staged = []
    for row in rows:
        edition = row["edition"]
        if row["package"] != PACKAGES[edition]:
            raise ValueError("Unexpected package name")
        apk = single(validated, f"app-{edition}-debug.apk")
        if apk.stat().st_size != row["bytes"] or hashlib.sha256(apk.read_bytes()).hexdigest() != row["sha256"]:
            raise ValueError("APK bytes differ from the verified artifact")
        tests = single(device, f"{edition}-tests.txt").read_text()
        if not re.search(r"OK \([1-9][0-9]* tests?\)", tests) or re.search(r"FAILURES|INSTRUMENTATION_FAILED|Process crashed", tests):
            raise ValueError(f"{edition} did not pass device acceptance")
        # Evidence is captured from the installed app, not generated artwork.
        if not list(device.glob(f"{edition}/**/home-light.png")):
            raise ValueError(f"Missing installed-app visual evidence: {edition}")
        staged.append((apk, output / f"MagicBox-{tag}-{edition}-test.apk"))
    output.mkdir(parents=True, exist_ok=False)
    for source_apk, destination in staged:
        shutil.copy2(source_apk, destination)
    shutil.copy2(source, output / f"MagicBox-{tag}-source.zip")
    report = {"commit": sha, "tag": tag, "editions": rows, "signing": "CI debug/test signing",
              "android_emulator": 35, "rooted_physical_device_verified": False}
    (output / "validation.json").write_text(json.dumps(report, indent=2) + "\n")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--sha", required=True)
    parser.add_argument("--tag", required=True)
    args = parser.parse_args()
    prepare(Path("validated"), Path("device"), Path("release"), args.sha, args.tag)
