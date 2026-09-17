import importlib.util,json,tempfile,unittest,zipfile
from pathlib import Path
spec=importlib.util.spec_from_file_location('publisher',Path(__file__).with_name('publish-reviewed.py'))
publisher=importlib.util.module_from_spec(spec);spec.loader.exec_module(publisher)

class ReleaseGateTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
  root=Path(self.tmp.name);self.v=root/'validated';self.d=root/'device';self.v.mkdir();self.d.mkdir()
  self.a={'schema':1,'visually_reviewed':True,'tag':'v0.2.0-alpha.1','head_sha':'a'*40,'source_sha':'b'*40,'screenshots':[]}
  with zipfile.ZipFile(self.v/'MagicBox-source.zip','w') as z:
   z.comment=self.a['source_sha'].encode();z.writestr('gradle.properties','project.version.name=0.2.0-alpha.1\n')
  rows=[]
  for edition, package in publisher.PACKAGES.items():
   apk=self.v/f'app-{edition}-debug.apk'
   with zipfile.ZipFile(apk,'w') as z:
    z.writestr('AndroidManifest.xml',b'synthetic-test-only')
    if edition=='universal':z.writestr('assets/components/provenance.txt','MagicBox='+self.a['source_sha']+'\n')
   rows.append({'edition':edition,'package':package,'bytes':apk.stat().st_size,'sha256':publisher.digest(apk)})
   (self.d/f'{edition}-tests.txt').write_text('OK (6 tests)\n')
   images=self.d/edition;images.mkdir();manifest={'package':package,'screenshots':[]}
   for page in ['home','subscriptions','settings']:
    for theme in ['light','dark']:
     name=f'qa-{page}-{theme}.png';f=images/name;f.write_bytes((edition+name).encode())
     row={'edition':edition,'file':name,'sha256':publisher.digest(f)};self.a['screenshots'].append(row)
     manifest['screenshots'].append({'file':name,'sha256':row['sha256'],'foreground_package':package})
   (images/'qa-manifest.json').write_text(json.dumps(manifest))
  (self.v/'apk-verification.json').write_text(json.dumps(rows))
  (self.d/'crashes.txt').write_text('');(self.d/'events.txt').write_text('')
 def validate(self):return publisher.validate(self.a,self.v,self.d)
 def rejects(self):
  with self.assertRaises((RuntimeError,ValueError)):self.validate()
 def test_valid(self):self.assertEqual(2,len(self.validate()))
 def test_unreviewed(self):self.a['visually_reviewed']=False;self.rejects()
 def test_source(self):self.a['source_sha']='c'*40;self.rejects()
 def test_version(self):self.a['tag']='v0.2.0-alpha.2';self.rejects()
 def test_stable(self):self.a['tag']='v0.2.0';self.rejects()
 def test_tampered_apk(self):(self.v/'app-universal-debug.apk').write_bytes(b'changed');self.rejects()
 def test_failed_tests(self):(self.d/'ui-tests.txt').write_text('FAILURES\nOK (6 tests)\n');self.rejects()
 def test_missing_image(self):self.a['screenshots'].pop();self.rejects()
 def test_changed_image(self):(self.d/'ui'/'qa-home-light.png').write_bytes(b'changed');self.rejects()
 def test_foreground(self):
  f=self.d/'ui'/'qa-manifest.json';value=json.loads(f.read_text());value['screenshots'][0]['foreground_package']='android';f.write_text(json.dumps(value));self.rejects()
 def test_crash(self):(self.d/'crashes.txt').write_text('Process: com.github.lightjunction.magicbox.ui');self.rejects()
 def test_anr(self):(self.d/'events.txt').write_text('am_anr 1 com.github.lightjunction.magicbox');self.rejects()

if __name__=='__main__':unittest.main()
