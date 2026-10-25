"""Local durable workflow journal and read-only recovery inspection; never replays tools."""
from contextlib import closing
import json
import os
from pathlib import Path
import sqlite3
import time
import uuid

from .core import Blocked, safe, sha, stable
from .audit_paths import retained_artifacts, secret_path
from .evidence import verify_report,REVIEW_ROLES


def read_rows(path, table):
    if not Path(path).exists():
        return []
    if table not in ('tasks', 'leases', 'calls', 'stops', 'events'):
        raise Blocked('Unsupported audit table')
    with closing(sqlite3.connect(Path(path).absolute().as_uri() + '?mode=ro', uri=True)) as db:
        db.row_factory = sqlite3.Row
        if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
            raise Blocked('SQLite integrity check failed')
        return [dict(row) for row in db.execute('SELECT * FROM ' + table)]


class History:
    def __init__(self, path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.path.touch(mode=0o600, exist_ok=True)
        os.chmod(self.path, 0o600)
        with closing(sqlite3.connect(self.path, timeout=30)) as db:
            db.execute('PRAGMA journal_mode=WAL')
            db.execute('PRAGMA synchronous=FULL')
            db.execute('CREATE TABLE IF NOT EXISTS events (seq INTEGER PRIMARY KEY, event TEXT NOT NULL, '
                       'trace TEXT, task TEXT, session TEXT, role TEXT, body TEXT NOT NULL, digest TEXT NOT NULL)')
            db.execute('CREATE INDEX IF NOT EXISTS task_history ON events(task,seq)')
            db.commit()

    def append(self, event, *, trace=None, task=None, session=None, role=None, payload=None):
        with closing(sqlite3.connect(self.path, timeout=30)) as db:
            db.execute('PRAGMA synchronous=FULL')
            db.execute('BEGIN IMMEDIATE')
            prior = db.execute('SELECT seq,digest FROM events ORDER BY seq DESC LIMIT 1').fetchone()
            seq, previous = (prior[0] + 1, prior[1]) if prior else (1, '0' * 64)
            body = {'seq': seq, 'event_id': uuid.uuid4().hex, 'at_epoch': time.time(),
                    'event': event, 'trace': trace, 'task': task, 'session': session, 'role': role,
                    'payload': payload or {}, 'previous_sha256': previous}
            digest = stable(body)
            db.execute('INSERT INTO events VALUES (?,?,?,?,?,?,?,?)',
                       (seq, event, trace, task, session, role, json.dumps(body), digest))
            db.commit()
            return {**body, 'sha256': digest}


def timeline(path, task=None):
    rows = sorted(read_rows(path, 'events'), key=lambda row: row['seq'])
    previous, result = '0' * 64, []
    for seq, row in enumerate(rows, 1):
        body = json.loads(row['body'])
        if (body.get('seq') != seq or row['seq'] != seq or body.get('previous_sha256') != previous
                or stable(body) != row['digest']
                or any(body.get(k) != row[k] for k in ('event', 'trace', 'task', 'session', 'role'))):
            raise Blocked('History ordering/hash integrity failure')
        previous = row['digest']
        if task is None or body['task'] == task:
            result.append({**body, 'sha256': row['digest']})
    return result


def database_state(directory, events):
    """Cross-check retained evidence before any writer can recreate a lost database."""
    directory = Path(directory)
    state = read_rows(directory / 'state.sqlite', 'tasks')
    leases = read_rows(directory / 'state.sqlite', 'leases')
    calls = read_rows(directory / 'budgets.sqlite', 'calls')
    stops = read_rows(directory / 'budgets.sqlite', 'stops')
    latest, sessions = {}, set()
    for event in events:
        if event['event'] in ('task.claimed', 'task.completed', 'task.blocked', 'task.retry_enabled'):
            latest[event['task']] = event
        if event['event'] in ('invocation.started', 'invocation.finished'):
            sessions.add(event['session'])
    rows = {row['id']: row for row in state}
    gaps = []
    if latest and not (directory / 'state.sqlite').is_file():
        gaps.append('Task history exists without state.sqlite; restore/reconcile, never recreate empty state')
    for task, event in latest.items():
        row = rows.get(task)
        status = {'task.completed': 'DONE', 'task.blocked': 'BLOCKED', 'task.retry_enabled': 'RETRY'}.get(event['event'])
        if (not row or row['owner'] != event['trace'] or (status and row['status'] != status)
                or (status == 'DONE' and row['commit_id'] != event['payload'].get('commit'))):
            gaps.append('Task state disagrees with retained history: ' + task)
    recorded = {row['session'] for row in calls}
    if sessions - recorded:
        gaps.append('Invocation history lacks budget reservations; restore/reconcile accounting')
    if recorded - sessions:
        gaps.append('Budget reservations lack invocation history; reconcile before resuming')
    usage = (directory / 'usage.jsonl').exists()
    if usage and not (directory / 'budgets.sqlite').is_file():
        gaps.append('Usage exists without budgets.sqlite')
    if not events and (state or leases or calls or stops or usage):
        gaps.append('Execution/accounting evidence lacks correlated history')
    if not events and retained_artifacts(directory):
        gaps.append('Retained execution artifacts lack history; preserve and reconcile, never treat as a fresh run')
    return state, leases, calls, stops, gaps


def recovery_status(directory):
    """Inspection only: a stopped process is not proof that its side effects completed."""
    directory = Path(directory)
    events = timeline(directory / 'trace.sqlite')
    pending, integrations, completed = {}, {}, set()
    for event in events:
        if event['event'] == 'invocation.started': pending[event['session']] = event
        elif event['event'] == 'invocation.finished': pending.pop(event['session'], None)
        elif event['event'] == 'integration.intent': integrations[event['trace']] = event
        elif event['event'] == 'integration.completed': integrations.pop(event['trace'], None)
        elif event['event'] == 'task.completed': completed.add((event['task'], event['trace']))
    state, leases, calls, stops, gaps = database_state(directory, events)
    active = [row for row in state if row['status'] in ('CLAIMED', 'DEVELOPING')]
    unsettled = [row for row in calls if row['state'] in ('RESERVED', 'RUNNING', 'UNKNOWN')]
    blockers = list(gaps)
    if active or leases: blockers.append('Task/lease ownership unresolved; confirm old processes stopped, reconcile worktree and integration HEAD')
    if pending: blockers.append('Unfinished invocation; inspect last observed tool event and actual side effects; do not replay automatically')
    if integrations: blockers.append('Integration intent has no completion; compare actual Git HEAD and gate evidence before any retry')
    if any(row['status'] == 'DONE' and (row['id'], row['owner']) not in completed for row in state):
        blockers.append('Task DONE lacks correlated completion; reconcile task database and journal before resuming')
    if unsettled or stops: blockers.append('Budget reconciliation required; reservations and stop latch must not be erased')
    if not (directory / 'trace.sqlite').exists() and (state or calls):
        blockers.append('Legacy state has no correlated history; explicit migration/qualification required')
    return {'status': 'RECOVERY_REQUIRED' if blockers else 'NO_UNRESOLVED_EXECUTION',
            'blockers': blockers, 'tasks': state, 'leases': leases,
            'unfinished_invocations': list(pending.values()), 'unsettled_budgets': unsettled,
            'unfinished_integrations': list(integrations.values()),
            'budget_stops': stops, 'events_verified': len(events),
            'note': 'No process killed, lease released, task replayed or budget reset. Readiness and live admission still required.'}


def verify_evidence(directory, events=None):
    """Validate indexed raw events and terminal receipts; paths remain inside the audit copy."""
    directory = Path(directory)
    events = timeline(directory / 'trace.sqlite') if events is None else events
    gaps = database_state(directory, events)[-1]
    if gaps: raise Blocked('Incomplete audit database set: ' + '; '.join(gaps))
    def artifact_parts(reference):
        parts = Path(reference).parts
        if any(part.lower() == 'engineering-log' for part in parts): raise Blocked('Protected evidence path')
        if '.execution' in parts: parts = parts[parts.index('.execution') + 1:]
        if not parts or parts[0] not in ('runs', 'model-preflight', 'batches'):
            raise Blocked('Evidence is outside the audit directory')
        if secret_path(parts):
            raise Blocked('Required evidence references an excluded secret path; preserve source for reviewed reconciliation')
        if '..' in parts:raise Blocked('Unbounded audit reference')
        return parts
    def artifact(reference):
        return safe(directory,str(Path(*artifact_parts(reference))))
    def verify_hash(reference, expected):
        path = artifact(reference)
        if not path.is_file() or sha(path) != expected:
            raise Blocked('Missing or changed audit evidence: ' + str(path))
    raw = {}
    packets={};cycles={};review_inputs={};review_anchors={}
    for event in events:
        payload = event['payload']
        owner,key=event['trace'],(event['trace'],event['task'])
        if event['event']=='task.claimed' and 'packet' in payload:
            if payload.get('owner')!=owner or payload['packet'].get('id')!=event['task'] or key in packets:
                raise Blocked('Ambiguous or foreign owner task obligations')
            packets[key]=payload['packet']
        if event['event']=='cycle.started':
            parts=artifact_parts(payload.get('cycle',''))
            if key not in packets or parts[:2]!=('runs',owner) or len(parts)<3 or not payload.get('candidate'):
                raise Blocked('Foreign or incomplete cycle identity')
            cycles[key]=(parts,payload['candidate'])
        if event['event'] in ('stage.completed', 'gate.completed'):
            if key not in packets or key not in cycles:raise Blocked('Stage lacks owned task/cycle obligations')
            cycle,candidate=cycles[key]
            expected_leaf=payload.get('stage')+'.json' if event['event']=='stage.completed' and isinstance(payload.get('stage'),str) else 'gate.json'
            if (payload.get('owner')!=owner or artifact_parts(payload.get('cycle',''))!=cycle
                    or payload.get('candidate')!=candidate
                    or artifact_parts(payload.get('artifact',''))!=(*cycle,expected_leaf)):
                raise Blocked('Foreign owner/cycle evidence namespace')
            if 'artifact' not in payload or 'sha256' not in payload:
                raise Blocked('Stage/gate lacks file evidence; explicit requalification required')
            verify_hash(payload['artifact'], payload['sha256'])
            if event['event'] == 'stage.completed':
                report = payload.get('report', {})
                if not report.get('artifact_hashes'):
                    raise Blocked('Stage lacks raw evidence')
                if report.get('stage')!=payload.get('stage'):raise Blocked('Wrong retained stage identity')
                verify_report(safe(directory,str(Path(*cycle))),report,packets[key],candidate,
                    expected_review_contract=review_anchors.get((owner,event['task'],report.get('actor'),report.get('stage')),{}).get('contract'),
                    expected_review_result=review_anchors.get((owner,event['task'],report.get('actor'),report.get('stage')),{}).get('result'))
        if event['event'] == 'cli.event':
            raw.setdefault(artifact(payload['raw_artifact']), {})[payload['line']] = payload['raw_event_sha256']
        if event['event'] in ('invocation.started', 'invocation.finished'):
            review_key=(owner,event['task'],event.get('session'),event.get('role'))
            reviewing=event.get('role') in REVIEW_ROLES and key in cycles
            if event['event']=='invocation.started':
                if reviewing and payload.get('kind')=='agent':
                    cycle,candidate=cycles[key]
                    if artifact_parts(payload.get('input_artifact',''))!=(*cycle,event['role']+'-'+event['session']+'.input.json'):
                        raise Blocked('Foreign review invocation input namespace')
                if 'input_artifact' in payload:verify_hash(payload['input_artifact'],payload['input_sha256'])
                if reviewing and payload.get('kind')=='agent':
                    value=json.loads(artifact(payload['input_artifact']).read_text())
                    context=value.get('context',{})
                    if (context!=payload.get('context') or context.get('attempt_id')!=owner
                            or context.get('candidate')!=candidate or not context.get('review_contract')):
                        raise Blocked('Missing or foreign admitted review context')
                    if review_key in review_inputs:raise Blocked('Ambiguous review invocation input')
                    review_inputs[review_key]=(cycle,candidate,context,payload['input_artifact'])
            else:
                if reviewing:
                    if review_key not in review_inputs:raise Blocked('Missing correlated review invocation input')
                    cycle,candidate,context,input_reference=review_inputs[review_key]
                    if cycles[key]!=(cycle,candidate) or artifact_parts(payload.get('receipt',''))!=(*cycle,event['role']+'-'+event['session']+'.execution.json'):
                        raise Blocked('Foreign review receipt namespace or cycle')
                if 'receipt' in payload:verify_hash(payload['receipt'],payload['sha256'])
                receipt=json.loads(artifact(payload['receipt']).read_text())
                for reference,expected_hash in receipt.get('artifact_hashes',{}).items():verify_hash(reference,expected_hash)
                if reviewing:
                    expected={'session':event['session'],'task':event['task'],'role':event['role'],
                              'trace':owner,'kind':'agent','status':'COMPLETED',
                              'iteration':str(Path(*cycle[1:]))}
                    if (any(receipt.get(k)!=v for k,v in expected.items()) or payload.get('status')!='COMPLETED'
                            or receipt.get('context')!=context or receipt.get('usage',{}).get('status')!='COMPLETE'
                            or artifact_parts(receipt.get('input_artifact',''))!=artifact_parts(input_reference)):
                        raise Blocked('Review receipt differs from verified admission anchor')
                    if review_key in review_anchors:raise Blocked('Ambiguous completed review invocation')
                    raw_reference=receipt.get('raw_result','')
                    if artifact_parts(raw_reference)!=(*cycle,event['role']+'-'+event['session']+'.result.json'):
                        raise Blocked('Foreign emitted reviewer result namespace')
                    hashes={artifact_parts(reference):digest for reference,digest in receipt.get('artifact_hashes',{}).items()}
                    if artifact_parts(raw_reference) not in hashes:raise Blocked('Receipt lacks hashed emitted reviewer result')
                    verify_hash(raw_reference,hashes[artifact_parts(raw_reference)])
                    emitted=json.loads(artifact(raw_reference).read_text())
                    review_anchors[review_key]={'contract':context['review_contract'],'result':emitted}
    for path, expected in raw.items():
        seen = set()
        with path.open() as stream:
            for number, line in enumerate(stream, 1):
                if number in expected:
                    if stable(json.loads(line)) != expected[number]: raise Blocked('Raw event changed')
                    seen.add(number)
        if seen != set(expected): raise Blocked('Raw event stream truncated')
    return {'status': 'VERIFIED', 'events': len(events), 'raw_streams': len(raw)}


def require_recovered(directory):
    report = recovery_status(directory)
    if report['status'] == 'RECOVERY_REQUIRED':
        raise Blocked('Recovery required before live preflight: ' + '; '.join(report['blockers']))
    verify_evidence(directory)
    return report
