create table if not exists ai_chat_memory (
    conversation_id text not null,
    message_order integer not null,
    message_type text not null,
    message_text text,
    message_metadata_json text,
    primary key (conversation_id, message_order)
);

create index if not exists idx_ai_chat_memory_conversation
    on ai_chat_memory (conversation_id);

create table if not exists ai_chat_session_metadata (
    conversation_id text primary key,
    model text,
    title text,
    favorite integer not null default 0,
    archived integer not null default 0,
    updated_at text
);

create table if not exists agent_jobs (
    id text primary key,
    type text not null,
    status text not null,
    conversation_id text,
    selected_model text,
    input_json text,
    result_json text,
    error_text text,
    created_at text not null,
    updated_at text not null,
    started_at text,
    completed_at text
);

create index if not exists idx_agent_jobs_conversation
    on agent_jobs (conversation_id);

create unique index if not exists idx_agent_jobs_conversation_title_active
    on agent_jobs (type, conversation_id)
    where type = 'CONVERSATION_TITLE'
      and status in ('QUEUED', 'RUNNING', 'SUCCEEDED');

create table if not exists ai_chat_plans (
    conversation_id text primary key,
    mode text not null,
    status text not null,
    planning_task text,
    goal text,
    title text,
    summary text,
    notes text,
    deliverables_json text,
    inputs_json text,
    outputs_json text,
    assumptions_json text,
    acceptance_criteria_json text,
    execution_evidence_json text,
    validation_feedback_json text,
    pre_planning_model text,
    pending_questions_json text,
    pending_question_index integer not null default 0,
    plan_start_message_order integer not null,
    created_at text not null,
    updated_at text not null
);

create table if not exists ai_chat_plan_steps (
    conversation_id text not null,
    step_order integer not null,
    step_text text not null,
    primary key (conversation_id, step_order),
    foreign key (conversation_id) references ai_chat_plans(conversation_id) on delete cascade
);

-- Immutable append-only event log. Each row is a discrete, known event in the
-- chat lifecycle, recorded explicitly at the point it occurs. Never deduped, never
-- derived from ai_chat_memory state — we control the flow and know when each event happens.
create table if not exists audit_event (
    id integer primary key autoincrement,
    conversation_id text not null,
    sequence integer not null,
    event_type text not null,
    -- message content (user_msg, assistant_msg)
    message_text text,
    message_metadata_json text,
    model text,
    -- tool execution (tool_exec)
    tool_call_id text,
    tool_name text,
    arguments_json text,
    arguments_summary text,
    call_preview text,
    result_text text,
    result_summary text,
    result_preview text,
    tool_status text,
    result_truncated integer default 0,
    result_large integer default 0,
    -- compaction (compaction)
    compaction_method text,
    compaction_summary text,
    -- context snapshot (context, compaction)
    used_tokens integer,
    max_tokens integer,
    trigger_tokens integer,
    percent_used real,
    stored_message_count integer,
    recorded_at text not null
);

create index if not exists idx_audit_event_conversation
    on audit_event (conversation_id, sequence);
