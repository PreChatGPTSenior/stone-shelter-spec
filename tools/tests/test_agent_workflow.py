"""Complete coordinator lifecycle with fake model/services/test adapters in a disposable Git repo.

This proves orchestration contracts, never real-provider or Stone Shelter acceptance.
"""
from contextlib import ExitStack
from copy import deepcopy
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).absolute().parents[1]))
from execution.core import Blocked, State, command, json_write, sha, stable
from execution.budgets import Budgets
from execution.gates import report
from execution.history import recovery_status, timeline, verify_evidence
from execution.model_policy import load, resolve, ROLES
import execution.agent_runtime as runtime
import orchestrate as engine


class WorkflowTest(unittest.TestCase):
    def exercise(self, reject_review=False):
        temporary = tempfile.TemporaryDirectory(); self.addCleanup(temporary.cleanup)
        root = Path(temporary.name).resolve() / 'repository'; root.mkdir()
        def git(argv, cwd=None, **kwargs): return command(argv, cwd=root if cwd is None else cwd, **kwargs)
        git(['git', 'init', '-b', 'main']); git(['git', 'config', 'user.name', 'Offline Workflow Fixture'])
        git(['git', 'config', 'user.email', 'fixture@example.invalid'])
        (root / 'feature.txt').write_text('before')
        for role in ROLES:
            path = root / 'execution/prompts' / (role + '.md'); path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('Offline fixture role; this prompt is never sent to a model.')
        git(['git', 'add', 'feature.txt', 'execution/prompts']); git(['git', 'commit', '-m', 'fixture seed'])
        coordinator = root / '.execution/integration'
        git(['git', 'worktree', 'add', '-b', 'codex/integration', str(coordinator), 'HEAD'])
        task = {'id': 'T0002-002', 'change': '0002-catalog-details', 'title': 'Offline workflow fixture',
                'kind': 'implementation', 'depends_on': [], 'exclusive_resources': ['fixture'],
                'requirements': ['R003'], 'acceptance': ['A01'], 'write_paths': ['feature.txt'],
                'environment': [], 'checks': [{'adapter': 'fixture'}], 'browser_required': True,
                'max_attempts': 1, 'timeout_seconds': 5}
        models = load([task['id']]); receipts = {}
        # Synthetic admission exists ONLY in this isolated test; live preflight is never called.
        evidence = root / '.execution/fixture-admission.json'; json_write(evidence, {'mode': 'OFFLINE_FIXTURE'})
        import time
        for role in ROLES:
            selection = resolve(models, 'gpt', task['id'], role)
            receipts[selection['selection_sha256']] = {'status': 'PASS', 'selection_sha256': selection['selection_sha256'],
                'policy_sha256': stable(models), 'checked_at_epoch': time.time(), 'artifact': str(evidence), 'sha256': sha(evidence)}
        policy = {'schema': 1, 'unit': 'input_plus_output_tokens', 'pools': {
            pool: {'limit_tokens': 10000, 'invocation_allowance_tokens': 1000} for pool in ('codex', 'openrouter')}}
        budget = Budgets(root / '.execution/budgets.sqlite', policy)
        state = State(root / '.execution/state.sqlite'); calls = []
        real_popen = subprocess.Popen

        class FakeModel:
            def __init__(inner, argv, **kwargs):
                output = Path(argv[argv.index('--output-last-message') + 1])
                role = output.name.split('-')[0]; calls.append(role)
                workspace = Path(kwargs['cwd'])
                if role == 'developer': (workspace / 'feature.txt').write_text('after')
                rejected = reject_review and role == 'code_review'
                value = {'status': 'CHANGES_REQUESTED' if rejected else 'APPROVED',
                         'summary': 'Fixture reviewer found missing coverage' if rejected else 'Fixture evidence satisfies assigned contribution',
                         'findings': ['Fixture missing coverage'] if rejected else [], 'evidence': ['R003', 'A01']}
                output.write_text(json.dumps(value))
                events = [{'type': 'thread.started', 'thread_id': 'fixture-' + role}, {'type': 'turn.started'},
                          {'type': 'item.completed', 'item': {'id': 'tool-1', 'type': 'command_execution',
                           'command': 'fixture edit' if role == 'developer' else 'fixture inspect', 'exit_code': 0,
                           'aggregated_output': 'fixture evidence'}},
                          {'type': 'turn.completed', 'usage': {'input_tokens': 10, 'cached_input_tokens': 2, 'output_tokens': 3}}]
                inner.stdout = io.StringIO(''.join(json.dumps(event) + '\n' for event in events))
                inner.stderr = io.StringIO(''); inner.stdin = io.StringIO(); inner.returncode = 0
            def wait(inner, timeout=None): return 0
            def poll(inner): return 0

        def popen(argv, *args, **kwargs):
            if argv[0] == 'codex': return FakeModel(argv, **kwargs)
            if argv[0] == 'git': return real_popen(argv, *args, **kwargs)
            raise AssertionError('Unexpected external process in offline workflow')

        def fixture_report(stage_name, task, run, candidate, **extra):
            raw = run / (stage_name + '-fixture.json'); json_write(raw, {'mode': 'OFFLINE_FIXTURE'})
            return report(run, stage_name, task, candidate, [raw], **extra)
        def static(task, tree, run, env, candidate, names): return fixture_report('static', task, run, candidate)
        def tests(task, tree, run, env, stack, candidate): return fixture_report('tests', task, run, candidate, tests=1)
        def browser(task, tree, run, env, stack, candidate): return fixture_report('browser', task, run, candidate, assertions=1)

        with ExitStack() as stack:
            for target, name, value in [(engine, 'ROOT', root), (runtime, 'ROOT', root),
                (engine, 'MODEL_POLICY', models), (engine, 'MODEL_PRESET', 'gpt'), (engine, 'MODEL_RECEIPTS', receipts),
                (engine, 'MODEL_ENV', {}), (engine, 'BUDGET', budget)]: stack.enter_context(patch.object(target, name, value))
            stack.enter_context(patch('subprocess.Popen', side_effect=popen))
            stack.enter_context(patch('socket.socket.connect', side_effect=AssertionError('Network forbidden')))
            stack.enter_context(patch.object(engine, 'command', side_effect=git))
            stack.enter_context(patch.object(engine, 'tasks', return_value=[task]))
            stack.enter_context(patch.object(engine, 'load_models', return_value=models))
            stack.enter_context(patch.object(engine, 'configure', return_value={'api': 'fixture', 'ui': 'fixture'}))
            stack.enter_context(patch.object(engine, 'prepare_dependencies'))
            stack.enter_context(patch.object(engine, 'probe', return_value=[]))
            stack.enter_context(patch.object(engine, 'static_gate', side_effect=static))
            selected = stack.enter_context(patch.object(engine, 'selected_tests', side_effect=tests))
            stack.enter_context(patch.object(engine, 'browser', side_effect=browser))
            if reject_review:
                with self.assertRaises(Blocked): engine.work(task, state, coordinator, {})
                selected.assert_not_called()
            else: engine.work(task, state, coordinator, {})
        return root, state, calls, coordinator

    def test_full_fake_workflow_links_requirement_tools_changes_reviews_commit_and_done(self):
        root, state, calls, coordinator = self.exercise()
        self.assertEqual('DONE', state.status()['T0002-002']['status'])
        self.assertEqual(['developer'] + list(ROLES[1:]) * 2, calls)
        events = timeline(root / '.execution/trace.sqlite', 'T0002-002')
        self.assertEqual(['R003'], events[0]['payload']['packet']['requirements'])
        invocations = [e for e in events if e['event'] == 'invocation.finished']
        self.assertEqual(11, len({e['session'] for e in invocations}))
        self.assertEqual('feature.txt', invocations[0]['payload']['changes'][0]['path'])
        self.assertTrue(any(e['event'] == 'cli.event' and e['payload']['action'].get('command') == 'fixture edit' for e in events))
        integration = next(e for e in events if e['event'] == 'integration.completed')
        self.assertEqual(state.status()['T0002-002']['commit'], integration['payload']['commit'])
        self.assertEqual('after', (coordinator / 'feature.txt').read_text())
        self.assertEqual('before', (root / 'feature.txt').read_text())
        self.assertEqual('NO_UNRESOLVED_EXECUTION', recovery_status(root / '.execution')['status'])
        self.assertEqual(11, verify_evidence(root / '.execution')['raw_streams'])
        receipt_path = root / invocations[0]['payload']['receipt']
        raw_result = Path(json.loads(receipt_path.read_text())['raw_result'])
        raw_result.write_text('{"tampered":true}')
        with self.assertRaises(Blocked): verify_evidence(root / '.execution')

    def test_reviewer_rejection_records_reason_and_prevents_tests_merge_and_done(self):
        root, state, calls, coordinator = self.exercise(reject_review=True)
        self.assertEqual('BLOCKED', state.status()['T0002-002']['status'])
        self.assertEqual(['developer', 'code_review'], calls)
        events = timeline(root / '.execution/trace.sqlite')
        review = next(e for e in events if e['event'] == 'invocation.finished' and e['role'] == 'code_review')
        self.assertEqual('CHANGES_REQUESTED', review['payload']['agent_decision']['status'])
        self.assertFalse(any(e['event'] == 'integration.completed' for e in events))
        self.assertEqual('before', (coordinator / 'feature.txt').read_text())


if __name__ == '__main__': unittest.main()
