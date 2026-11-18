-- Run against the live project inside a transaction that rolls back (see supabase/tests/README or the
-- other *_test.sql files for the pattern). Staged with 20261119000000_app_config.sql; not run yet.
-- Ends with a raised error carrying the results so nothing is kept.
do $$
declare
  v_rows integer;
  v_min integer;
  v_denied boolean;
  v_out text := '';
begin
  -- The single row exists with "no minimum".
  select count(*), max(min_version_code) into v_rows, v_min from public.app_config;
  v_out := v_out || format('rows=%s min=%s; ', v_rows, v_min);
  if v_rows <> 1 or v_min <> 0 then raise exception 'FAIL seed: %', v_out; end if;

  -- A second row is impossible (the key is a boolean that must be true).
  begin
    insert into public.app_config (id) values (false);
    raise exception 'FAIL: inserted id=false';
  exception when check_violation then v_out := v_out || 'second row refused; ';
  end;
  begin
    insert into public.app_config (id) values (true);
    raise exception 'FAIL: inserted a duplicate row';
  exception when unique_violation then v_out := v_out || 'duplicate refused; ';
  end;

  -- recommended can't sit below the minimum.
  begin
    update public.app_config set min_version_code = 5, recommended_version_code = 3;
    raise exception 'FAIL: recommended below min accepted';
  exception when check_violation then v_out := v_out || 'recommended>=min enforced; ';
  end;

  -- anon can read, and can't write.
  set local role anon;
  select count(*) into v_rows from public.app_config;
  if v_rows <> 1 then reset role; raise exception 'FAIL: anon cannot read (% rows)', v_rows; end if;
  v_denied := false;
  begin
    update public.app_config set min_version_code = 99;
  exception when insufficient_privilege then v_denied := true;
  end;
  reset role;
  if not v_denied then raise exception 'FAIL: anon updated the row'; end if;
  v_out := v_out || 'anon reads, cannot write; ';

  -- authenticated: same.
  set local role authenticated;
  v_denied := false;
  begin
    delete from public.app_config;
  exception when insufficient_privilege then v_denied := true;
  end;
  reset role;
  if not v_denied then raise exception 'FAIL: authenticated deleted the row'; end if;
  v_out := v_out || 'authenticated cannot delete; ';

  raise exception 'RESULTS (rolled back): %', v_out;
end $$;
