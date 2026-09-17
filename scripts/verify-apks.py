"""Verify packaged APK bytes, signatures and the edition boundary."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
RUNTIME_ABIS = {"arm64-v8a", "x86_64"}
RUNTIME_LIBS = {"libsingbox.so", "libproxylink.so", "libmbprobe.so"}
# Compose uses this renderer even when no proxy runtime is packaged.
UI_LIBS = {"libandroidx.graphics.path.so"}


def inspect_native(edition, names):
    """Reject all unexpected native code; allow only the named UI dependency."""
    libs = {name for name in names if name.startswith("lib/") and not name.endswith("/")}
    for name in libs:
        parts = name.split("/")
        if len(parts) != 3 or parts[1] not in ABIS:
            raise ValueError(f"Unexpected native library path: {name}")
        allowed = UI_LIBS | (RUNTIME_LIBS if edition == "universal" else set())
        if parts[2] not in allowed:
            raise ValueError(f"Unexpected {edition} native component: {name}")
    if edition == "universal":
        required = {f"lib/{abi}/{lib}" for abi in RUNTIME_ABIS for lib in RUNTIME_LIBS}
        missing = required - libs
        if missing:
            raise ValueError(f"Missing universal runtime components: {sorted(missing)}")
    return sorted(libs)


def verify():
    root = Path("app/build/outputs/apk")
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    evidence = Path("evidence")
    evidence.mkdir(exist_ok=True)
    report = []
    for edition, package in [("universal", "com.github.lightjunction.magicbox"), ("ui", "com.github.lightjunction.magicbox.ui")]:
        apk = root / edition / "debug" / f"app-{edition}-debug.apk"
        signature = subprocess.check_output([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)], text=True)
        badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(apk)], text=True)
        if f"package: name='{package}'" not in badging:
            raise ValueError(f"Unexpected package: {badging.splitlines()[0]}")
        with zipfile.ZipFile(apk) as archive:
            names = archive.namelist()
            libs = inspect_native(edition, names)
            for name in libs:
                if archive.read(name)[:4] != b"\x7fELF":
                    raise ValueError(f"Not an ELF component: {name}")
            for asset in ("index.html", "cloud.css", "cloud.js"):
                name = f"assets/cloud/{asset}"
                if archive.read(name) != Path(f"app/assets/cloud/{asset}").read_bytes():
                    raise ValueError(f"Packaged UI differs from the reviewed source: {name}")
            if edition == "ui" and any(n.startswith("assets/components/") for n in names):
                raise ValueError("UI edition must not contain standalone runtime assets")
            if edition == "universal":
                checksums = archive.read("assets/components/SHA256SUMS").decode().splitlines()
                checked = set()
                for entry in checksums:
                    digest, relpath = entry.split()
                    if not re.fullmatch(r"[0-9a-f]{64}", digest):
                        raise ValueError("Invalid native checksum")
                    name = f"lib/{relpath}"
                    if hashlib.sha256(archive.read(name)).hexdigest() != digest:
                        raise ValueError(f"Native provenance mismatch: {name}")
                    checked.add(name)
                required = {f"lib/{abi}/{lib}" for abi in RUNTIME_ABIS for lib in RUNTIME_LIBS}
                if checked != required:
                    raise ValueError("Checksum inventory does not exactly cover the runtime")
        (evidence / f"{edition}-signature.txt").write_text(signature)
        (evidence / f"{edition}-badging.txt").write_text(badging)
        report.append({"edition": edition, "package": package, "bytes": apk.stat().st_size,
                       "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "native_files": libs})
    (evidence / "apk-verification.json").write_text(json.dumps(report, indent=2) + "\n")
    # Keep the exact reviewed source beside the binary evidence, excluding untracked build inputs.
    subprocess.run(["git", "archive", "--format=zip", "-o", str(evidence / "MagicBox-source.zip"), "HEAD"], check=True)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    import unittest
    suite = unittest.defaultTestLoader.discover("scripts", pattern="test_verify_apks.py")
    if not unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful():
        raise SystemExit(1)
    verify()
