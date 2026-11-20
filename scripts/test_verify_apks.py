"""Regression tests for UI-only packaging, including the AndroidX renderer."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("apk_verifier", Path(__file__).with_name("verify-apks.py"))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class NativeBoundaryTests(unittest.TestCase):
    def test_ui_renderer_is_not_a_proxy_runtime(self):
        names = [f"lib/{abi}/libandroidx.graphics.path.so" for abi in verifier.ABIS]
        self.assertEqual(sorted(names), verifier.inspect_native("ui", names))

    def test_ui_rejects_each_proxy_component(self):
        for lib in verifier.RUNTIME_LIBS:
            with self.subTest(lib=lib), self.assertRaises(ValueError):
                verifier.inspect_native("ui", [f"lib/arm64-v8a/{lib}"])

    def test_ui_rejects_unknown_library(self):
        with self.assertRaises(ValueError):
            verifier.inspect_native("ui", ["lib/arm64-v8a/libunexpected.so"])

    def test_universal_requires_every_component_for_every_abi(self):
        complete = [f"lib/{abi}/{lib}" for abi in verifier.RUNTIME_ABIS for lib in verifier.RUNTIME_LIBS]
        self.assertEqual(sorted(complete), verifier.inspect_native("universal", complete))
        for omitted in complete:
            with self.subTest(omitted=omitted), self.assertRaises(ValueError):
                verifier.inspect_native("universal", [n for n in complete if n != omitted])

    def test_directory_entries_do_not_count_as_libraries(self):
        self.assertEqual([], verifier.inspect_native("ui", ["lib/", "lib/arm64-v8a/"]))

    def test_nested_and_unknown_abi_paths_are_rejected(self):
        for path in ("lib/arm64-v8a/nested/libandroidx.graphics.path.so", "lib/unknown/libandroidx.graphics.path.so"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                verifier.inspect_native("ui", [path])
