-- Staged shared-bill display update. Existing links and person ids keep working.
-- No table changes or grants. To roll back, restore the prior definitions of the three
-- functions below from 20261110000000_bill_item_splits.sql.

begin;

-- A first name is the first word. Fold common non-breaking spaces and control separators.
create or replace function private.clean_bill_name(p_name text)
returns text
language sql
immutable
set search_path = ''
as $$
  select left(split_part(btrim(regexp_replace(
    translate(coalesce(p_name, ''), U&'\00A0\202F', '  '), '[[:space:][:cntrl:]]+', ' ', 'g'
  )), ' ', 1), 40);
$$;

-- Use the same roster for old links and new ones. Number repeats in roster order.
create or replace function private.bill_split_state(p_split_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', s.id,
    'title', s.title,
    'host_name', private.clean_bill_name(s.host_name),
    'total_paise', s.total_paise,
    'version', s.version,
    'expires_at', s.expires_at,
    'items', coalesce((
      select jsonb_agg(jsonb_build_object('id', i.id, 'name', i.name, 'quantity', i.quantity, 'amount_paise', i.amount_paise)
                       order by i.position, i.id)
      from private.bill_split_items i where i.split_id = s.id), '[]'::jsonb),
    'people', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', p.id,
        'name', case when p.name_number = 1 then p.first_name else p.first_name || ' ' || p.name_number end,
        'is_host', p.is_host,
        'done', p.done
      ) order by p.seq)
      from (
        select names.*, row_number() over (partition by lower(names.first_name) order by names.seq) as name_number
        from (
          select people.id, people.seq, people.is_host, people.done, private.clean_bill_name(people.name) as first_name
          from private.bill_split_people people where people.split_id = s.id
        ) names
      ) p), '[]'::jsonb),
    'claims', coalesce((
      select jsonb_agg(jsonb_build_object('item_id', c.item_id, 'person_id', c.person_id, 'weight', c.weight)
                       order by c.item_id, c.person_id)
      from private.bill_split_claims c where c.split_id = s.id), '[]'::jsonb)
  )
  from private.bill_splits s
  where s.id = p_split_id;
$$;

-- A supplied person id identifies a new entry or a retry. Older pages can still reselect by name.
create or replace function public.bill_split_join(p_token text, p_name text, p_person_id uuid default null)
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
  where split_id = v_split.id
    and (id = p_person_id or (p_person_id is null and lower(private.clean_bill_name(name)) = lower(v_name)))
  order by is_host, seq
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

commit;
