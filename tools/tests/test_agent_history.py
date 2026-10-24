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
from execution.budgets import Budgets


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

    def completed_task(self):
        state = State(self.directory / 'state.sqlite'); state.claim(self.task, 'attempt')
        self.journal.append('task.claimed', task=self.task['id'], trace='attempt')
        state.set(self.task['id'], 'DONE', 'attempt', 'fixture-commit'); state.release('attempt')
        self.journal.append('task.completed', task=self.task['id'], trace='attempt', payload={'commit':'fixture-commit'})
        return state

    def test_missing_state_database_blocks_before_recreation(self):
        state = self.completed_task(); state.path.unlink()
        self.assertEqual('RECOVERY_REQUIRED', recovery_status(self.directory)['status'])
        with self.assertRaises(Blocked): require_recovered(self.directory)
        with self.assertRaises(Blocked): backup(self.directory, self.root / 'incomplete-copy')
        self.assertFalse(state.path.exists())
        import orchestrate as engine
        with patch.object(engine,'ROOT',self.root), patch.object(engine,'runtime_env',return_value={}), \
                patch.object(engine,'toolchain'), patch.object(engine,'State') as writer, \
                patch.object(engine,'live_preflight') as probe:
            with self.assertRaises(Blocked): engine.run([], 1)
            writer.assert_not_called(); probe.assert_not_called()

    def test_deleted_row_stale_owner_and_commit_disagreement_block(self):
        state = self.completed_task()
        for sql in ("UPDATE tasks SET owner='stale'", "UPDATE tasks SET commit_id='wrong'", 'DELETE FROM tasks'):
            with self.subTest(sql=sql):
                with closing(sqlite3.connect(state.path)) as db: db.execute(sql); db.commit()
                with self.assertRaises(Blocked): require_recovered(self.directory)
                with closing(sqlite3.connect(state.path)) as db:
                    db.execute('INSERT OR REPLACE INTO tasks VALUES (?,?,?,?,?,?)',
                               (self.task['id'],'DONE','attempt','fixture-commit','fixture',0)); db.commit()

    def test_missing_trace_database_with_completed_task_blocks(self):
        self.completed_task(); self.journal.path.unlink()
        with self.assertRaises(Blocked): require_recovered(self.directory)
        self.assertFalse(self.journal.path.exists())

    def test_empty_directory_and_preflight_only_history_do_not_require_task_database(self):
        fresh = self.root / 'fresh'
        self.assertEqual('NO_UNRESOLVED_EXECUTION', require_recovered(fresh)['status'])
        self.assertFalse(fresh.exists())
        policy = {'schema':1,'unit':'input_plus_output_tokens','pools':{
            pool:{'limit_tokens':100,'invocation_allowance_tokens':50} for pool in ('codex','openrouter')}}
        budget = Budgets(self.directory / 'budgets.sqlite', policy)
        selection = {'provider_config':{'kind':'builtin','provider_id':'openai'}}
        budget.reserve('probe', selection, self.task['id'], 'developer', 'preflight')
        budget.observe('probe', {'known_input_tokens':2,'known_output_tokens':1,'status':'COMPLETE'}, final=True)
        self.journal.append('invocation.started', session='probe', payload={'kind':'preflight'})
        receipt = self.directory / 'model-preflight/probe.execution.json'; json_write(receipt, {'artifact_hashes':{}})
        self.journal.append('invocation.finished', session='probe', payload={
            'receipt':str(receipt),'sha256':sha(receipt)})
        self.assertEqual('NO_UNRESOLVED_EXECUTION', require_recovered(self.directory)['status'])
        budget.path.unlink()
        with self.assertRaises(Blocked): require_recovered(self.directory)
        self.assertFalse(budget.path.exists())

    def test_gate_report_and_raw_artifact_are_both_verified_after_backup_restore(self):
        from execution.gates import retain_static_artifacts
        cache = self.root / 'disposable-cache'; cache.mkdir()
        report = cache / 'pmd.xml'; report.write_text('<pmd/>')
        run = self.directory / 'runs/attempt'; run.mkdir(parents=True)
        retained = retain_static_artifacts(run, [report]); report.unlink()
        stage = {'artifact_hashes':{str(retained[0]):sha(retained[0])}}
        envelope = run / 'static.json'; json_write(envelope, stage)
        self.journal.append('stage.completed', payload={'stage':'static','report':stage,
            'artifact':str(envelope),'sha256':sha(envelope)})
        destination = self.root / 'complete-backup'; backup(self.directory, destination)
        restored = self.root / 'restored'; restore(destination, restored)
        self.assertEqual('VERIFIED', verify_evidence(restored)['status'])
        copied = destination / retained[0].relative_to(self.directory); copied.unlink()
        with self.assertRaises(Blocked): verify(destination)

    def test_changed_analyzer_output_cannot_be_archived_as_verified(self):
        from execution.gates import retain_static_artifacts
        source = self.root / 'analyzer.xml'; source.write_text('<verified/>')
        expected = {str(source):sha(source)}
        source.write_text('<changed/>')
        run = self.directory / 'runs/attempt'; run.mkdir(parents=True)
        with self.assertRaises(Blocked): retain_static_artifacts(run,[source],expected)


if __name__ == '__main__': unittest.main()
