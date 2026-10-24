"""Local history, actual abrupt-process death and audit backup/restore fixtures; no agents."""
from concurrent.futures import ThreadPoolExecutor
from contextlib import closing
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).absolute().parents[1]))
from execution.core import Blocked, State, json_write, sha, stable
from execution.history import History, read_rows, recovery_status, require_recovered, timeline, verify_evidence
from execution.audit_backup import backup, idle, restore, verify


class HistoryTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        # macOS /var is a system alias; use the canonical newly-created fixture directory.
        self.root = Path(self.tmp.name).resolve(); self.directory = self.root / '.execution'
        self.journal = History(self.directory / 'trace.sqlite')
        self.task = {'id': 'T0002-002', 'depends_on': [], 'exclusive_resources': ['fixture-resource']}
        self.addCleanup(patch.stopall)
        patch('socket.socket.connect', side_effect=AssertionError('Network forbidden')).start()

    def test_parallel_event_order_survives_reopening(self):
        def append(i): return self.journal.append('cli.event', task='T0002-002', session=str(i), payload={'command': 'fixture'})
        with ThreadPoolExecutor(max_workers=5) as pool: list(pool.map(append, range(20)))
        reopened = History(self.directory / 'trace.sqlite')
        reopened.append('gate.completed', task='T0002-002', payload={'status': 'PASS'})
        rows = timeline(reopened.path, 'T0002-002')
        self.assertEqual(list(range(1, 22)), [row['seq'] for row in rows])
        self.assertEqual(20, len({row['session'] for row in rows[:-1]}))

    def test_history_content_or_order_tampering_blocks(self):
        self.journal.append('first', task='T0002-002'); self.journal.append('second', task='T0002-002')
        with closing(sqlite3.connect(self.journal.path)) as db:
            db.execute("UPDATE events SET body='{}' WHERE seq=1"); db.commit()
        with self.assertRaises(Blocked): timeline(self.journal.path)

    def test_actual_process_exit_preserves_committed_trace_and_claim(self):
        script = '''
from execution.history import History
from execution.core import State
from pathlib import Path
import os,sys
root=Path(sys.argv[1])
State(root/'state.sqlite').claim({'id':'T0002-002','depends_on':[],'exclusive_resources':['fixture-resource']},'attempt')
History(root/'trace.sqlite').append('invocation.started',trace='attempt',task='T0002-002',session='invocation',role='developer')
os._exit(17)
'''
        env = dict(os.environ); env['PYTHONPATH'] = str(Path(__file__).absolute().parents[1])
        result = subprocess.run([sys.executable, '-c', script, str(self.directory)], env=env, capture_output=True, timeout=10)
        self.assertEqual(17, result.returncode, result.stderr)
        report = recovery_status(self.directory)
        self.assertEqual('RECOVERY_REQUIRED', report['status'])
        self.assertEqual('invocation', report['unfinished_invocations'][0]['session'])
        with self.assertRaises(Blocked): require_recovered(self.directory)
        self.assertEqual(1, len(read_rows(self.directory / 'state.sqlite', 'leases')))

    def test_state_events_include_claim_owner_commit_and_release(self):
        state = State(self.directory / 'state.sqlite'); state.claim(self.task, 'attempt')
        state.set('T0002-002', 'DONE', 'attempt', 'commit-fixture', 'all evidence qualified')
        state.release('attempt')
        rows = read_rows(state.path, 'events')
        self.assertEqual(['CLAIMED', 'DONE', 'LEASES_RELEASED'], [r['status'] for r in rows])
        self.assertEqual('commit-fixture', rows[1]['commit_id'])
        self.assertTrue(all(r['owner'] == 'attempt' for r in rows))

    def test_backup_restore_retains_history_hashes_and_detects_modified_artifact(self):
        event = {'type': 'item.completed', 'item': {'type': 'command_execution', 'command': 'fixture', 'exit_code': 0}}
        raw = self.directory / 'runs/attempt/developer.events.jsonl'; raw.parent.mkdir(parents=True)
        raw.write_text(json.dumps(event) + '\n')
        self.journal.append('cli.event', task='T0002-002', session='fixture', payload={
            'raw_artifact': '.execution/runs/attempt/developer.events.jsonl', 'line': 1, 'raw_event_sha256': stable(event)})
        artifact = self.directory / 'runs/attempt/result.json'; json_write(artifact, {'status': 'fixture'})
        destination = self.root / 'backup'
        manifest = backup(self.directory, destination)
        restored = self.root / 'restored-audit'; self.assertFalse(restore(destination, restored)['execution_resumed'])
        self.assertEqual(timeline(self.journal.path), timeline(restored / 'trace.sqlite'))
        self.assertEqual(sha(artifact), manifest['files']['runs/attempt/result.json'])
        (destination / 'runs/attempt/result.json').write_text('tampered')
        with self.assertRaises(Blocked): restore(destination, self.root / 'rejected')
        self.assertFalse((self.root / 'rejected').exists())

    def test_backup_blocks_active_coordinator_and_never_overwrites(self):
        with idle(self.directory):
            with self.assertRaises(Blocked): backup(self.directory, self.root / 'busy')
        destination = self.root / 'backup'; backup(self.directory, destination)
        with self.assertRaises(Blocked): backup(self.directory, destination)
        with self.assertRaises(Blocked): restore(destination, self.directory)

    def test_restore_rejects_traversal_and_symlinks_before_copy(self):
        destination = self.root / 'backup'; backup(self.directory, destination)
        manifest = json.loads((destination / 'manifest.json').read_text())
        manifest['files']['../outside'] = 'fake'; json_write(destination / 'manifest.json', manifest)
        with self.assertRaises(Blocked): verify(destination)
        link = self.root / 'alias'; link.symlink_to(self.directory)
        with self.assertRaises(Blocked): backup(link, self.root / 'rejected')

    def test_protected_path_rejected_without_metadata(self):
        with patch.object(Path, 'is_symlink', side_effect=AssertionError('Metadata touched')):
            with self.assertRaises(Blocked): backup(self.root / 'engineering-log', self.root / 'rejected')

    def test_raw_trace_truncation_and_uncertain_integration_are_detected(self):
        raw = self.directory / 'runs/attempt/events.jsonl'; raw.parent.mkdir(parents=True); raw.write_text('')
        self.journal.append('cli.event', task='T0002-002', session='fixture', payload={
            'raw_artifact': '.execution/runs/attempt/events.jsonl', 'line': 1, 'raw_event_sha256': stable({'fixture': True})})
        with self.assertRaises(Blocked): verify_evidence(self.directory)
        self.journal.append('integration.intent', task='T0002-002', trace='attempt', payload={'candidate_commit': 'fixture'})
        self.assertEqual(1, len(recovery_status(self.directory)['unfinished_integrations']))

    def test_uncommitted_sqlite_event_is_rolled_back_after_process_death(self):
        self.journal.append('committed')
        script = "import sqlite3,os,sys; db=sqlite3.connect(sys.argv[1]); db.execute('BEGIN IMMEDIATE'); db.execute('DELETE FROM events'); os._exit(19)"
        result = subprocess.run([sys.executable, '-c', script, str(self.journal.path)], capture_output=True, timeout=10)
        self.assertEqual(19, result.returncode)
        self.assertEqual(['committed'], [event['event'] for event in timeline(self.journal.path)])


if __name__ == '__main__': unittest.main()
