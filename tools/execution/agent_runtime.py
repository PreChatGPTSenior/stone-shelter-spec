"""Bounded Codex invocations, compatibility admission and observed usage accounting.

No inference happens on import. Live checks are called only by explicit execution.
"""
from __future__ import annotations

from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import threading
import time
import uuid

from .core import ROOT, Blocked, command, json_read, json_write, sha, stable
from .model_policy import cli_args


def utc():
    return datetime.now(timezone.utc).isoformat()


def private_stream(path):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    return os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w')


def redact(text, env, selection):
    names = {'OPENAI_API_KEY', 'OPENROUTER_API_KEY', 'CODEX_ACCESS_TOKEN'}
    names.add(selection['provider_config'].get('api_key_env', ''))
    for name in names:
        if name and env.get(name):
            text = text.replace(env[name], '[REDACTED]')
    return re.sub(r'\bsk-(?:or-v1-)?[A-Za-z0-9_-]{12,}', '[REDACTED]', text)


def count(value):
    return value if type(value) is int and value >= 0 else None


class Usage:
    """Each turn.completed usage is per turn; cached/reasoning tokens are subsets."""
    def __init__(self):
        self.turns = []
        self.started = None
        self.incomplete = False
        self.tool_outputs = []
        self.observed_models = set()
        self.parse_errors = 0

    def event(self, value, now):
        kind = value.get('type')
        if kind == 'turn.started':
            if self.started is not None:
                self.incomplete = True
            self.started = now
        elif kind in ('turn.completed', 'turn.failed'):
            if self.started is None:
                self.incomplete = True
            usage = value.get('usage') or {}
            fields = {key: count(usage.get(key)) for key in
                      ('input_tokens', 'cached_input_tokens', 'output_tokens', 'reasoning_output_tokens')}
            self.turns.append({'iteration': len(self.turns) + 1, 'status': kind,
                               'elapsed_seconds': None if self.started is None else round(now - self.started, 6),
                               **fields})
            if kind != 'turn.completed' or fields['input_tokens'] is None or fields['output_tokens'] is None:
                self.incomplete = True
            if (fields['cached_input_tokens'] is not None and fields['input_tokens'] is not None
                    and fields['cached_input_tokens'] > fields['input_tokens']):
                self.incomplete = True
            if (fields['reasoning_output_tokens'] is not None and fields['output_tokens'] is not None
                    and fields['reasoning_output_tokens'] > fields['output_tokens']):
                self.incomplete = True
            self.started = None
        if kind == 'item.completed':
            item = value.get('item') or {}
            if item.get('type') == 'command_execution' and item.get('exit_code') == 0:
                self.tool_outputs.append(str(item.get('aggregated_output', '')))
        # Report observation separately; never fabricate it from the request.
        if kind in ('thread.started', 'turn.started', 'turn.completed') and isinstance(value.get('model'), str):
            self.observed_models.add(value['model'])

    def summary(self, succeeded):
        complete = succeeded and bool(self.turns) and not self.incomplete and self.started is None and not self.parse_errors
        totals = {}
        for key in ('input_tokens', 'cached_input_tokens', 'output_tokens', 'reasoning_output_tokens'):
            values = [turn[key] for turn in self.turns]
            totals[key] = sum(values) if values and all(v is not None for v in values) else None
            totals['known_' + key] = sum(v for v in values if v is not None)
        totals['total_tokens'] = (totals['input_tokens'] + totals['output_tokens']
                                  if complete and totals['input_tokens'] is not None and totals['output_tokens'] is not None else None)
        return {'status': 'COMPLETE' if complete else 'PARTIAL_OR_UNAVAILABLE',
                'source': 'codex turn.completed JSONL; provider-reported counts',
                'turns': self.turns, **totals, 'cost_usd': None,
                'billing_status': 'NOT_AVAILABLE_IN_CODEX_USAGE',
                'observed_models': sorted(self.observed_models), 'parse_errors': self.parse_errors}


def append_ledger(path, record):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd = os.open(path, os.O_APPEND | os.O_CREAT | os.O_WRONLY, 0o600)
    with os.fdopen(fd, 'a') as stream:
        fcntl.flock(stream, fcntl.LOCK_EX)
        stream.write(json.dumps(record, separators=(',', ':')) + '\n')
        stream.flush()
        os.fsync(stream.fileno())


def terminate(process):
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()


def invoke(selection, task_id, role, workspace, folder, prompt, timeout,
           kind='agent', iteration='1', env=None, budget=None):
    """Run once; preserve metrics on failure, timeout and interruption. No retry/fallback."""
    env = dict(os.environ if env is None else env)
    key_name = selection['provider_config'].get('api_key_env')
    if key_name and not env.get(key_name, '').strip():
        raise Blocked('Provider credential missing: ' + key_name)
    if budget is None:
        raise Blocked('Budget admission required before model invocation')
    session = uuid.uuid4().hex
    folder = Path(folder)
    folder.mkdir(parents=True, exist_ok=True, mode=0o700)
    output = folder / (role + '-' + session + '.result.json')
    log = folder / (role + '-' + session + '.events.jsonl')
    errors = folder / (role + '-' + session + '.stderr.log')
    receipt = folder / (role + '-' + session + '.execution.json')
    argv = ['codex', 'exec', '--ephemeral', '--sandbox',
            'workspace-write' if role == 'developer' and kind == 'agent' else 'read-only',
            '--cd', str(workspace), '--output-schema', str(ROOT / 'templates/agent-result.schema.json'),
            '--output-last-message', str(output), '--json', *cli_args(selection)]
    if kind == 'preflight':
        argv.append('--skip-git-repo-check')
    argv.append('-')
    started_at, started = utc(), time.monotonic()
    usage, warnings, reader_errors = Usage(), [], []
    process, status, code = None, 'START_FAILED', None
    reservation = budget.reserve(session, selection, task_id, role, kind)
    try:
        with private_stream(log) as stdout, private_stream(errors) as stderr:
            process = subprocess.Popen(argv, cwd=workspace, env=env, stdin=subprocess.PIPE,
                                       stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                       text=True, start_new_session=True)

            def drain(source, destination, events=False):
                try:
                    for line in source:
                        line = redact(line, env, selection)
                        destination.write(line)
                        destination.flush()
                        if not events and re.search(r'unknown model|fallback metadata|unrecognized model', line, re.I):
                            warnings.append('Unqualified model metadata')
                        if events:
                            try:
                                event = json.loads(line)
                                if not isinstance(event, dict):
                                    raise ValueError('Expected event object')
                                usage.event(event, time.monotonic())
                                if event.get('type') in ('turn.completed', 'turn.failed'):
                                    budget.observe(session, usage.summary(True))
                            except (ValueError, TypeError):
                                usage.parse_errors += 1
                                budget.observe(session, usage.summary(False))
                except Exception:
                    reader_errors.append('Event stream could not be recorded')
                    budget.observe(session, usage.summary(False))

            readers = [threading.Thread(target=drain, args=(process.stdout, stdout, True), daemon=True),
                       threading.Thread(target=drain, args=(process.stderr, stderr), daemon=True)]
            for reader in readers:
                reader.start()
            try:
                process.stdin.write(prompt)
                process.stdin.close()
                deadline = time.monotonic() + timeout
                while True:
                    budget.check_stop()
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise subprocess.TimeoutExpired(argv, timeout)
                    try:
                        code = process.wait(timeout=min(0.25, remaining))
                        break
                    except subprocess.TimeoutExpired:
                        continue
                status = 'COMPLETED' if code == 0 else 'FAILED'
            except subprocess.TimeoutExpired:
                status = 'TIMED_OUT'
                terminate(process)
            except Blocked:
                status = 'BUDGET_STOPPED'
                terminate(process)
            except BaseException:
                status = 'INTERRUPTED'
                terminate(process)
                raise
            finally:
                for reader in readers:
                    reader.join(timeout=10)
                if any(reader.is_alive() for reader in readers):
                    reader_errors.append('Incomplete event stream')
                code = process.returncode
    finally:
        try:
            if output.is_file():
                output.write_text(redact(output.read_text(), env, selection))
                os.chmod(output, 0o600)
        except (OSError, UnicodeError):
            reader_errors.append('Final result could not be safely recorded')
        metrics = usage.summary(status == 'COMPLETED' and not reader_errors)
        accounting = budget.observe(session, metrics, final=True, not_started=process is None)
        record = {'schema': 1, 'session': session, 'kind': kind, 'task': task_id,
                  'role': role, 'iteration': str(iteration), 'selection': selection,
                  'started_at': started_at, 'ended_at': utc(),
                  'elapsed_seconds': round(time.monotonic() - started, 6),
                  'status': status, 'exit_code': code, 'usage': metrics,
                  'budget': {**reservation, **accounting},
                  'warnings': warnings + reader_errors, 'event_log': str(log),
                  'stderr_log': str(errors), 'raw_result': str(output)}
        json_write(receipt, record)
        append_ledger(ROOT / '.execution/usage.jsonl', record)
    budget.check_stop()
    if status != 'COMPLETED' or reader_errors or warnings:
        raise Blocked('Agent invocation blocked; inspect execution receipt: ' + str(receipt))
    if usage.observed_models and usage.observed_models != {selection['model']}:
        raise Blocked('Observed model differs from selected model; no automatic fallback accepted')
    if not output.is_file():
        raise Blocked('Agent produced no structured result: ' + str(receipt))
    # An accidental credential echo must not persist in the final model-authored result.
    clean = redact(output.read_text(), env, selection)
    output.write_text(clean)
    os.chmod(output, 0o600)
    try:
        result = json.loads(clean)
    except ValueError as error:
        raise Blocked('Invalid structured agent result') from error
    if (not isinstance(result, dict) or set(result) != {'status', 'summary', 'findings', 'evidence'}
            or result['status'] != 'APPROVED' or result['findings'] != []
            or not isinstance(result['summary'], str) or not isinstance(result['evidence'], list)
            or not all(isinstance(item, str) for item in result['evidence'])):
        raise Blocked('Agent returned findings or blocked; inspect its private result')
    if metrics['status'] != 'COMPLETE':
        raise Blocked('Usage telemetry missing/incomplete; cannot admit unaccounted agent result')
    return result, receipt, session, usage


def catalog_admission(catalog, selection, vision_required=False):
    """Pinned Codex ModelsResponse schema; no generic metadata or effort downgrade."""
    entries = catalog.get('models') if isinstance(catalog, dict) else None
    if not isinstance(entries, list):
        raise Blocked('Codex catalog schema changed; qualify the CLI adapter before launch')
    if not all(isinstance(entry, dict) for entry in entries):
        raise Blocked('Invalid Codex model catalog entry')
    matches = [entry for entry in entries if entry.get('slug') == selection['model']]
    if len(matches) != 1:
        raise Blocked('Exact selected model is absent or ambiguous in the Codex model catalog')
    entry = matches[0]
    raw_levels = entry.get('supported_reasoning_levels')
    if not isinstance(raw_levels, list):
        raise Blocked('Model catalog does not declare reasoning capabilities')
    levels = [item.get('effort') for item in raw_levels if isinstance(item, dict)]
    requested = selection['reasoning_effort']
    if requested not in levels and not (requested == 'none' and not raw_levels):
        raise Blocked('Selected reasoning effort is not advertised by the model catalog')
    if vision_required and 'image' not in entry.get('input_modalities', []):
        raise Blocked('Browser reviewer/planner requires advertised image input support')
    return {'model': entry['slug'], 'advertised_efforts': levels,
            'requested_effort': requested, 'input_modalities': entry.get('input_modalities', []),
            'vision_required': vision_required, 'catalog_entry_sha256': stable(entry)}


def live_preflight(bindings, policy, run_dir, env, budget=None):
    """Tests the real CLI/tool loop/schema for every unique selection before workers."""
    if budget is None:
        raise Blocked('Budget admission required before live preflight')
    budget.guard(bindings)
    expected = json_read(ROOT / 'execution/runtime-lock.json')['codex_cli_qualified']
    version = command(['codex', '--version'], env=env).strip()
    if version.split()[-1:] != [expected]:
        raise Blocked('Codex CLI version requires requalification: expected ' + expected)
    help_text = command(['codex', 'exec', '--help'], env=env)
    if any(flag not in help_text for flag in ('--model', '--config', '--json', '--output-schema', '--ephemeral')):
        raise Blocked('Codex CLI lacks required model/telemetry capabilities')
    receipts = {}
    for binding in bindings:
        budget.guard([binding])
        key = binding['selection_sha256']
        if key in receipts:
            continue
        selection = {k: v for k, v in binding.items() if k not in ('task', 'role')}
        folder = Path(run_dir) / key
        folder.mkdir(parents=True, exist_ok=True, mode=0o700)
        key_name = selection['provider_config'].get('api_key_env')
        if key_name and not env.get(key_name, '').strip():
            raise Blocked('Provider credential missing: ' + key_name)
        # Refresh through the same provider/auth settings. A model listing alone never passes.
        try:
            catalog = json.loads(command(['codex', *cli_args(selection), 'debug', 'models'],
                                         cwd=folder, env=env, timeout=policy['preflight_timeout_seconds']))
        except ValueError as error:
            raise Blocked('Codex model catalog is not valid JSON') from error
        vision = selection['requires_image_input']
        capability = catalog_admission(catalog, selection, vision)
        json_write(folder / 'catalog-admission.json', capability)
        nonce = uuid.uuid4().hex
        (folder / 'probe.txt').write_text(nonce + '\n')
        prompt = ('Compatibility probe only. Read ONLY probe.txt in the current directory using '
                  'one shell command. Do not edit files, inspect other paths, use network tools or '
                  'delegate. Return the required JSON schema: status APPROVED, summary '
                  '"Compatibility probe", findings [], evidence [the exact file contents without whitespace].')
        result, evidence, _, usage = invoke(selection, binding['task'], binding['role'], folder,
                                             folder, prompt, policy['preflight_timeout_seconds'],
                                             kind='preflight', iteration='compatibility', env=env, budget=budget)
        if result['evidence'] != [nonce] or not any(nonce in text for text in usage.tool_outputs):
            raise Blocked('Compatibility probe did not demonstrate a successful local tool round trip')
        receipts[key] = {'status': 'PASS', 'selection_sha256': key,
                         'policy_sha256': stable(policy), 'checked_at_epoch': time.time(),
                         'cli_version': version, 'artifact': str(evidence), 'sha256': sha(evidence),
                         'catalog': capability,
                         'checks': ['exact catalog model and effort', 'real CLI response', 'JSON event stream', 'local tool round trip',
                                    'structured result', 'usage counters', 'no unknown-model warning'],
                         'limit': 'Protocol acceptance does not prove subjective reasoning quality or visual capability'}
    return receipts


def admit(selection, receipt, policy):
    if (not receipt or receipt.get('status') != 'PASS'
            or receipt.get('selection_sha256') != selection['selection_sha256']
            or receipt.get('policy_sha256') != selection['policy_sha256']):
        raise Blocked('Missing/mismatched model compatibility receipt')
    age = time.time() - receipt['checked_at_epoch']
    if not 0 <= age <= policy['preflight_ttl_seconds']:
        raise Blocked('Model compatibility receipt expired; rerun live preflight')
    if sha(receipt['artifact']) != receipt['sha256']:
        raise Blocked('Model compatibility evidence changed')


def usage_totals(path):
    """Roll up invocations; never sum both turn rows and invocation totals."""
    groups, seen = {}, set()
    if not Path(path).exists():
        return {'status': 'NO_RUNS', 'groups': []}
    with Path(path).open() as stream:
        fcntl.flock(stream, fcntl.LOCK_SH)
        for line in stream:
            record = json.loads(line)
            if record['session'] in seen:
                continue
            seen.add(record['session'])
            key = (record['kind'], record['task'], record['role'], record['iteration'],
                   record['selection']['provider'], record['selection']['model'], record['selection']['reasoning_effort'])
            group = groups.setdefault(key, dict(zip(('kind', 'task', 'role', 'iteration', 'provider', 'model', 'reasoning_effort'), key),
                                               calls=0, elapsed_seconds=0, known_input_tokens=0,
                                               known_output_tokens=0, incomplete_calls=0))
            group['calls'] += 1
            group['elapsed_seconds'] += record['elapsed_seconds']
            for field in ('known_input_tokens', 'known_output_tokens'):
                group[field] += record['usage'][field]
            group['incomplete_calls'] += record['usage']['status'] != 'COMPLETE'
    return {'status': 'OBSERVED', 'groups': list(groups.values()),
            'time_semantics': 'Sum of agent durations; parallel durations are not wall-clock batch time',
            'billing': 'Token counters, not provider invoices; preflight costs are included separately'}
