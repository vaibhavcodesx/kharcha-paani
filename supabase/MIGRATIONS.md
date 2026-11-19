<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Migrations: what is applied

Checked against the live project's `supabase_migrations.schema_migrations` (read-only) on 2026-11-19. The file
name's timestamp is the version the live history uses. Update this table whenever the owner applies a migration.

| File | State |
|---|---|
| `20260913144006_create_core_schema_with_rls.sql` | applied, recorded in the live history |
| `20260913144030_restrict_handle_new_user_execute.sql` | applied, recorded in the live history |
| `20260913154813_restore_split_type_percentage_iou_group.sql` | applied, recorded in the live history |
| `20260913184544_transactions_sync_columns.sql` | applied, recorded in the live history |
| `20260914033124_groups_invites_rls.sql` | applied, recorded in the live history |
| `20260914034325_group_sync_columns.sql` | applied, recorded in the live history |
| `20260914142135_leave_rules_and_immutable_records.sql` | applied, recorded in the live history |
| `20260915184031_profile_avatar_emoji.sql` | applied, recorded in the live history |
| `20260918073251_payer_can_edit_group_expenses.sql` | applied, recorded in the live history |
| `20260918210534_add_upi_id_to_profiles.sql` | applied, recorded in the live history |
| `20260918210646_fix_upi_id_format_constraint.sql` | applied, recorded in the live history |
| `20260920030201_local_participant_shares.sql` | applied, recorded in the live history |
| `20260924173011_owner_private_local_ledger.sql` | applied, recorded in the live history |
| `20260927073400_settlement_confirmation.sql` | applied, recorded in the live history |
| `20260927073436_group_expense_guests.sql` | applied, recorded in the live history |
| `20260927073454_profile_hue.sql` | applied, recorded in the live history |
| `20261025000100_delete_my_account.sql` | **applied by hand, NOT recorded** (repair: see `docs/engineering/runbooks/migration-history-repair.md`) |
| `20261026141316_group_expense_sums.sql` | applied, recorded in the live history |
| `20261026154229_ai_daily_reads.sql` | applied, recorded in the live history |
| `20261026203844_database_hygiene.sql` | applied, recorded in the live history |
| `20261106124001_bill_photo_storage.sql` | applied, recorded in the live history |
| `20261106130938_text_limits.sql` | applied, recorded in the live history |
| `20261110000000_bill_item_splits.sql` | **applied by hand, NOT recorded** (repair: see `docs/engineering/runbooks/migration-history-repair.md`) |
| `20261119000000_app_config.sql` | **applied by hand 2026-11-19, NOT recorded in the live history** |
| `20261119000100_ai_plans_and_ceiling.sql` | **applied by hand 2026-11-19 (tables first, then the `take_ai_read` replacement from the one-paste script), NOT recorded in the live history** |
| `20261119000200_profiles_column_grants.sql` | **applied by hand 2026-11-19, NOT recorded in the live history** |
| `20261119000300_group_invite_limits_and_member_removal.sql` | **applied by hand 2026-11-19, NOT recorded in the live history** |
| `20261119000400_receipt_quota.sql` | **applied by hand 2026-11-19, NOT recorded in the live history** |
| Admin page S1 to S4 (`20261120000000` to `20261120000300`, in PRs #445, #448, #451, #452, not yet merged) | **applied by hand 2026-11-19 from the same script, NOT recorded.** The owner's row is in `private.admins`. Merge those PRs only after review; the SQL is already live |

Staged files are applied by the owner, by hand, in file order, after a database dump
(`docs/engineering/runbooks/migration.md`). Some staged files need an app change first; their header says so.
