"""Reject mismatched or partial runtime artifacts before they enter an APK."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("stage_runtime", Path(__file__).with_name("stage-runtime.py"))
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class RuntimeStagingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.inputs = self.root / "inputs"
        self.sha = "a" * 40
        lock = {"schema": 1, "android_abis": list(runtime.ABIS),
                "magicnet": {"revision": "b" * 40}, "sing_box": {"revision": "c" * 40},
                "subscription": {"revision": "d" * 40}}
        (self.root / "runtime.lock.json").write_text(json.dumps(lock))
        (self.root / "LICENSE").write_text("synthetic license fixture")
        for target in (*runtime.ABIS, "linux-x86_64"):
            directory = self.inputs / f"runtime-{target}"
            directory.mkdir(parents=True)
            records = []
            for name in runtime.LIBRARIES:
                content = b"\x7fELF" + (name + target).encode() * 100
                (directory / name).write_bytes(content)
                records.append(f"{hashlib.sha256(content).hexdigest()}  {name}")
            (directory / "SHA256SUMS").write_text("\n".join(records) + "\n")
            (directory / "PROVENANCE.txt").write_text(
                f"MagicNet {'b' * 40}\nsing-box {'c' * 40}\nProxylink {'d' * 40}\nMagicBox adapters {self.sha}\n")
            for name in runtime.LICENSES:
                (directory / name).write_text("synthetic component license")

    def stage(self):
        return runtime.stage(self.root, self.inputs, self.sha)

    def test_exact_bytes_and_complete_inventory(self):
        self.stage()
        records = (self.root / "app/runtime/assets/components/SHA256SUMS").read_text().splitlines()
        self.assertEqual(6, len(records))
        for abi in runtime.ABIS:
            for name in runtime.LIBRARIES:
                self.assertEqual((self.inputs / f"runtime-{abi}" / name).read_bytes(),
                                 (self.root / "app/runtime/jniLibs" / abi / name).read_bytes())

    def test_rejects_wrong_adapter_commit_without_staging_anything(self):
        self.sha = "e" * 40
        with self.assertRaises(ValueError):
            self.stage()
        self.assertFalse((self.root / "app/runtime").exists())

    def test_rejects_tampered_linux_test_binary(self):
        (self.inputs / "runtime-linux-x86_64/libproxylink.so").write_bytes(b"\x7fELF" + b"x" * 2048)
        with self.assertRaises(ValueError):
            self.stage()
        self.assertFalse((self.root / "app/runtime").exists())

    def test_rejects_duplicate_or_traversing_checksum(self):
        checksum = self.inputs / "runtime-arm64-v8a/SHA256SUMS"
        original = checksum.read_text()
        for extra in (original.splitlines()[0], "0" * 64 + "  ../libsingbox.so"):
            checksum.write_text(original + extra + "\n")
            with self.assertRaises(ValueError):
                self.stage()

    def test_rejects_unlisted_library(self):
        (self.inputs / "runtime-x86_64/libunexpected.so").write_bytes(b"\x7fELF")
        with self.assertRaises(ValueError):
            self.stage()

    def test_does_not_overwrite_existing_staged_runtime(self):
        self.stage()
        with self.assertRaises(ValueError):
            self.stage()
