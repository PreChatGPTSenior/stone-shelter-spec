"""Bounded audit path policy; inspect names before any protected/secret metadata."""
import os
from pathlib import Path

from .core import safe

DATABASES = ('state.sqlite', 'budgets.sqlite', 'trace.sqlite')
TREES = ('runs', 'model-preflight', 'batches')
LEAVES = ('usage.jsonl', 'policy.json', 'result.json', 'budget-stop.json')
PROTECTED_NAMES = {'engineering-log', '.git'}
SECRET_NAMES = {'secrets', '.secrets', 'credentials', '.credentials', '.ssh',
                'credentials.json', 'credentials.toml', 'auth.json', 'auth.toml',
                'id_rsa', 'id_dsa', 'id_ecdsa', 'id_ed25519'}


def secret_path(parts):
    """A path exclusion, not content redaction or a claim that logs contain no secrets."""
    for part in parts:
        name = part.lower()
        if (name in SECRET_NAMES or name.startswith('.env') or name.endswith('.env')
                or '.env.' in name or name.endswith(('.key', '.pem', '.p12', '.pfx'))):
            return True
    return False


def retained_artifacts(directory):
    """Detect prior execution without reading files or recursively walking worktrees."""
    directory = Path(directory)
    if any(safe(directory, name).exists() for name in LEAVES):
        return True
    for name in DATABASES:
        if not safe(directory, name).exists():
            if any(safe(directory, name + suffix).exists() for suffix in ('-wal', '-shm', '-journal')):
                return True
    for name in TREES + ('worktrees', 'integration'):
        folder = safe(directory, name)
        if not folder.exists():
            continue
        # A malformed non-directory is also retained evidence, never a fresh start.
        if not folder.is_dir():
            return True
        with os.scandir(folder) as entries:
            for entry in entries:
                if entry.name.lower() in PROTECTED_NAMES:
                    continue
                # Even an empty attempt directory or a secret file signals prior work.
                # Do not stat/follow the entry or read its content.
                return True
    return False
