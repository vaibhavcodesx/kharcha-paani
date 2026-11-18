-- STAGED, NOT APPLIED (owner review: new table with an anon read policy). S-06, issue #299.
--
-- What it is for. A server-driven minimum app version, so a bad release or a server contract change
-- can be answered without waiting for people to update. The app reads this one row at the start of
-- each sync pass and caches it. Below `min_version_code` the app stops syncing and tells the person to
-- update; reading and exporting their data keep working (owner answer C-08).
--
-- Who can do what:
--   * anon and authenticated can SELECT the one row. It holds two numbers and nothing private, and it
--     has to be readable before sign-in (the update prompt must reach a signed-out phone too).
--   * Nobody can insert, update or delete through the API. The owner changes it from the dashboard
--     SQL editor (service role), for example: update public.app_config set min_version_code = 9;
--
-- Additive only. Both numbers default to 0 = "no minimum", so applying this changes nothing by itself.
-- Rollback: drop table public.app_config;

begin;

create table public.app_config (
  -- A single-row table: the primary key can only ever be true.
  id boolean primary key default true check (id),
  min_version_code integer not null default 0 check (min_version_code >= 0),
  recommended_version_code integer not null default 0 check (recommended_version_code >= 0),
  updated_at timestamptz not null default now(),
  check (recommended_version_code >= min_version_code)
);

insert into public.app_config (id) values (true);

alter table public.app_config enable row level security;

revoke all on public.app_config from public, anon, authenticated;
grant select on public.app_config to anon, authenticated;

create policy app_config_read on public.app_config
  for select to anon, authenticated
  using (true);

commit;
