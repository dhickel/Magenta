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
    active_task_run_id text,
    planning_model text,
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
    error_type text,
    stack_trace text,
    recorded_at text not null
);

create unique index if not exists idx_audit_event_conversation
    on audit_event (conversation_id, sequence);

create table if not exists ai_task_definitions (
    id text primary key,
    title text not null,
    summary text,
    goal text,
    notes text,
    input_description text,
    inputs_json text,
    output_description text,
    outputs_json text,
    assumptions_json text,
    steps_json text,
    validation_criteria_json text,
    created_at text not null,
    updated_at text not null
);

create table if not exists ai_task_drafts (
    conversation_id text primary key,
    status text not null,
    planning_task text,
    title text,
    summary text,
    goal text,
    notes text,
    input_description text,
    inputs_json text,
    output_description text,
    outputs_json text,
    assumptions_json text,
    steps_json text,
    validation_criteria_json text,
    pending_questions_json text,
    pending_question_index integer not null default 0,
    pre_planning_model text,
    execution_model text,
    created_task_id text,
    created_at text not null,
    updated_at text not null
);

create table if not exists ai_task_runs (
    id text primary key,
    task_id text not null,
    status text not null,
    input_values_json text,
    output_values_json text,
    task_snapshot_json text not null,
    execution_evidence_json text,
    validation_feedback_json text,
    final_message text,
    error_text text,
    created_at text not null,
    updated_at text not null,
    started_at text,
    completed_at text,
    foreign key (task_id) references ai_task_definitions(id) on delete cascade
);

create index if not exists idx_ai_task_runs_task
    on ai_task_runs (task_id, created_at desc);

create table if not exists ai_workflow_definitions (
    id text primary key,
    title text not null,
    summary text,
    steps_json text not null,
    created_at text not null,
    updated_at text not null
);

create table if not exists ai_workflow_runs (
    id text primary key,
    workflow_id text not null,
    status text not null,
    workflow_snapshot_json text not null,
    step_runs_json text,
    final_outputs_json text,
    final_message text,
    error_text text,
    created_at text not null,
    updated_at text not null,
    started_at text,
    completed_at text,
    foreign key (workflow_id) references ai_workflow_definitions(id) on delete cascade
);

create index if not exists idx_ai_workflow_runs_workflow
    on ai_workflow_runs (workflow_id, created_at desc);

-- Orchestration and agent tables (repository-owned bootstrapping with schema.sql as canonical source)

create table if not exists agent_profiles (
    id text primary key,
    name text not null unique,
    status text not null,
    default_model text,
    system_prompt_text text,
    approved_tool_names_json text not null,
    allowed_shell_commands_json text not null,
    direct_line_enabled integer not null,
    created_at text not null,
    updated_at text not null
);

create table if not exists orchestration_jobs (
    id text primary key,
    owner_agent_id text not null,
    title text not null,
    summary text,
    default_model text,
    workspace_id text,
    status text not null,
    created_at text not null,
    updated_at text not null
);

create table if not exists orchestration_job_items (
    id text primary key,
    job_id text not null,
    item_order integer not null,
    item_type text not null,
    task_id text,
    workflow_id text,
    model_override text,
    priority integer not null,
    retry_count integer not null default 0,
    continue_on_failure integer not null default 0,
    config_json text,
    created_at text not null,
    updated_at text not null,
    foreign key(job_id) references orchestration_jobs(id)
);

create table if not exists work_assignments (
    id text primary key,
    agent_id text not null,
    job_id text,
    job_item_id text,
    assignment_type text not null,
    priority integer not null,
    status text not null,
    model_override text,
    workspace_id text,
    current_item_index integer not null,
    checkpoint_json text,
    input_json text,
    output_json text,
    evidence_json text,
    error_text text,
    lease_owner text,
    lease_expires_at text,
    created_at text not null,
    updated_at text not null,
    started_at text,
    completed_at text
);

create index if not exists idx_work_assignments_queue
    on work_assignments(status, priority, created_at);

create table if not exists agent_inbox_messages (
    id text primary key,
    to_agent_id text not null,
    from_id text,
    message_type text not null,
    body text,
    metadata_json text,
    read_flag integer not null,
    handled_flag integer not null,
    created_at text not null,
    updated_at text not null
);

create table if not exists agent_schedules (
    id text primary key,
    agent_id text not null,
    job_id text,
    assignment_template_json text,
    cron_expression text not null,
    timezone text not null,
    enabled_flag integer not null,
    next_run_at text,
    created_at text not null,
    updated_at text not null
);

create table if not exists schedule_firings (
    id text primary key,
    schedule_id text not null,
    due_at text not null,
    assignment_id text not null,
    created_at text not null,
    unique(schedule_id, due_at),
    foreign key(schedule_id) references agent_schedules(id)
);

create table if not exists agent_event_reactions (
    id text primary key,
    agent_id text not null,
    event_type text not null,
    filter_json text,
    action_type text not null,
    assignment_template_json text,
    enabled_flag integer not null,
    created_at text not null,
    updated_at text not null
);

create table if not exists orchestration_events (
    id text primary key,
    event_type text not null,
    source_type text,
    source_id text,
    payload_json text,
    created_at text not null,
    handled_at text
);

create table if not exists runtime_settings (
    id text primary key,
    default_agent_id text,
    default_agent_name text,
    default_model text,
    planning_model text,
    summary_model text,
    compaction_model text,
    context_buffer_percent integer
);

create table if not exists workspaces (
    id text primary key,
    owner_type text not null,
    owner_id text not null,
    root_relative_path text not null,
    display_name text not null,
    metadata_json text,
    created_at text not null,
    updated_at text not null
);

create unique index if not exists idx_workspaces_owner
    on workspaces(owner_type, owner_id);

create table if not exists workspace_links (
    id text primary key,
    workspace_id text not null,
    label text not null,
    link_type text not null,
    target text not null,
    readable integer not null,
    writable integer not null,
    created_at text not null,
    updated_at text not null,
    foreign key(workspace_id) references workspaces(id)
);
