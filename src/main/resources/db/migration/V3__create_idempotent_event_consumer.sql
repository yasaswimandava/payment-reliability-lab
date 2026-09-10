create table processed_payment_events (
    event_id uuid primary key,
    payment_id uuid not null,
    event_type text not null,
    consumer_name text not null,
    processed_at timestamptz not null
);

create index idx_processed_payment_events_payment
    on processed_payment_events (payment_id, processed_at desc);

create table payment_event_projection (
    payment_id uuid primary key,
    source_event_id uuid not null unique
        references processed_payment_events (event_id),
    merchant_id text not null,
    amount numeric(14, 2) not null check (amount > 0),
    currency varchar(3) not null,
    received_at timestamptz not null,
    projected_at timestamptz not null
);

create index idx_payment_event_projection_merchant
    on payment_event_projection (merchant_id, received_at desc);
