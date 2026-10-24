"""POSIX invocation supervision using an exclusive coordinator-owned pipe, not saved PIDs."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time


def launch(argv, *, cwd, env, timeout):
    """Only this process owns the write end; death closes it even on SIGKILL."""
    if os.name != 'posix':
        raise OSError('Invocation supervision requires POSIX process groups')
    read_fd, write_fd = os.pipe()
    try:
        process = subprocess.Popen(
            [sys.executable, str(Path(__file__).absolute()), 'supervise', str(read_fd), str(timeout), *argv],
            cwd=cwd, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, start_new_session=True, pass_fds=(read_fd,))
    except BaseException:
        os.close(write_fd)
        raise
    finally:
        os.close(read_fd)
    process.parent_lease_fd = write_fd
    return process


def close_lease(process):
    descriptor = getattr(process, 'parent_lease_fd', None)
    if descriptor is not None:
        os.close(descriptor)
        process.parent_lease_fd = None
        process.wait(timeout=3)


def group_runner(result_fd, argv):
    """Keep the group leader alive until its supervisor ends the entire group."""
    # Caught handlers reset on exec, so the actual CLI keeps ordinary signal semantics.
    signal.signal(signal.SIGTERM, lambda *_: None)
    signal.signal(signal.SIGINT, lambda *_: None)
    os.set_inheritable(result_fd, False)
    code = 127
    try:
        child = subprocess.Popen(argv, stdin=sys.stdin, stdout=sys.stdout, stderr=sys.stderr, close_fds=True)
        code = child.wait()
    finally:
        os.write(result_fd, (str(code) + '\n').encode())
        os.close(result_fd)
        # A live anchor avoids PID/PGID reuse and macOS empty-zombie-group EPERM.
        # It also keeps ownership when the CLI exits before its descendants.
        while True: signal.pause()


def supervise(read_fd, timeout, argv):
    """A private pipe observes parent death; a live anchor owns the invocation group."""
    stopping = False
    def stop(_signal, _frame):
        nonlocal stopping
        stopping = True
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    os.set_inheritable(read_fd, False)
    deadline = time.monotonic() + timeout
    anchor = None
    result = 125
    result_read, result_write = os.pipe()
    with selectors.DefaultSelector() as selector:
        selector.register(read_fd, selectors.EVENT_READ, 'parent')
        selector.register(result_read, selectors.EVENT_READ, 'result')
        try:
            if stopping or selector.select(0): return 125
            anchor = subprocess.Popen(
                [sys.executable, str(Path(__file__).absolute()), 'group', str(result_write), *argv],
                stdin=sys.stdin, stdout=sys.stdout, stderr=sys.stderr, close_fds=True,
                start_new_session=True, pass_fds=(result_write,))
            os.close(result_write); result_write = None
            while not stopping:
                if time.monotonic() >= deadline:
                    result = 124
                    break
                ready = selector.select(0.1)
                # Parent loss wins over a simultaneous normal completion.
                if any(key.data == 'parent' for key, _ in ready): break
                if any(key.data == 'result' for key, _ in ready):
                    value = os.read(result_read, 64).strip()
                    result = int(value) if value else 125
                    if result < 0: result = 128 - result
                    break
        finally:
            if anchor is not None:
                # No saved-PID lookup: this is our unreaped, live group anchor.
                for sig in (signal.SIGTERM, signal.SIGKILL):
                    try: os.killpg(anchor.pid, sig)
                    except ProcessLookupError: pass
                    if sig == signal.SIGTERM: time.sleep(0.2)
                anchor.wait()
            for descriptor in (read_fd, result_read, result_write):
                if descriptor is not None: os.close(descriptor)
    return result


if __name__ == '__main__':
    if sys.argv[1] == 'group': group_runner(int(sys.argv[2]), sys.argv[3:])
    else: raise SystemExit(supervise(int(sys.argv[2]), float(sys.argv[3]), sys.argv[4:]))
