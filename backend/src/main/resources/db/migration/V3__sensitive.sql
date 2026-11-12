create table employee_sensitive (
  id                  uuid primary key,
  employee_id         uuid not null references employee (id),
  gov_id_type         varchar(50),
  gov_id_number       bytea,
  gov_id_last4        varchar(4),
  tax_id              bytea,
  tax_id_last4        varchar(4),
  bank_account_holder bytea,
  bank_name           varchar(150),
  bank_account_number bytea,
  bank_account_last4  varchar(4),
  bank_routing_code   bytea,
  bank_routing_last4  varchar(4),
  key_id              varchar(40) not null,
  version             bigint not null default 0,
  created_at          timestamptz not null,
  updated_at          timestamptz not null
);
create unique index ux_employee_sensitive_employee on employee_sensitive (employee_id);

create table compensation_record (
  id             uuid primary key,
  employee_id    uuid not null references employee (id),
  amount         bytea not null,
  currency       varchar(3) not null,
  pay_frequency  varchar(10) not null check (pay_frequency in ('ANNUAL', 'MONTHLY', 'HOURLY')),
  effective_date date not null,
  reason         varchar(200),
  created_by     uuid not null references user_account (id),
  created_at     timestamptz not null,
  updated_at     timestamptz not null
);
create unique index ux_compensation_effective on compensation_record (employee_id, effective_date);

create table sensitive_access_log (
  id          uuid primary key,
  actor_id    uuid not null references user_account (id),
  employee_id uuid not null references employee (id),
  field       varchar(30) not null,
  action      varchar(10) not null check (action in ('REVEAL', 'CREATE', 'UPDATE')),
  occurred_at timestamptz not null
);
create index ix_access_log_employee on sensitive_access_log (employee_id, occurred_at desc);
create index ix_access_log_actor on sensitive_access_log (actor_id, occurred_at desc);
