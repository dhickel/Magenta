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

create table if not exists avatar_dashboard_rows (
    id text primary key,
    row_position integer not null,
    collapsed integer not null default 0,
    settings_json text not null default '{}',
    updated_at text not null
);

create table if not exists avatar_dashboard_widgets (
    id text primary key,
    row_id text not null,
    widget_key text not null,
    column_position integer not null,
    column_width integer not null,
    enabled integer not null default 1,
    collapsed integer not null default 0,
    settings_json text not null default '{}',
    updated_at text not null,
    unique(widget_key),
    foreign key(row_id) references avatar_dashboard_rows(id) on delete cascade
);

create index if not exists idx_avatar_dashboard_widgets_row
    on avatar_dashboard_widgets(row_id, column_position);

create table if not exists user_dashboards (
    id text primary key,
    dashboard_name text not null,
    dashboard_position integer not null,
    default_dashboard integer not null default 0,
    settings_json text not null default '{}',
    created_at text not null,
    updated_at text not null,
    unique(dashboard_name)
);

create index if not exists idx_user_dashboards_position
    on user_dashboards(dashboard_position, id);

create table if not exists user_dashboard_rows (
    id text primary key,
    dashboard_id text not null,
    row_position integer not null,
    collapsed integer not null default 0,
    settings_json text not null default '{}',
    updated_at text not null,
    foreign key(dashboard_id) references user_dashboards(id) on delete cascade
);

create index if not exists idx_user_dashboard_rows_dashboard
    on user_dashboard_rows(dashboard_id, row_position, id);

create table if not exists user_dashboard_widgets (
    id text primary key,
    dashboard_id text not null,
    row_id text not null,
    widget_key text not null,
    widget_type text not null,
    instance_label text,
    column_position integer not null,
    column_width integer not null,
    enabled integer not null default 1,
    collapsed integer not null default 0,
    settings_json text not null default '{}',
    single_instance_key text,
    created_at text not null,
    updated_at text not null,
    unique(dashboard_id, single_instance_key),
    foreign key(dashboard_id) references user_dashboards(id) on delete cascade,
    foreign key(row_id) references user_dashboard_rows(id) on delete cascade
);

create index if not exists idx_user_dashboard_widgets_row
    on user_dashboard_widgets(row_id, column_position);

create index if not exists idx_user_dashboard_widgets_dashboard_type
    on user_dashboard_widgets(dashboard_id, widget_type);

create index if not exists idx_user_dashboard_widgets_dashboard_row_position
    on user_dashboard_widgets(dashboard_id, row_id, column_position);

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

create table if not exists avatar_planner_tasks (
    id text primary key,
    title text not null,
    notes text,
    status text not null,
    priority text not null,
    starts_at text,
    due_at text,
    timezone text,
    recurrence_json text not null default '{}',
    linked_project_id text,
    linked_assignment_id text,
    linked_job_id text,
    linked_output_id text,
    created_at text not null,
    updated_at text not null,
    completed_at text
);

create index if not exists idx_avatar_planner_tasks_status_due
    on avatar_planner_tasks (status, due_at);

create table if not exists avatar_planner_subtodos (
    id text primary key,
    task_id text not null,
    title text not null,
    status text not null,
    subtodo_position integer not null,
    created_at text not null,
    updated_at text not null,
    foreign key(task_id) references avatar_planner_tasks(id) on delete cascade
);

create index if not exists idx_avatar_planner_subtodos_task
    on avatar_planner_subtodos (task_id, subtodo_position);

create table if not exists avatar_planner_task_notes (
    task_id text not null,
    note_id text not null,
    created_at text not null,
    primary key(task_id, note_id),
    foreign key(task_id) references avatar_planner_tasks(id) on delete cascade,
    foreign key(note_id) references avatar_notes(id) on delete cascade
);

create table if not exists avatar_planner_calendar_projection (
    id text primary key,
    task_id text not null,
    occurrence_start text not null,
    occurrence_end text,
    status text not null,
    created_at text not null,
    updated_at text not null,
    foreign key(task_id) references avatar_planner_tasks(id) on delete cascade
);

create index if not exists idx_avatar_planner_projection_start
    on avatar_planner_calendar_projection (occurrence_start, task_id);

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
