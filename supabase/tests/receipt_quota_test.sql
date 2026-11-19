-- Staging only. Synthetic Storage metadata stays in this rolled-back transaction.
begin;

do $$
declare
  first_folder constant text := '10000000-0300-4000-8000-000000000001/receipt/';
  second_folder constant text := '10000000-0300-4000-8000-000000000002/receipt/';
begin
  if (select file_size_limit from storage.buckets where id = 'receipts') is distinct from 200000 then
    raise exception 'FAIL: receipts bucket limit must be 200000 bytes';
  end if;

  insert into storage.objects (bucket_id, name)
    select 'receipts', first_folder || n || '.jpg' from generate_series(1, 50) n;
  insert into storage.objects (bucket_id, name) values ('receipts', second_folder || '1.jpg');

  begin
    insert into storage.objects (bucket_id, name) values ('receipts', first_folder || '51.jpg');
    raise exception 'FAIL: account exceeded its receipt quota';
  exception when check_violation then
    if sqlerrm <> 'Receipt quota reached' then raise; end if;
  end;

  begin
    update storage.objects set name = first_folder || '51.jpg'
      where bucket_id = 'receipts' and name = second_folder || '1.jpg';
    raise exception 'FAIL: moving a photo into a full account exceeded its quota';
  exception when check_violation then
    if sqlerrm <> 'Receipt quota reached' then raise; end if;
  end;

  update storage.objects set name = first_folder || 'renamed.jpg'
    where bucket_id = 'receipts' and name = first_folder || '1.jpg';
  delete from storage.objects where bucket_id = 'receipts' and name = first_folder || 'renamed.jpg';
  insert into storage.objects (bucket_id, name) values ('receipts', first_folder || '51.jpg');

  raise exception 'receipt_quota_test PASS (rolled back)';
end
$$;
