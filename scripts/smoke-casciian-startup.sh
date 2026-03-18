#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="${MAGENTA_SMOKE_JAR:-$HOME/.magenta/Magenta2-1.0-SNAPSHOT.jar}"
CONFIG_PATH="${MAGENTA_SMOKE_CONFIG:-$HOME/.magenta/configs/magenta.yaml}"
OUT_DIR="${MAGENTA_SMOKE_OUT_DIR:-$ROOT_DIR/target/smoke}"
RAW_LOG="$OUT_DIR/casciian-startup.raw.log"
CLEAN_LOG="$OUT_DIR/casciian-startup.clean.log"
INPUT_FILE="$OUT_DIR/casciian-startup.input"

mkdir -p "$OUT_DIR"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Smoke failed: jar not found at $JAR_PATH" >&2
  exit 1
fi

if [[ ! -f "$CONFIG_PATH" ]]; then
  echo "Smoke failed: config not found at $CONFIG_PATH" >&2
  exit 1
fi

printf '/exit\r' > "$INPUT_FILE"

if ! python3 - "$JAR_PATH" "$CONFIG_PATH" "$RAW_LOG" "$INPUT_FILE" <<'PY'
import os
import pty
import select
import signal
import subprocess
import sys
import time

jar_path, config_path, raw_log, input_file = sys.argv[1:]
with open(input_file, "rb") as fh:
    scripted_input = fh.read()

try:
    master_fd, slave_fd = pty.openpty()
except OSError as exc:
    print(f"PTY allocation failed: {exc}", file=sys.stderr)
    sys.exit(2)

env = os.environ.copy()
env["TERM"] = env.get("TERM", "xterm")
proc = subprocess.Popen(
    ["java", "-jar", jar_path, config_path],
    stdin=slave_fd,
    stdout=slave_fd,
    stderr=slave_fd,
    env=env,
    close_fds=True,
)
os.close(slave_fd)

captured = bytearray()
sent_input = False
deadline = time.time() + 12.0

try:
    while time.time() < deadline:
        if not sent_input and time.time() + 10.0 >= deadline:
            os.write(master_fd, scripted_input)
            sent_input = True
        ready, _, _ = select.select([master_fd], [], [], 0.2)
        if master_fd in ready:
            try:
                chunk = os.read(master_fd, 65536)
            except OSError:
                break
            if not chunk:
                break
            captured.extend(chunk)
        if proc.poll() is not None and not ready:
            break
        if not sent_input and len(captured) > 0 and time.time() + 9.5 >= deadline:
            os.write(master_fd, scripted_input)
            sent_input = True
    if proc.poll() is None:
        proc.send_signal(signal.SIGTERM)
        try:
            proc.wait(timeout=2.0)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=2.0)
finally:
    os.close(master_fd)

with open(raw_log, "wb") as fh:
    fh.write(captured)
PY
then
  echo "Casciian startup smoke failed before capture. This environment could not allocate a PTY." >&2
  exit 1
fi

perl -pe 's/\e\[[0-9;?]*[ -\/]*[@-~]//g; s/\r/\n/g; s/\e[@-_]//g' "$RAW_LOG" \
  | tr '\0' '\n' \
  | sed '/^[[:space:]]*$/d' \
  > "$CLEAN_LOG"

if rg -n "Exception in thread|Failed to start terminal UI|Chat restore failed|Cannot invoke|\\bERROR\\b" "$CLEAN_LOG" >/dev/null; then
  echo "Casciian startup smoke failed. See:"
  echo "  Raw:   $RAW_LOG"
  echo "  Clean: $CLEAN_LOG"
  rg -n "Exception in thread|Failed to start terminal UI|Chat restore failed|Cannot invoke|\\bERROR\\b" "$CLEAN_LOG" || true
  exit 1
fi

echo "Casciian startup smoke passed."
echo "  Raw:   $RAW_LOG"
echo "  Clean: $CLEAN_LOG"
