-- One row per tool-failure incident with adjacent context message previews.
-- Usage:
--   sqlite3 -header -column ~/.magenta/root/.magenta/state.db < scripts/sql/context-db-incident-windows.sql

WITH incident_tools AS (
  SELECT
    cm.session_id,
    cm.message_id AS tool_message_id,
    cm.tool_name,
    cm.tool_call_id,
    cm.created_at_ms,
    datetime(cm.created_at_ms/1000,'unixepoch','localtime') AS tool_time_local,
    json_extract(cm.content, '$.status') AS tool_status,
    json_extract(cm.content, '$.code') AS tool_code,
    substr(cm.content, 1, 260) AS tool_content_preview
  FROM context_messages cm
  WHERE cm.element_type = 'tool'
    AND json_valid(cm.content) = 1
    AND (
      json_extract(cm.content, '$.status') = 'failed'
      OR json_extract(cm.content, '$.code') IN ('command_timeout', 'db_error', 'command_failed')
    )
), adjacent AS (
  SELECT
    it.*,
    prev.element_type AS prev_type,
    substr(prev.content, 1, 200) AS prev_preview,
    next.element_type AS next_type,
    substr(next.content, 1, 200) AS next_preview
  FROM incident_tools it
  LEFT JOIN context_messages prev
    ON prev.session_id = it.session_id
   AND prev.message_id = it.tool_message_id - 1
  LEFT JOIN context_messages next
    ON next.session_id = it.session_id
   AND next.message_id = it.tool_message_id + 1
)
SELECT
  session_id,
  tool_message_id,
  tool_time_local,
  tool_name,
  tool_status,
  tool_code,
  prev_type,
  prev_preview,
  tool_content_preview,
  next_type,
  next_preview
FROM adjacent
ORDER BY created_at_ms DESC
LIMIT 200;
