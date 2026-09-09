create table payments (
    id uuid primary key,
    merchant_id text not null check (length(merchant_id) between 1 and 100),
    amount numeric(19, 2) not null check (amount > 0),
    currency char(3) not null check (currency ~ '^[A-Z]{3}$'),
    status text not null check (status in ('RECEIVED')),
    created_at timestamptz not null
);

create index idx_payments_merchant_created_at
    on payments (merchant_id, created_at desc);

create table idempotency_keys (
    merchant_id text not null check (length(merchant_id) between 1 and 100),
    idempotency_key text not null check (length(idempotency_key) between 1 and 200),
    request_fingerprint char(64) not null,
    payment_id uuid not null unique references payments (id) on delete cascade,
    created_at timestamptz not null default current_timestamp,
    primary key (merchant_id, idempotency_key)
);
