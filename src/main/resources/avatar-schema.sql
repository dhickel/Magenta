create table if not exists avatar_profile (
    id text primary key,
    display_name text not null,
    timezone text,
    locale text,
    summary text,
    created_at text not null,
    updated_at text not null
);

create table if not exists avatar_preferences (
    namespace text not null,
    preference_key text not null,
    value_json text not null,
    updated_at text not null,
    primary key (namespace, preference_key)
);

create table if not exists avatar_dashboard_layout (
    widget_id text primary key,
    widget_position integer not null,
    widget_size text not null,
    enabled integer not null,
    collapsed integer not null,
    settings_json text not null,
    updated_at text not null
);

create table if not exists avatar_todos (
    id text primary key,
    title text not null,
    notes text,
    status text not null,
    priority text not null,
    due_at text,
    linked_project_id text,
    linked_task_id text,
    linked_output_id text,
    created_at text not null,
    updated_at text not null,
    completed_at text
);

create index if not exists idx_avatar_todos_status_due
    on avatar_todos (status, due_at);

create table if not exists avatar_daily_tasks (
    id text primary key,
    task_date text not null,
    title text not null,
    notes text,
    status text not null,
    task_position integer not null,
    created_at text not null,
    updated_at text not null
);

create index if not exists idx_avatar_daily_tasks_date_position
    on avatar_daily_tasks (task_date, task_position);

create table if not exists avatar_calendar_items (
    id text primary key,
    title text not null,
    notes text,
    starts_at text not null,
    ends_at text,
    timezone text,
    location text,
    status text not null,
    created_at text not null,
    updated_at text not null
);

create index if not exists idx_avatar_calendar_items_starts
    on avatar_calendar_items (starts_at);

create table if not exists avatar_notes (
    id text primary key,
    title text not null,
    body text not null,
    tags_json text not null,
    source_ref_json text not null,
    archived integer not null,
    created_at text not null,
    updated_at text not null
);

create index if not exists idx_avatar_notes_archived_updated
    on avatar_notes (archived, updated_at);

create table if not exists avatar_facts (
    namespace text not null,
    fact_key text not null,
    value_json text not null,
    status text not null,
    updated_at text not null,
    primary key (namespace, fact_key)
);

create table if not exists avatar_events (
    id text primary key,
    event_type text not null,
    payload_json text not null,
    occurred_at text not null
);

create index if not exists idx_avatar_events_occurred
    on avatar_events (occurred_at, id);
