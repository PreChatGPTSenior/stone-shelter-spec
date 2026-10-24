"""Persistent token admission and cancellation. No network or model calls.

Reservations are conservative allowances, not provider-enforced token caps.
Reported usage can arrive after a turn has already exceeded its allowance.
"""
from contextlib import closing, contextmanager
from copy import deepcopy
import os
from pathlib import Path
import sqlite3
import time
import uuid

from .core import ROOT, Blocked, json_read, stable

POLICY_PATH = ROOT / 'execution/agent-budgets.json'
POOLS = ('codex', 'openrouter')


def validate(policy):
    if (not isinstance(policy, dict) or set(policy) != {'schema', 'unit', 'pools'}
            or type(policy['schema']) is not int or policy['schema'] != 1
            or policy['unit'] != 'input_plus_output_tokens'
            or not isinstance(policy['pools'], dict) or set(policy['pools']) != set(POOLS)):
        raise Blocked('Invalid budget policy; explicit Codex and OpenRouter token pools required')
    for settings in policy['pools'].values():
        if (not isinstance(settings, dict)
                or set(settings) != {'limit_tokens', 'invocation_allowance_tokens'}
                or any(type(v) is not int or not 0 <= v <= 2**53 for v in settings.values())
                or settings['invocation_allowance_tokens'] == 0
                or (settings['limit_tokens'] > 0
                    and settings['invocation_allowance_tokens'] > settings['limit_tokens'])):
            raise Blocked('Invalid token limit or invocation allowance')
    return policy


def load():
    return validate(json_read(POLICY_PATH))


def pool_for(selection):
    provider = selection['provider_config']
    if provider['kind'] == 'builtin' and provider.get('provider_id') == 'openai':
        return 'codex'
    if provider['kind'] == 'responses' and provider.get('base_url') == 'https://openrouter.ai/api/v1':
        return 'openrouter'
    raise Blocked('Provider has no approved budget pool; arbitrary routers need explicit accounting support')


def summarize(policy, rows, stop):
    pools = {}
    for name, settings in policy['pools'].items():
        own = [r for r in rows if r['pool'] == name]
        observed = sum(r['tokens'] for r in own)
        charged = sum(max(r['tokens'], r['allowance']) if r['state'] in
                      ('RESERVED', 'RUNNING', 'UNKNOWN') else r['tokens'] for r in own)
        pools[name] = {**settings, 'observed_tokens': observed, 'charged_tokens': charged,
                       'available_tokens': max(0, settings['limit_tokens'] - charged),
                       'unsettled_calls': sum(r['state'] in ('RESERVED', 'RUNNING', 'UNKNOWN') for r in own)}
    return {'unit': policy['unit'], 'stop_reason': stop, 'pools': pools,
            'scope': 'Project lifetime; retries and preset changes do not reset consumption',
            'billing': 'Observed tokens, not currency or remaining account quota'}


def snapshot(path, policy):
    """Read-only CLI status; does not create a database or require credentials."""
    if not Path(path).exists():
        return {'status': 'NOT_STARTED', **summarize(policy, [], None)}
    with closing(sqlite3.connect(Path(path).absolute().as_uri() + '?mode=ro', uri=True)) as db:
        db.row_factory = sqlite3.Row
        db.execute('BEGIN')
        rows = [dict(row) for row in db.execute('SELECT * FROM calls')]
        stop = db.execute("SELECT reason FROM stops WHERE id=1").fetchone()
        return {'status': 'STOPPED' if stop else 'RECORDED',
                **summarize(policy, rows, stop[0] if stop else None)}


class Budgets:
    def __init__(self, path, policy, policy_path=None):
        self.path, self.policy = Path(path), deepcopy(validate(policy))
        self.policy_path, self.owner = policy_path, uuid.uuid4().hex
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        # Fail closed if accounting was removed while invocation evidence survives.
        if not self.path.exists() and (self.path.parent / 'usage.jsonl').exists():
            raise Blocked('Usage ledger exists without budget accounting; reconcile before execution')
        self.path.touch(mode=0o600, exist_ok=True)
        os.chmod(self.path, 0o600)
        with self.transaction() as db:
            db.execute('CREATE TABLE IF NOT EXISTS calls (session TEXT PRIMARY KEY, pool TEXT NOT NULL, '
                       'task TEXT NOT NULL, role TEXT NOT NULL, kind TEXT NOT NULL, owner TEXT NOT NULL, '
                       'allowance INTEGER NOT NULL, tokens INTEGER NOT NULL, state TEXT NOT NULL, updated REAL NOT NULL)')
            db.execute('CREATE TABLE IF NOT EXISTS stops (id INTEGER PRIMARY KEY CHECK(id=1), reason TEXT NOT NULL)')
            if db.execute("SELECT 1 FROM calls WHERE state IN ('RESERVED','RUNNING','UNKNOWN') LIMIT 1").fetchone():
                self.stop(db, 'Unsettled prior invocation; explicit accounting reconciliation required')
            for row in db.execute('SELECT pool,SUM(tokens) AS spent FROM calls GROUP BY pool'):
                if row['spent'] > 0 and row['spent'] >= self.policy['pools'][row['pool']]['limit_tokens']:
                    self.stop(db, row['pool'] + ' historical consumption reaches configured limit')

    @contextmanager
    def transaction(self):
        db = sqlite3.connect(self.path, timeout=30)
        db.row_factory = sqlite3.Row
        try:
            db.execute('BEGIN IMMEDIATE')
            yield db
            db.commit()
        except BaseException:
            db.rollback()
            raise
        finally:
            db.close()

    @staticmethod
    def stop(db, reason):
        db.execute('INSERT OR IGNORE INTO stops VALUES (1,?)', (reason,))

    def check_stop(self):
        if self.policy_path is not None and stable(validate(json_read(self.policy_path))) != stable(self.policy):
            with self.transaction() as db:
                self.stop(db, 'Budget policy changed during execution; stop and requalify')
        with self.transaction() as db:
            row = db.execute('SELECT reason FROM stops WHERE id=1').fetchone()
            if row:
                raise Blocked('Budget stopped: ' + row[0])

    def guard(self, selections=()):
        """Check before features/stages; actual calls acquire atomic reservations."""
        self.check_stop()
        state = snapshot(self.path, self.policy)
        for pool in {pool_for(s) for s in selections}:
            settings = state['pools'][pool]
            if settings['available_tokens'] < settings['invocation_allowance_tokens']:
                raise Blocked('Insufficient ' + pool + ' budget for a model invocation')

    def reserve(self, session, selection, task, role, kind):
        self.check_stop()
        pool = pool_for(selection)
        with self.transaction() as db:
            if db.execute('SELECT 1 FROM stops').fetchone():
                raise Blocked('Budget stopped before reservation')
            rows = [dict(row) for row in db.execute('SELECT * FROM calls')]
            settings = summarize(self.policy, rows, None)['pools'][pool]
            allowance = settings['invocation_allowance_tokens']
            if settings['available_tokens'] < allowance:
                raise Blocked('Insufficient ' + pool + ' budget; no model process started')
            db.execute('INSERT INTO calls VALUES (?,?,?,?,?,?,?,?,?,?)',
                       (session, pool, task, role, kind, self.owner, allowance, 0, 'RESERVED', time.time()))
        return {'pool': pool, 'reservation_tokens': allowance, 'policy_sha256': stable(self.policy)}

    def observe(self, session, metrics, final=False, not_started=False):
        """Persist each terminal-turn observation, even when it exceeds the reservation."""
        with self.transaction() as db:
            row = db.execute('SELECT * FROM calls WHERE session=?', (session,)).fetchone()
            if row is None or row['owner'] != self.owner or row['state'] in ('SETTLED', 'NOT_STARTED'):
                raise Blocked('Missing, foreign or settled budget reservation')
            tokens = metrics['known_input_tokens'] + metrics['known_output_tokens']
            complete = metrics['status'] == 'COMPLETE'
            if not_started:
                tokens, state = 0, 'NOT_STARTED'
            else:
                tokens = max(row['tokens'], tokens)
                state = ('SETTLED' if final else 'RUNNING') if complete else 'UNKNOWN'
                if not complete:
                    self.stop(db, 'Incomplete model usage; reservation retained for reconciliation')
                if tokens >= row['allowance']:
                    self.stop(db, 'Invocation token allowance reached: ' + session)
            db.execute('UPDATE calls SET tokens=?,state=?,updated=? WHERE session=?',
                       (tokens, state, time.time(), session))
            observed = db.execute('SELECT SUM(tokens) FROM calls WHERE pool=?', (row['pool'],)).fetchone()[0]
            if observed >= self.policy['pools'][row['pool']]['limit_tokens'] and not not_started:
                self.stop(db, row['pool'] + ' token limit reached')
        return self.receipt(session)

    def receipt(self, session):
        with self.transaction() as db:
            row = db.execute('SELECT * FROM calls WHERE session=?', (session,)).fetchone()
            stop = db.execute('SELECT reason FROM stops WHERE id=1').fetchone()
            return {'reservation': dict(row) if row else None, 'stop_reason': stop[0] if stop else None,
                    'policy_sha256': stable(self.policy)}
