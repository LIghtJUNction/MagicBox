#!/usr/bin/env python3
"""Offline integration tests of the shipped native adapters and core (no user data)."""
import argparse
import base64
import contextlib
import http.server
import json
from pathlib import Path
import socket
import re
import urllib.request
import subprocess
import tempfile
import threading
import time
import unittest

PARSER = argparse.ArgumentParser()
PARSER.add_argument('--runtime-dir', type=Path, required=True)
ARGS, EXTRA = PARSER.parse_known_args()
CORE = str((ARGS.runtime_dir / 'libsingbox.so').resolve())
CONVERTER = str((ARGS.runtime_dir / 'libproxylink.so').resolve())

class RuntimeTests(unittest.TestCase):
    def convert(self, raw):
        with tempfile.TemporaryDirectory() as tmp:
            src, dst = Path(tmp) / 'input', Path(tmp) / 'output'
            src.write_bytes(raw.encode() if isinstance(raw, str) else raw)
            result = subprocess.run([CONVERTER, str(src), str(dst)], capture_output=True, timeout=15)
            return result, json.loads(dst.read_text()) if dst.exists() else None

    def test_supported_format_matrix(self):
        node = {'type':'socks', 'tag':'fixture', 'server':'127.0.0.1', 'server_port':39001}
        uri = 'socks://127.0.0.1:39001#fixture'
        fixtures = {
            'uri': uri,
            'base64': base64.b64encode(uri.encode()).decode(),
            'clash': 'proxies:\n  - name: fixture\n    type: socks5\n    server: 127.0.0.1\n    port: 39001\n',
            'sing-box': json.dumps({'outbounds':[node]}),
            'xray': json.dumps({'outbounds':[{'tag':'fixture','protocol':'socks','settings':{'servers':[{'address':'127.0.0.1','port':39001}]}}]}),
            'sip008': json.dumps({'version':1,'servers':[{'server':'127.0.0.1','server_port':39001,'method':'aes-128-gcm','password':'fixture-only','remarks':'fixture'}]}),
        }
        for name, raw in fixtures.items():
            with self.subTest(format=name):
                result, report = self.convert(raw)
                self.assertEqual(result.returncode, 0, result.stderr.decode())
                self.assertEqual(report['schema'], 1)
                self.assertEqual(report['failed'], 0)
                self.assertEqual(len(report['outbounds']), 1)

    def test_unknown_nodes_are_counted(self):
        result, report = self.convert('socks://127.0.0.1:39001#good\nnot-a-supported-link://secret-do-not-print')
        self.assertEqual(result.returncode, 0)
        self.assertEqual(report['failed'], 1)
        self.assertNotIn(b'secret-do-not-print', result.stderr)

    def test_invalid_and_oversized_inputs_fail_without_secrets(self):
        for raw in [b'\xff', b'a\x00b', b'x' * (8*1024*1024 + 1), b'private://secret-do-not-print']:
            with self.subTest(length=len(raw)):
                result, report = self.convert(raw)
                self.assertNotEqual(result.returncode, 0)
                self.assertIsNone(report)
                self.assertNotIn(b'secret-do-not-print', result.stderr)

    def test_actual_two_hop_local_proxy(self):
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                data = b'magicbox-offline-routing-proof'
                self.send_response(200); self.send_header('Content-Length', str(len(data)))
                self.end_headers(); self.wfile.write(data)
            def log_message(self, *_): pass
        server = http.server.ThreadingHTTPServer(('127.0.0.1',0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        def port():
            with socket.socket() as s: s.bind(('127.0.0.1',0)); return s.getsockname()[1]
        upstream_port, local_port, api_port = port(), port(), port()
        result, report = self.convert(f'socks://127.0.0.1:{upstream_port}#offline-fixture')
        self.assertEqual(result.returncode, 0)
        node = report['outbounds'][0]; node['tag'] = 'egress'
        upstream = {'log':{'level':'error'},'inbounds':[{'type':'mixed','listen':'127.0.0.1','listen_port':upstream_port}], 'outbounds':[{'type':'direct','tag':'direct'}], 'route':{'final':'direct'}}
        # Exercise the actual production DNS constant, not a permissive test-only config.
        source = (Path(__file__).resolve().parents[1] / 'app/universal/ProfileStore.kt').read_text()
        match = re.search(r'val dns = JSONObject\("""(.*?)"""\)', source, re.S)
        self.assertIsNotNone(match, 'production DNS fixture moved; update this extraction')
        dns = json.loads(match.group(1))
        local = {'log':{'level':'error'}, 'dns':dns,
                 'inbounds':[{'type':'mixed','listen':'127.0.0.1','listen_port':local_port}],
                 'outbounds':[node, {'type':'direct','tag':'direct'}],
                 'route':{'final':'egress','default_domain_resolver':'bootstrap'},
                 'experimental':{'clash_api':{'external_controller':f'127.0.0.1:{api_port}','secret':'offline-fixture'}}}
        processes = []
        try:
            with tempfile.TemporaryDirectory() as tmp:
                for name, config in [('upstream',upstream), ('local',local)]:
                    file = Path(tmp) / (name+'.json'); file.write_text(json.dumps(config))
                    check = subprocess.run([CORE,'check','-c',str(file)], capture_output=True, timeout=15)
                    self.assertEqual(check.returncode, 0, check.stderr.decode())
                    processes.append(subprocess.Popen([CORE,'run','-c',str(file)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL))
                for _ in range(60):
                    try:
                        conn = socket.create_connection(('127.0.0.1',local_port), timeout=0.2)
                        break
                    except OSError: time.sleep(0.1)
                else: self.fail('mixed listener did not start')
                request = urllib.request.Request(f'http://127.0.0.1:{api_port}/version', headers={'Authorization':'Bearer offline-fixture'})
                with urllib.request.urlopen(request, timeout=3) as result:
                    self.assertIn('sing-box', json.load(result)['version'])
                with conn:
                    target = f'127.0.0.1:{server.server_port}'
                    conn.sendall(f'GET http://{target}/proof HTTP/1.1\r\nHost: {target}\r\nConnection: close\r\n\r\n'.encode())
                    conn.settimeout(5); response = b''
                    while True:
                        chunk = conn.recv(8192)
                        if not chunk: break
                        response += chunk
                    self.assertIn(b'200 OK',response)
                    self.assertIn(b'magicbox-offline-routing-proof',response)
        finally:
            for process in processes:
                process.terminate()
                try: process.wait(timeout=5)
                except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=2)
            server.shutdown(); server.server_close()

if __name__ == '__main__':
    unittest.main(argv=['test_runtime.py']+EXTRA, verbosity=2)
