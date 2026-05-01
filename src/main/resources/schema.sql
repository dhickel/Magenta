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
    title text
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
    goal text,
    title text,
    summary text,
    notes text,
    assumptions_json text,
    acceptance_criteria_json text,
    execution_evidence_json text,
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
