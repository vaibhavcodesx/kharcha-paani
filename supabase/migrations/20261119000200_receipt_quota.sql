-- STAGED, NOT APPLIED. Cap new cloud receipts at 200 KB each and 50 objects per account.
-- Existing receipts remain readable and deletable under the existing access policies.
-- Owner: validate in staging and gate old clients before applying (see the migration runbook).
-- Rollback (run in one transaction as the migration role):
-- create or replace function private.enforce_receipt_account_quota() returns trigger
-- language plpgsql security definer set search_path = '' as $$ begin return null; end; $$;
-- update storage.buckets set file_size_limit = 1048576 where id = 'receipts';

begin;

update storage.buckets set file_size_limit = 200000 where id = 'receipts';

create function private.enforce_receipt_account_quota()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  account_folder text := split_part(new.name, '/', 1);
begin
  if tg_op = 'UPDATE' then
    if old.bucket_id = new.bucket_id and split_part(old.name, '/', 1) = account_folder then
      return null;
    end if;
  end if;

  -- Serialize quota checks for the same account, including concurrent device uploads.
  perform pg_advisory_xact_lock(hashtextextended('receipts:' || account_folder, 0));
  if (
    select count(*)
    from storage.objects
    where bucket_id = 'receipts' and split_part(name, '/', 1) = account_folder
  ) > 50 then
    raise check_violation using message = 'Receipt quota reached';
  end if;
  return null;
end;
$$;

revoke all on function private.enforce_receipt_account_quota() from public, anon, authenticated;

create trigger receipts_account_quota
after insert or update of bucket_id, name on storage.objects
for each row when (new.bucket_id = 'receipts')
execute function private.enforce_receipt_account_quota();

commit;
