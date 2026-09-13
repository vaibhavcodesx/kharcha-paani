-- Restores fields the core schema dropped from the existing Room model
-- (SplitEntity.splitType, SplitShareEntity.percentage, SettlementEntity.isIou,
-- SettlementEntity.groupId). Without them the cloud copy can't represent
-- exact/percentage splits, IOUs, or trip-scoped settlements, all of which
-- already work locally.

alter table public.splits
  add column split_type text not null default 'equal'
    constraint splits_split_type_check
    check (split_type in ('equal', 'exact', 'percentage'));

-- Unscaled numeric on purpose: the app keeps the raw Double (33.333... for a
-- three-way equal split), and a fixed scale would round it on every sync.
alter table public.split_shares
  add column percentage numeric
    constraint split_shares_percentage_check
    check (percentage >= 0 and percentage <= 100);

alter table public.settlements
  add column is_iou boolean not null default false,
  add column group_id uuid references public.groups (id) on delete set null;

create index settlements_group_id_idx on public.settlements (group_id);

-- settlements RLS is intentionally unchanged: still readable/writable only by
-- the two parties. Whether other group members should see a group-scoped
-- settlement is decided with the groups/invites work (Migration Plan step 4).
