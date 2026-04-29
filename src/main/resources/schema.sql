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
    model text
);

create table if not exists ai_chat_plans (
    conversation_id text primary key,
    mode text not null,
    status text not null,
    goal text,
    title text,
    summary text,
    assumptions_json text,
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
