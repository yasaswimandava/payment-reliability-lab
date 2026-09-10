create table outbox_events (
    event_id uuid primary key,
    aggregate_type text not null check (length(aggregate_type) between 1 and 100),
    aggregate_id uuid not null references payments (id) on delete cascade,
    event_type text not null check (length(event_type) between 1 and 200),
    payload jsonb not null,
    occurred_at timestamptz not null,
    status text not null default 'PENDING'
        check (status in ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),
    available_at timestamptz not null,
    locked_at timestamptz,
    lock_owner text,
    published_at timestamptz,
    attempt_count integer not null default 0 check (attempt_count >= 0),
    last_error text,
    unique (aggregate_type, aggregate_id, event_type)
);

create index idx_outbox_events_pending
    on outbox_events (available_at, occurred_at, event_id)
    where status = 'PENDING';
