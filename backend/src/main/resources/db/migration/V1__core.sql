create extension if not exists pg_trgm;

create table department (
  id               uuid primary key,
  name             varchar(100) not null,
  head_employee_id uuid,
  created_at       timestamptz not null,
  updated_at       timestamptz not null
);
create unique index ux_department_name on department (lower(name));

create table location (
  id         uuid primary key,
  name       varchar(100) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create unique index ux_location_name on location (lower(name));

create table employee (
  id                             uuid primary key,
  employee_number                varchar(20)  not null,
  full_name                      varchar(150) not null,
  work_email                     varchar(254) not null,
  phone                          varchar(16),
  job_title                      varchar(100) not null,
  department_id                  uuid not null references department (id),
  location_id                    uuid references location (id),
  manager_id                     uuid references employee (id),
  employment_type                varchar(20) not null
    check (employment_type in ('FULL_TIME', 'PART_TIME', 'CONTRACTOR', 'INTERN')),
  status                         varchar(20) not null
    check (status in ('ACTIVE', 'ON_LEAVE', 'TERMINATED')),
  start_date                     date not null,
  end_date                       date,
  date_of_birth                  date,
  home_address                   varchar(500),
  emergency_contact_name         varchar(150),
  emergency_contact_relationship varchar(50),
  emergency_contact_phone        varchar(16),
  notes                          varchar(2000),
  version                        bigint not null default 0,
  created_at                     timestamptz not null,
  updated_at                     timestamptz not null,
  constraint ck_employee_end_date_iff_terminated check ((status = 'TERMINATED') = (end_date is not null)),
  constraint ck_employee_end_after_start check (end_date is null or end_date >= start_date),
  constraint ck_employee_not_own_manager check (manager_id is null or manager_id <> id)
);
create unique index ux_employee_number on employee (lower(employee_number));
create unique index ux_employee_email on employee (lower(work_email));
create index ix_employee_department on employee (department_id);
create index ix_employee_location on employee (location_id);
create index ix_employee_manager on employee (manager_id);
create index ix_employee_status on employee (status);
create index ix_employee_name_trgm on employee using gin (full_name gin_trgm_ops);
create index ix_employee_number_trgm on employee using gin (employee_number gin_trgm_ops);
create index ix_employee_email_trgm on employee using gin (work_email gin_trgm_ops);

alter table department
  add constraint fk_department_head foreign key (head_employee_id) references employee (id);

create table user_account (
  id                   uuid primary key,
  username             varchar(254) not null,
  password_hash        varchar(100) not null,
  role                 varchar(20) not null check (role in ('ADMIN', 'EMPLOYEE')),
  employee_id          uuid references employee (id),
  enabled              boolean not null default true,
  must_change_password boolean not null default false,
  failed_attempts      integer not null default 0,
  locked_until         timestamptz,
  password_changed_at  timestamptz not null,
  last_login_at        timestamptz,
  created_at           timestamptz not null,
  updated_at           timestamptz not null,
  constraint ck_user_employee_role check (role <> 'EMPLOYEE' or employee_id is not null)
);
create unique index ux_user_username on user_account (lower(username));
create unique index ux_user_employee on user_account (employee_id);

create table password_reset_token (
  id         uuid primary key,
  user_id    uuid not null references user_account (id),
  token_hash varchar(64) not null,
  expires_at timestamptz not null,
  used_at    timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create unique index ux_reset_token_hash on password_reset_token (token_hash);
create index ix_reset_token_user on password_reset_token (user_id);

create table employee_change (
  id          uuid primary key,
  employee_id uuid not null references employee (id),
  change_type varchar(20) not null
    check (change_type in ('CREATED', 'UPDATED', 'STATUS_CHANGED', 'REACTIVATED', 'SELF_UPDATED')),
  field_name  varchar(50),
  old_value   varchar(2000),
  new_value   varchar(2000),
  changed_by  uuid not null references user_account (id),
  changed_at  timestamptz not null
);
create index ix_employee_change_employee on employee_change (employee_id, changed_at desc);
