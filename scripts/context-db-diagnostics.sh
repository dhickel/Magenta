#!/usr/bin/env bash
set -euo pipefail

STATE_DB_DEFAULT="${HOME}/.magenta/root/.magenta/state.db"
CONTEXT_DB_DEFAULT="${HOME}/.magenta/configs/.magenta/context.db"
STATE_DB="${STATE_DB_DEFAULT}"
CONTEXT_DB="${CONTEXT_DB_DEFAULT}"
LIMIT="20"

usage() {
  cat <<USAGE
Usage: $(basename "$0") [--state-db PATH] [--context-db PATH] [--limit N]

Quick diagnostics for Magenta session/context stores.
Defaults:
  --state-db   ${STATE_DB_DEFAULT}
  --context-db ${CONTEXT_DB_DEFAULT}
  --limit      20
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --state-db)
      STATE_DB="$2"
      shift 2
      ;;
    --context-db)
      CONTEXT_DB="$2"
      shift 2
      ;;
    --limit)
      LIMIT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: missing required command: $1" >&2
    exit 1
  fi
}

require_cmd sqlite3

if [[ ! -f "${STATE_DB}" ]]; then
  echo "ERROR: state DB not found: ${STATE_DB}" >&2
  exit 1
fi

if [[ ! -f "${CONTEXT_DB}" ]]; then
  echo "WARN: context DB not found: ${CONTEXT_DB}" >&2
fi

echo "== Paths =="
echo "state_db=${STATE_DB}"
echo "context_db=${CONTEXT_DB}"

echo
echo "== state.db tables =="
sqlite3 "${STATE_DB}" '.tables'

if [[ -f "${CONTEXT_DB}" ]]; then
  echo
  echo "== context.db tables =="
  sqlite3 "${CONTEXT_DB}" '.tables'
fi

echo
echo "== Recent sessions =="
sqlite3 -header -column "${STATE_DB}" "
SELECT session_id,
       next_message_id,
       datetime(updated_at_ms/1000,'unixepoch','localtime') AS updated_local
FROM sessions
ORDER BY updated_at_ms DESC
LIMIT ${LIMIT};"

echo
echo "== Largest session contexts =="
sqlite3 -header -column "${STATE_DB}" "
SELECT session_id, COUNT(*) AS message_count
FROM context_messages
GROUP BY session_id
ORDER BY message_count DESC
LIMIT ${LIMIT};"

echo
echo "== Tool error summary =="
sqlite3 -header -column "${STATE_DB}" "
SELECT tool_name,
       json_extract(content,'$.code') AS code,
       COUNT(*) AS count
FROM context_messages
WHERE element_type='tool'
  AND json_valid(content)=1
  AND json_extract(content,'$.status')='failed'
GROUP BY tool_name, code
ORDER BY count DESC
LIMIT ${LIMIT};"

echo
echo "== Timeout-like tool incidents =="
sqlite3 -header -column "${STATE_DB}" "
SELECT session_id,
       message_id,
       tool_name,
       datetime(created_at_ms/1000,'unixepoch','localtime') AS created_local,
       json_extract(content,'$.code') AS code,
       json_extract(content,'$.data.durationMs') AS duration_ms,
       json_extract(content,'$.data.timedOut') AS timed_out
FROM context_messages
WHERE element_type='tool'
  AND json_valid(content)=1
  AND (
    lower(content) LIKE '%timeout%'
    OR json_extract(content,'$.code') IN ('command_timeout','db_error')
  )
ORDER BY created_at_ms DESC
LIMIT ${LIMIT};"

echo
echo "== Repeated write_file validation failures =="
sqlite3 -header -column "${STATE_DB}" "
SELECT session_id,
       COUNT(*) AS failures
FROM context_messages
WHERE element_type='tool'
  AND tool_name='write_file'
  AND json_valid(content)=1
  AND json_extract(content,'$.status')='failed'
  AND json_extract(content,'$.code')='validation_error'
GROUP BY session_id
ORDER BY failures DESC
LIMIT ${LIMIT};"

echo
echo "== Assistant empty-output count by session =="
sqlite3 -header -column "${STATE_DB}" "
SELECT session_id,
       COUNT(*) AS empty_assistant_messages
FROM context_messages
WHERE element_type='assistant'
  AND length(content)=0
GROUP BY session_id
ORDER BY empty_assistant_messages DESC
LIMIT ${LIMIT};"

if [[ -f "${CONTEXT_DB}" ]]; then
  echo
  echo "== context.db empty_model_turn rows =="
  sqlite3 -header -column "${CONTEXT_DB}" "
SELECT session_id,
       COUNT(*) AS empty_model_turn_count
FROM session_messages
WHERE role='system'
  AND payload_json LIKE '%empty_model_turn%'
GROUP BY session_id
ORDER BY empty_model_turn_count DESC
LIMIT ${LIMIT};"
fi

echo
echo "Diagnostics complete."
