import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('alpha', Path(__file__).with_name('prepare-alpha.py'))
alpha = importlib.util.module_from_spec(spec)
spec.loader.exec_module(alpha)

class PromotionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.validated = self.root / 'validated'
        self.device = self.root / 'device'
        self.validated.mkdir(); self.device.mkdir()
        self.sha = 'a' * 40
        self.tag = 'v0.2.0-alpha.1'
        with zipfile.ZipFile(self.validated / 'MagicBox-source.zip', 'w') as archive:
            archive.comment = self.sha.encode()
            archive.writestr('gradle.properties', 'project.version.name=0.2.0-alpha.1\n')
        rows = []
        for edition, package in alpha.PACKAGES.items():
            content = f'synthetic-{edition}-apk'.encode()
            (self.validated / f'app-{edition}-debug.apk').write_bytes(content)
            (self.device / f'{edition}-tests.txt').write_text('OK (3 tests)\n')
            for theme in ('light', 'dark'):
                (self.device / f'{edition}-{theme}-tests.txt').write_text('OK (1 test)\n')
            images = self.device / edition / 'evidence'
            images.mkdir(parents=True)
            (images / 'home-light.png').write_bytes(b'synthetic screenshot')
            (images / 'home-dark.png').write_bytes(b'synthetic screenshot')
            rows.append(dict(edition=edition, package=package, bytes=len(content), sha256=hashlib.sha256(content).hexdigest()))
        (self.validated / 'apk-verification.json').write_text(json.dumps(rows))
        (self.device / 'native-runtime-tests.txt').write_text('OK (1 test)\n')
        (self.device / 'native-routing-proof.txt').write_text('PASS: installed Android parser, synthetic unit-test fixture\n')
        (self.device / 'coinstalled-packages.txt').write_text(''.join(f'package:{p}\n' for p in alpha.PACKAGES.values()))
    def promote(self):
        return alpha.prepare(self.validated, self.device, self.root / 'release', self.sha, self.tag)
    def test_exact_bytes_promoted(self):
        report = self.promote()
        self.assertFalse(report['rooted_physical_device_verified'])
        for edition in alpha.PACKAGES:
            self.assertEqual((self.validated/f'app-{edition}-debug.apk').read_bytes(),
                (self.root/f'release/MagicBox-{self.tag}-{edition}-test.apk').read_bytes())
    def test_reject_modified_apk(self):
        (self.validated/'app-universal-debug.apk').write_bytes(b'changed')
        with self.assertRaises(ValueError): self.promote()
    def test_reject_failed_device(self):
        (self.device/'ui-tests.txt').write_text('FAILURES\nOK (3 tests)')
        with self.assertRaises(ValueError): self.promote()
    def test_reject_different_commit(self):
        self.sha='b'*40
        with self.assertRaises(ValueError): self.promote()
    def test_reject_missing_screenshot(self):
        (self.device/'ui/evidence/home-light.png').unlink()
        with self.assertRaises(ValueError): self.promote()
    def test_reject_existing_output(self):
        (self.root/'release').mkdir()
        with self.assertRaises(FileExistsError): self.promote()
    def test_reject_other_package(self):
        path=self.validated/'apk-verification.json'
        rows=json.loads(path.read_text()); rows[0]['package']='unexpected'
        path.write_text(json.dumps(rows))
        with self.assertRaises(ValueError): self.promote()
    def test_reject_version_mismatch(self):
        self.tag='v0.2.0-alpha.2'
        with self.assertRaises(ValueError): self.promote()
    def test_reject_missing_native_test(self):
        (self.device/'native-runtime-tests.txt').unlink()
        with self.assertRaises(ValueError): self.promote()
    def test_reject_failed_native_test_even_with_ok_summary(self):
        (self.device/'native-runtime-tests.txt').write_text('Process crashed\nOK (1 test)')
        with self.assertRaises(ValueError): self.promote()
    def test_reject_failed_dark_theme(self):
        (self.device/'ui-dark-tests.txt').write_text('FAILURES\nOK (1 test)')
        with self.assertRaises(ValueError): self.promote()
    def test_reject_missing_native_proof(self):
        (self.device/'native-routing-proof.txt').write_text('not a successful routing test')
        with self.assertRaises(ValueError): self.promote()
    def test_reject_wrong_coinstalled_package(self):
        (self.device/'coinstalled-packages.txt').write_text('package:unrelated\n')
        with self.assertRaises(ValueError): self.promote()

if __name__ == '__main__': unittest.main()
