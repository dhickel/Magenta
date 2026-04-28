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
