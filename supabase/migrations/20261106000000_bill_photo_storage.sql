-- Bill photos in the cloud, shared with the people who can see the expense
-- (docs/designs/bill-photo-sync.md; owner decisions 2026-11-06). Additive only.
--
-- Objects live at receipts/<uploader uid>/<transaction id>/<photo uuid>.(webp|jpg).
-- * Writes: only into your own folder.
-- * Reads: your own folder, or an object that some transaction you can access points at through
--   receipt_storage_path. The uploader sets that path (sharing on) or leaves it null (sharing off).
-- * The path on a transaction must sit in that transaction's owner's folder, under that same
--   transaction id. Without that, anyone who can edit a row could point it at somebody else's
--   photo and read it.

-- 1. The bucket: private, at most 1 MB a file, images only.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('receipts', 'receipts', false, 1048576, array['image/webp', 'image/jpeg'])
on conflict (id) do update
  set public = false,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- 2. A transaction can only point at a photo of its own, in its owner's folder.
alter table public.transactions
  add constraint transactions_receipt_path_own check (
    receipt_storage_path is null
    or receipt_storage_path ~ ('^' || owner_id::text || '/' || id::text || '/[A-Za-z0-9-]{1,64}\.(webp|jpg)$')
  );

create index if not exists transactions_receipt_storage_path_idx
  on public.transactions (receipt_storage_path)
  where receipt_storage_path is not null;

-- 3. Can the current user read this object through an expense they can see?
create or replace function private.can_read_receipt(p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.transactions t
    where t.receipt_storage_path = p_name
      and t.deleted_at is null
      and private.can_access_transaction(t.id)
  );
$$;

revoke all on function private.can_read_receipt(text) from public, anon;
grant execute on function private.can_read_receipt(text) to authenticated;

-- 4. Storage policies.
create policy receipts_read on storage.objects
  for select to authenticated
  using (
    bucket_id = 'receipts'
    and (
      (storage.foldername(name))[1] = (select auth.uid())::text
      or private.can_read_receipt(name)
    )
  );

create policy receipts_insert_own on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

create policy receipts_update_own on storage.objects
  for update to authenticated
  using (bucket_id = 'receipts' and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'receipts' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy receipts_delete_own on storage.objects
  for delete to authenticated
  using (bucket_id = 'receipts' and (storage.foldername(name))[1] = (select auth.uid())::text);
