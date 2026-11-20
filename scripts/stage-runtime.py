"""Validate pinned runtime artifacts before staging the universal APK inputs."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess

ABIS = ("arm64-v8a", "x86_64")
LIBRARIES = ("libsingbox.so", "libproxylink.so", "libebpfcheck.so")
LICENSES = ("MagicNet-LICENSE", "sing-box-LICENSE", "Proxylink-LICENSE")


def inspect(directory, expected):
    provenance = {}
    for line in (directory / "PROVENANCE.txt").read_text().splitlines():
        name, revision = line.rsplit(" ", 1)
        if name in provenance or not re.fullmatch(r"[0-9a-f]{40}", revision):
            raise ValueError("Invalid or duplicate runtime source identity")
        provenance[name] = revision
    if provenance != expected:
        raise ValueError("Runtime artifacts do not match the reviewed source cohort")
    checksums = {}
    for line in (directory / "SHA256SUMS").read_text().splitlines():
        digest, name = line.split()
        if name not in LIBRARIES or name in checksums or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise ValueError("Invalid, duplicate or unexpected native checksum entry")
        binary = directory / name
        if binary.is_symlink():
            raise ValueError("Native artifacts must be regular files")
        content = binary.read_bytes()
        if len(content) <= 1024 or content[:4] != b"\x7fELF":
            raise ValueError(f"Not a complete ELF component: {name}")
        if hashlib.sha256(content).hexdigest() != digest:
            raise ValueError(f"Runtime checksum mismatch: {name}")
        checksums[name] = digest
    if set(checksums) != set(LIBRARIES):
        raise ValueError("Runtime checksum inventory is incomplete")
    if {p.name for p in directory.glob("*.so")} != set(LIBRARIES):
        raise ValueError("Unexpected native artifact")
    for name in LICENSES:
        if not (directory / name).read_bytes().strip():
            raise ValueError(f"Empty component license: {name}")
    return checksums


def stage(root, inputs, reviewed_sha):
    lock = json.loads((root / "runtime.lock.json").read_text())
    if lock["schema"] != 1 or lock["android_abis"] != list(ABIS):
        raise ValueError("Unsupported runtime inventory")
    expected = {"MagicNet": lock["magicnet"]["revision"],
                "sing-box": lock["sing_box"]["revision"],
                "Proxylink": lock["subscription"]["revision"],
                "MagicBox adapters": reviewed_sha}
    inventories = {target: inspect(inputs / f"runtime-{target}", expected)
                   for target in (*ABIS, "linux-x86_64")}
    destination = root / "app/runtime"
    if destination.exists():
        raise ValueError("Runtime staging destination already exists; use a clean build directory")
    # All targets are validated before any APK input is created.
    components = destination / "assets/components"
    components.mkdir(parents=True)
    records = []
    for abi in ABIS:
        target = destination / "jniLibs" / abi
        target.mkdir(parents=True)
        for name in LIBRARIES:
            shutil.copy2(inputs / f"runtime-{abi}" / name, target / name)
            records.append(f"{inventories[abi][name]}  {abi}/{name}")
        shutil.copy2(inputs / f"runtime-{abi}/PROVENANCE.txt", components / f"{abi}-provenance.txt")
    (components / "SHA256SUMS").write_text("\n".join(records) + "\n")
    licenses = destination / "assets/licenses"
    licenses.mkdir()
    for name in LICENSES:
        shutil.copy2(inputs / f"runtime-{ABIS[0]}" / name, licenses / name)
    shutil.copy2(root / "LICENSE", licenses / "MagicBox-LICENSE")
    shutil.copy2(root / "runtime.lock.json", destination / "assets/runtime.lock.json")
    return {"source": reviewed_sha, "android_abis": list(ABIS), "checksums": inventories}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=project, text=True).strip()
    print(json.dumps(stage(project, args.input.resolve(), revision), indent=2))
