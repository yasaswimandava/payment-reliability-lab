alter table payments
    drop constraint payments_status_check;

alter table payments
    add constraint payments_status_check
        check (status in ('RECEIVED', 'AUTHORIZED', 'DECLINED')),
    add column provider_reference text,
    add column updated_at timestamptz not null default current_timestamp;

create index idx_payments_status_updated_at
    on payments (status, updated_at desc);
