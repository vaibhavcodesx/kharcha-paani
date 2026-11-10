-- NOT APPLIED. Staged for owner review: this is the first thing a signed-out caller can run.
-- Design: docs/designs/split-by-items.md. Tests: supabase/tests/bill_item_splits_test.sql.
--
-- Split a bill by items. One person (the host, signed in) photographs a bill; the app lists its
-- items. They share a link, and everyone at the table opens it in a browser, no app and no account,
-- and ticks what they had. The host sees the picks arrive and saves the split as an ordinary expense.
--
-- What is kept here, and for how long: the bill's items and prices, its total, the host's first name,
-- and the first name each person typed. No account ids of guests, no phone numbers, no photo. A split
-- can be opened for 24 hours; it is deleted when the host finishes it, and otherwise the next time
-- anyone starts a split after it has expired.
--
-- Who can do what:
--   * Nobody reads or writes the tables directly: they're in `private`, which the API doesn't expose.
--   * The host (signed in) creates, corrects and finishes their own splits: create_bill_split,
--     update_bill_split, remove_bill_split_person, close_bill_split.
--   * Anyone holding the split's link (a 32-hex-character token, 122 random bits) can see that one
--     split, add a name to it and tick items for anyone at the table except the host:
--     bill_split_view, bill_split_join, bill_split_claim, bill_split_done. The link is the table:
--     "I'll tick for Priya, her phone is dead" is meant to work. The host's own picks need the host's
--     session. Nothing a link-holder does reaches the host's ledger: the host reviews the picks and
--     saves the expense themself.
--   * Those four are granted to `anon`, which until now could run nothing (20261025000300). They take
--     the token on every call, touch only that split's rows, and are capped: 30 people and 100 items
--     a split, names of 40 characters, 10 open splits a host.
--
-- Additive only. Rollback: drop the eight public functions below, then
-- drop table private.bill_split_claims, private.bill_split_people, private.bill_split_items,
-- private.bill_splits; drop function private.bill_split_state(uuid), private.clean_bill_name(text).

begin;

create table private.bill_splits (
  id uuid primary key,
  host_id uuid not null references auth.users (id) on delete cascade,
  token text not null unique check (token ~ '^[0-9a-f]{32}$'),
  title text not null default '' check (char_length(title) <= 120),
  host_name text not null check (char_length(host_name) between 1 and 40),
  -- Paise, so nothing here is ever a float. The ceiling is numeric(12,2)'s, as for transactions.
  total_paise bigint not null check (total_paise between 0 and 999999999999),
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null
);
create index bill_splits_host_idx on private.bill_splits (host_id);
create index bill_splits_expires_idx on private.bill_splits (expires_at);

create table private.bill_split_items (
  id uuid primary key,
  split_id uuid not null references private.bill_splits (id) on delete cascade,
  position integer not null check (position >= 0),
  name text not null check (char_length(name) between 1 and 80),
  quantity integer not null default 1 check (quantity between 1 and 99),
  amount_paise bigint not null check (amount_paise between 0 and 999999999999),
  unique (split_id, id)
);

create table private.bill_split_people (
  id uuid primary key,
  split_id uuid not null references private.bill_splits (id) on delete cascade,
  -- Join order, which is also the order leftover paise are handed out in on every screen.
  seq bigint generated always as identity,
  name text not null check (char_length(name) between 1 and 40),
  is_host boolean not null default false,
  done boolean not null default false,
  unique (split_id, id)
);
create index bill_split_people_split_idx on private.bill_split_people (split_id);
create unique index bill_split_people_one_host on private.bill_split_people (split_id) where is_host;

create table private.bill_split_claims (
  split_id uuid not null,
  item_id uuid not null,
  person_id uuid not null,
  -- How many parts of the item this person had: two of the three naan is weight 2.
  weight smallint not null default 1 check (weight between 1 and 20),
  primary key (item_id, person_id),
  -- Both in the same split: a claim can't tie one split's item to another split's person.
  foreign key (split_id, item_id) references private.bill_split_items (split_id, id) on delete cascade,
  foreign key (split_id, person_id) references private.bill_split_people (split_id, id) on delete cascade
);
create index bill_split_claims_split_idx on private.bill_split_claims (split_id);
create index bill_split_claims_person_idx on private.bill_split_claims (split_id, person_id);

revoke all on private.bill_splits, private.bill_split_items, private.bill_split_people, private.bill_split_claims
  from public, anon, authenticated;

-- A name as typed, made safe to show: no control characters, single spaces, at most 40 characters.
create function private.clean_bill_name(p_name text)
returns text
language sql
immutable
set search_path = ''
as $$
  select left(btrim(regexp_replace(regexp_replace(coalesce(p_name, ''), '[[:cntrl:]]', '', 'g'), '\s+', ' ', 'g')), 40);
$$;

-- The whole split as the app and the page read it.
create function private.bill_split_state(p_split_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', s.id,
    'title', s.title,
    'host_name', s.host_name,
    'total_paise', s.total_paise,
    'version', s.version,
    'expires_at', s.expires_at,
    'items', coalesce((
      select jsonb_agg(jsonb_build_object('id', i.id, 'name', i.name, 'quantity', i.quantity, 'amount_paise', i.amount_paise)
                       order by i.position, i.id)
      from private.bill_split_items i where i.split_id = s.id), '[]'::jsonb),
    'people', coalesce((
      select jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name, 'is_host', p.is_host, 'done', p.done) order by p.seq)
      from private.bill_split_people p where p.split_id = s.id), '[]'::jsonb),
    'claims', coalesce((
      select jsonb_agg(jsonb_build_object('item_id', c.item_id, 'person_id', c.person_id, 'weight', c.weight)
                       order by c.item_id, c.person_id)
      from private.bill_split_claims c where c.split_id = s.id), '[]'::jsonb)
  )
  from private.bill_splits s
  where s.id = p_split_id;
$$;

revoke all on function private.clean_bill_name(text), private.bill_split_state(uuid) from public, anon, authenticated;

-- ---------------------------------------------------------------------------------------------
-- The host's side (signed in)
-- ---------------------------------------------------------------------------------------------

-- Starts a split from what the app holds: {id, title, host_name, total_paise, items: [{id, name,
-- quantity, amount_paise}], people: [{id, name, is_host}], claims: [{item_id, person_id, weight}]}.
-- The id comes from the phone, so a retry after a dropped reply returns the same link.
create function public.create_bill_split(p_payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_id uuid := (p_payload ->> 'id')::uuid;
  v_split private.bill_splits;
  v_items jsonb := coalesce(p_payload -> 'items', '[]'::jsonb);
  v_people jsonb := coalesce(p_payload -> 'people', '[]'::jsonb);
  v_claims jsonb := coalesce(p_payload -> 'claims', '[]'::jsonb);
  v_host_name text := private.clean_bill_name(p_payload ->> 'host_name');
begin
  if v_user is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;
  if v_id is null then
    raise exception 'bad_request' using errcode = '22023';
  end if;

  select * into v_split from private.bill_splits where id = v_id;
  if found then
    if v_split.host_id <> v_user then
      raise exception 'not_allowed' using errcode = '42501';
    end if;
    return jsonb_build_object('id', v_split.id, 'token', v_split.token, 'expires_at', v_split.expires_at);
  end if;

  if jsonb_typeof(v_items) <> 'array' or jsonb_typeof(v_people) <> 'array' or jsonb_typeof(v_claims) <> 'array'
     or jsonb_array_length(v_items) not between 1 and 100
     or jsonb_array_length(v_people) not between 1 and 30
     or jsonb_array_length(v_claims) > 3000
     or v_host_name = ''
     -- Exactly one host at the table.
     or (select count(*) from jsonb_array_elements(v_people) as p where coalesce((p ->> 'is_host')::boolean, false)) <> 1 then
    raise exception 'bad_request' using errcode = '22023';
  end if;

  -- Splits nobody can open any more go now: there is no scheduled job, and this is often enough.
  delete from private.bill_splits where expires_at < now();

  if (select count(*) from private.bill_splits where host_id = v_user) >= 10 then
    raise exception 'too_many_splits' using errcode = 'P0001';
  end if;

  insert into private.bill_splits (id, host_id, token, title, host_name, total_paise, expires_at)
  values (
    v_id, v_user, replace(gen_random_uuid()::text, '-', ''),
    left(btrim(coalesce(p_payload ->> 'title', '')), 120), v_host_name,
    (p_payload ->> 'total_paise')::bigint, now() + interval '24 hours'
  )
  returning * into v_split;

  insert into private.bill_split_items (id, split_id, position, name, quantity, amount_paise)
  select (i.value ->> 'id')::uuid, v_id, (i.ordinality - 1)::integer,
         left(btrim(i.value ->> 'name'), 80), coalesce((i.value ->> 'quantity')::integer, 1), (i.value ->> 'amount_paise')::bigint
  from jsonb_array_elements(v_items) with ordinality as i (value, ordinality);

  insert into private.bill_split_people (id, split_id, name, is_host)
  select (p.value ->> 'id')::uuid, v_id, private.clean_bill_name(p.value ->> 'name'), coalesce((p.value ->> 'is_host')::boolean, false)
  from jsonb_array_elements(v_people) with ordinality as p (value, ordinality)
  order by p.ordinality;

  insert into private.bill_split_claims (split_id, item_id, person_id, weight)
  select v_id, (c ->> 'item_id')::uuid, (c ->> 'person_id')::uuid, coalesce((c ->> 'weight')::smallint, 1)
  from jsonb_array_elements(v_claims) as c;

  return jsonb_build_object('id', v_split.id, 'token', v_split.token, 'expires_at', v_split.expires_at);
end;
$$;

-- The host corrects the bill while people are picking: {title, total_paise, items: [...]}. Items
-- keep their picks by id; an item left out goes, and its picks with it.
create function public.update_bill_split(p_id uuid, p_payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_split private.bill_splits;
  v_items jsonb := coalesce(p_payload -> 'items', '[]'::jsonb);
begin
  select * into v_split from private.bill_splits where id = p_id and expires_at > now() for update;
  if not found then
    raise exception 'not_open' using errcode = 'P0001';
  end if;
  if v_user is null or v_split.host_id <> v_user then
    raise exception 'not_allowed' using errcode = '42501';
  end if;
  if jsonb_typeof(v_items) <> 'array' or jsonb_array_length(v_items) not between 1 and 100 then
    raise exception 'bad_request' using errcode = '22023';
  end if;

  delete from private.bill_split_items i
  where i.split_id = p_id
    and not exists (select 1 from jsonb_array_elements(v_items) as n where (n ->> 'id')::uuid = i.id);

  insert into private.bill_split_items as i (id, split_id, position, name, quantity, amount_paise)
  select (n.value ->> 'id')::uuid, p_id, (n.ordinality - 1)::integer,
         left(btrim(n.value ->> 'name'), 80), coalesce((n.value ->> 'quantity')::integer, 1), (n.value ->> 'amount_paise')::bigint
  from jsonb_array_elements(v_items) with ordinality as n (value, ordinality)
  on conflict (id) do update
    set position = excluded.position, name = excluded.name, quantity = excluded.quantity, amount_paise = excluded.amount_paise
    -- An id that belongs to some other split is left alone.
    where i.split_id = excluded.split_id;

  if (select count(*) from private.bill_split_items where split_id = p_id) <> jsonb_array_length(v_items) then
    raise exception 'bad_request' using errcode = '22023';
  end if;

  update private.bill_splits
  set title = left(btrim(coalesce(p_payload ->> 'title', title)), 120),
      total_paise = coalesce((p_payload ->> 'total_paise')::bigint, total_paise),
      version = version + 1
  where id = p_id;

  return private.bill_split_state(p_id);
end;
$$;

-- The host takes someone off the table (a name added twice, a stranger with the link).
create function public.remove_bill_split_person(p_id uuid, p_person_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_split private.bill_splits;
begin
  select * into v_split from private.bill_splits where id = p_id and expires_at > now() for update;
  if not found then
    raise exception 'not_open' using errcode = 'P0001';
  end if;
  if v_user is null or v_split.host_id <> v_user then
    raise exception 'not_allowed' using errcode = '42501';
  end if;

  delete from private.bill_split_people where split_id = p_id and id = p_person_id and not is_host;
  if found then
    update private.bill_splits set version = version + 1 where id = p_id;
  end if;
  return private.bill_split_state(p_id);
end;
$$;

-- The host is finished (the expense is saved, or they stopped sharing): the split and everything
-- typed into it is deleted, and the link stops working.
create function public.close_bill_split(p_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;
  delete from private.bill_splits where id = p_id and host_id = v_user;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- The table's side (anyone with the link, signed in or not)
-- ---------------------------------------------------------------------------------------------

-- The split behind a link, or null when there isn't one any more (finished, expired, mistyped).
create function public.bill_split_view(p_token text)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select private.bill_split_state(s.id)
  from private.bill_splits s
  where s.token = p_token and s.expires_at > now();
$$;

-- Adds a name to the table and returns {person_id, split}. Typing a name that is already there
-- returns that person: the page offers "tap your name" first, and this keeps a second phone from
-- adding a second Rohan. The host's name can't be taken. [p_person_id] lets the host's phone choose
-- the id, so what it shows before the reply arrives is what the server keeps.
create function public.bill_split_join(p_token text, p_name text, p_person_id uuid default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_split private.bill_splits;
  v_name text := private.clean_bill_name(p_name);
  v_person private.bill_split_people;
begin
  select * into v_split from private.bill_splits where token = p_token and expires_at > now() for update;
  if not found then
    raise exception 'not_open' using errcode = 'P0001';
  end if;
  if v_name = '' then
    raise exception 'bad_name' using errcode = 'P0001';
  end if;

  select * into v_person from private.bill_split_people
  where split_id = v_split.id and (id = p_person_id or lower(name) = lower(v_name))
  order by (id = p_person_id) desc nulls last
  limit 1;
  if found then
    if v_person.is_host and (auth.uid() is null or auth.uid() <> v_split.host_id) then
      raise exception 'name_taken' using errcode = 'P0001';
    end if;
    return jsonb_build_object('person_id', v_person.id, 'split', private.bill_split_state(v_split.id));
  end if;

  if (select count(*) from private.bill_split_people where split_id = v_split.id) >= 30 then
    raise exception 'too_many_people' using errcode = 'P0001';
  end if;

  insert into private.bill_split_people (id, split_id, name)
  values (coalesce(p_person_id, gen_random_uuid()), v_split.id, v_name)
  returning * into v_person;
  update private.bill_splits set version = version + 1 where id = v_split.id;

  return jsonb_build_object('person_id', v_person.id, 'split', private.bill_split_state(v_split.id));
end;
$$;

-- One person's pick of one item: weight 0 takes it back, 1 to 20 sets how many parts they had.
-- Returns the split as it now stands. The host's picks need the host's session.
create function public.bill_split_claim(p_token text, p_person_id uuid, p_item_id uuid, p_weight integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_split private.bill_splits;
  v_person private.bill_split_people;
begin
  select * into v_split from private.bill_splits where token = p_token and expires_at > now() for update;
  if not found then
    raise exception 'not_open' using errcode = 'P0001';
  end if;
  select * into v_person from private.bill_split_people where split_id = v_split.id and id = p_person_id;
  if not found or not exists (select 1 from private.bill_split_items where split_id = v_split.id and id = p_item_id)
     or p_weight is null or p_weight not between 0 and 20 then
    raise exception 'bad_request' using errcode = '22023';
  end if;
  if v_person.is_host and (auth.uid() is null or auth.uid() <> v_split.host_id) then
    raise exception 'not_allowed' using errcode = '42501';
  end if;

  if p_weight = 0 then
    delete from private.bill_split_claims where item_id = p_item_id and person_id = p_person_id;
  else
    insert into private.bill_split_claims (split_id, item_id, person_id, weight)
    values (v_split.id, p_item_id, p_person_id, p_weight::smallint)
    on conflict (item_id, person_id) do update set weight = excluded.weight;
  end if;
  update private.bill_splits set version = version + 1 where id = v_split.id;

  return private.bill_split_state(v_split.id);
end;
$$;

-- "That's everything I had", or taking it back. Only tells the host who is still choosing.
create function public.bill_split_done(p_token text, p_person_id uuid, p_done boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_split private.bill_splits;
  v_person private.bill_split_people;
begin
  select * into v_split from private.bill_splits where token = p_token and expires_at > now() for update;
  if not found then
    raise exception 'not_open' using errcode = 'P0001';
  end if;
  select * into v_person from private.bill_split_people where split_id = v_split.id and id = p_person_id;
  if not found or p_done is null then
    raise exception 'bad_request' using errcode = '22023';
  end if;
  if v_person.is_host and (auth.uid() is null or auth.uid() <> v_split.host_id) then
    raise exception 'not_allowed' using errcode = '42501';
  end if;

  update private.bill_split_people set done = p_done where id = p_person_id and done is distinct from p_done;
  if found then
    update private.bill_splits set version = version + 1 where id = v_split.id;
  end if;
  return private.bill_split_state(v_split.id);
end;
$$;

revoke all on function
  public.create_bill_split(jsonb), public.update_bill_split(uuid, jsonb),
  public.remove_bill_split_person(uuid, uuid), public.close_bill_split(uuid),
  public.bill_split_view(text), public.bill_split_join(text, text, uuid),
  public.bill_split_claim(text, uuid, uuid, integer), public.bill_split_done(text, uuid, boolean)
  from public, anon;

grant execute on function
  public.create_bill_split(jsonb), public.update_bill_split(uuid, jsonb),
  public.remove_bill_split_person(uuid, uuid), public.close_bill_split(uuid)
  to authenticated;

-- The one exception to "anon runs nothing": the four that take a split's link.
grant execute on function
  public.bill_split_view(text), public.bill_split_join(text, text, uuid),
  public.bill_split_claim(text, uuid, uuid, integer), public.bill_split_done(text, uuid, boolean)
  to anon, authenticated;

commit;
