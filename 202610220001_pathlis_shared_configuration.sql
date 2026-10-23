-- PathLIS additive migration. Review against the live schema before applying.
-- Does not drop or alter existing patient/report tables.

create table if not exists public.lab_settings (
  lab_id uuid primary key,
  lab_name text not null default 'Maa PathLab',
  lab_address text not null default '',
  tech_name text not null default '',
  tech_designation text not null default 'Lab Technician',
  path_name text not null default '',
  path_designation text not null default 'Pathologist',
  config_version bigint not null default 1,
  updated_by uuid,
  updated_at timestamptz not null default now()
);

create table if not exists public.lab_tests (
  id uuid primary key default gen_random_uuid(),
  lab_id uuid not null,
  test_name text not null,
  sort_order integer not null default 0,
  is_active boolean not null default true,
  updated_by uuid,
  updated_at timestamptz not null default now(),
  unique (lab_id, test_name)
);

create table if not exists public.lab_test_parameters (
  id uuid primary key default gen_random_uuid(),
  lab_id uuid not null,
  test_id uuid not null references public.lab_tests(id) on delete cascade,
  parameter_name text not null,
  unit text not null default '',
  reference_low numeric,
  reference_high numeric,
  age_sex_ranges jsonb not null default '[]'::jsonb,
  sort_order integer not null default 0,
  is_active boolean not null default true,
  updated_by uuid,
  updated_at timestamptz not null default now(),
  unique (test_id, parameter_name)
);

create table if not exists public.lab_activity (
  id bigint generated always as identity primary key,
  lab_id uuid not null,
  actor_id uuid not null,
  action text not null,
  target_type text not null default '',
  target_id text,
  created_at timestamptz not null default now()
);

-- Enforce that a parameter's parent test belongs to the same laboratory.
-- This is additive and preserves existing records; migration stops if legacy rows violate it.
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'lab_tests_id_lab_unique') then
    alter table public.lab_tests add constraint lab_tests_id_lab_unique unique (id, lab_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'lab_parameters_test_same_lab_fk') then
    alter table public.lab_test_parameters add constraint lab_parameters_test_same_lab_fk
      foreign key (test_id, lab_id) references public.lab_tests(id, lab_id) on delete cascade;
  end if;
end $$;

create index if not exists lab_tests_lab_order_idx on public.lab_tests(lab_id, sort_order);
create index if not exists lab_parameters_lab_test_order_idx on public.lab_test_parameters(lab_id, test_id, sort_order);
create index if not exists lab_activity_lab_created_idx on public.lab_activity(lab_id, created_at desc);
create index if not exists lab_activity_actor_created_idx on public.lab_activity(actor_id, created_at desc);

create or replace function public.pathlis_touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  new.config_version = coalesce(old.config_version, 0) + 1;
  return new;
end;
$$;

drop trigger if exists lab_settings_touch_updated_at on public.lab_settings;
create trigger lab_settings_touch_updated_at before update on public.lab_settings
for each row execute function public.pathlis_touch_updated_at();

create or replace function public.pathlis_touch_config_row()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;
drop trigger if exists lab_tests_touch_updated_at on public.lab_tests;
create trigger lab_tests_touch_updated_at before update on public.lab_tests
for each row execute function public.pathlis_touch_config_row();
drop trigger if exists lab_parameters_touch_updated_at on public.lab_test_parameters;
create trigger lab_parameters_touch_updated_at before update on public.lab_test_parameters
for each row execute function public.pathlis_touch_config_row();

alter table public.lab_settings enable row level security;
alter table public.lab_tests enable row level security;
alter table public.lab_test_parameters enable row level security;
alter table public.lab_activity enable row level security;

-- Assumes existing public.profiles(id, lab_id, role), as used by the current Android login.
-- Users can read only configuration belonging to their own assigned lab.
drop policy if exists lab_settings_read_same_lab on public.lab_settings;
create policy lab_settings_read_same_lab on public.lab_settings for select to authenticated
using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.lab_id = lab_settings.lab_id));
drop policy if exists lab_settings_admin_write on public.lab_settings;
create policy lab_settings_admin_write on public.lab_settings for all to authenticated
using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.lab_id = lab_settings.lab_id and lower(p.role) = 'admin'))
with check (exists (select 1 from public.profiles p where p.id = auth.uid() and p.lab_id = lab_settings.lab_id and lower(p.role) = 'admin'));

drop policy if exists lab_tests_read_same_lab on public.lab_tests;
create policy lab_tests_read_same_lab on public.lab_tests for select to authenticated
using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.lab_id = lab_tests.lab_id));
drop policy if exists lab_tests_admin_write on public.lab_tests;
create policy lab_tests_admin_write on public.lab_tests for all to authenticated
using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.lab_id = lab_tests.lab_id and lower(p.role) = 'admin'))
with check (exists (select 1 from public.profiles p where p.id = auth.uid() and p.lab_id = lab_tests.lab_id and lower(p.role) = 'admin'));

drop policy if exists lab_parameters_read_same_lab on public.lab_test_parameters;
create policy lab_parameters_read_same_lab on public.lab_test_parameters for select to authenticated
using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.lab_id = lab_test_parameters.lab_id));
drop policy if exists lab_parameters_admin_write on public.lab_test_parameters;
create policy lab_parameters_admin_write on public.lab_test_parameters for all to authenticated
using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.lab_id = lab_test_parameters.lab_id and lower(p.role) = 'admin'))
with check (exists (select 1 from public.profiles p where p.id = auth.uid() and p.lab_id = lab_test_parameters.lab_id and lower(p.role) = 'admin'));

drop policy if exists lab_activity_admin_read on public.lab_activity;
create policy lab_activity_admin_read on public.lab_activity for select to authenticated
using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.lab_id = lab_activity.lab_id and lower(p.role) = 'admin'));
drop policy if exists lab_activity_same_lab_insert on public.lab_activity;
create policy lab_activity_same_lab_insert on public.lab_activity for insert to authenticated
with check (actor_id = auth.uid() and exists (select 1 from public.profiles p where p.id = auth.uid() and p.lab_id = lab_activity.lab_id));

-- No anonymous policies are created. No service-role key is used by this migration.
