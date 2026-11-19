<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Product Glossary: one word per thing

Source: `docs/audits/2026-11-17/OWNER-ANSWERS.md` (D-14), `docs/audits/2026-11-17/04-ux-and-feature-gaps.md` (UX-36, UX-37).

This document establishes the single canonical term for each core product concept in Kharcha Paani. Inconsistent terminology creates friction for users. Across all user-facing copy, notifications, and documentation, each concept must use its chosen word without rotating synonyms.

---

## 1. Core Vocabulary Definitions

| Concept | Chosen Word | What it means | What NOT to call it |
| :--- | :--- | :--- | :--- |
| **Direct Contact** | **friend** | Someone you split with or track balances with directly (1:1). | person, people, contact, user, buddy |
| **Group Participant** | **member** | Someone who has joined or been added to a shared group. | groupmate, person, people, friend |
| **Non-App Participant** | **guest** | Someone without a Kharcha Paani account in a split or group; their share is covered by the user who added them. | friend who isn't on the app, ghost, outside person |
| **Direct Debt / Lending** | **IOU** | Money directly lent or borrowed between friends that is not part of a group expense or split bill. Explained once at the top of the Log an IOU sheet. | loan, debt, credit, borrow/lend record |
| **Shared Space** | **group** | A collective space for sharing expenses and balances with multiple people. | trip, flat, circle, room |
| **General Ledger Item** | **entry** | Any single recorded item in the personal ledger (can be an expense, income, or adjustment). | transaction, record, item, log |
| **Money Spent** | **expense** | Money paid out of pocket for goods, food, bills, or services. | transaction, spending, debit |
| **Clearing Balances (Verb)** | **settle up** | The action of paying back or clearing an outstanding balance. | settle, clear, pay back, square up |
| **Clearing Balances (Noun)** | **settlement** | The recorded event or payment that resolves an outstanding balance. | settle, clearance, payment record |

---

## 2. Audit of User-Facing Nouns in strings.xml

### Nouns for a Person
- **`friend`** (28 strings): Canonical term for 1:1 relationships ("Add a friend", "Friends owe you", "Who owes whom, with friends and on trips").
- **`member`** (11 strings): Used for group membership ("%1$d member", "Former member", "Archived groups... can't take new members").
- **`groupmate`** (8 strings): Inconsistently used in notification, UPI, and settlement contexts ("a groupmate", "When a groupmate says money changed hands...").
- **`people`** (8 strings): Generic plural used loosely ("%1$d PEOPLE", "Invite people", "Splits reach the other people...").
- **`guest`** (5 strings): Used specifically for participants whose share is covered by another member ("Your guest · you cover their share", "Guests are in this split...").
- **`person`** (4 strings): Generic singular used where a more specific term fits ("Only the person who added this expense...", "once the other person confirms it").
- **`user`** (1 string): Technical jargon used in AI privacy notice ("never shown to other users").

### Nouns for a Debt
- **`IOU`** (17 strings): Kept. The owner decided on 2026-11-19 that "loan" sounds too serious; the word is explained at the top of the Log an IOU sheet (`payment_iou_body`). PR #411 was reverted.
- **`loan`** (1 string): Only the default note on a lent amount ("Loan").
- **`balance`** (9 strings): Used for running totals ("takes it out of your balance", "clear every balance").
- **`borrowed` / `lent`** (17 strings): Used as directional verbs or descriptors ("You lent", "You borrowed", "Money lent or borrowed").
- **`debt`** (0 user-facing strings): Kept internal (e.g. debt simplification algorithm).

---

## 3. Disagreeing Strings by Concept

The table below catalogs strings that deviate from the chosen canonical vocabulary, listing the file, key, current wording, and recommended alignment.

| Concept | Chosen Word | Disagreeing String (File & Key) | Current Text | Recommended Alignment |
| :--- | :--- | :--- | :--- | :--- |
| **Person (1:1)** | **friend** | `strings.xml:notification_screen_payment_body` | "A payment in a group counts once the other person confirms it." | Replace "the other person" with "the other member". |
| **Person (1:1)** | **friend** | `strings.xml:action_notice_expense_payer_restricted` | "Only the person who added this expense can change who paid..." | Replace "the person who added" with "the member who added" (in groups) or "the friend who added". |
| **Person (1:1)** | **friend** | `strings.xml:delete_account_stays_body` | "Groups you made pass to the person who's been in them longest." | Replace "person" with "member". |
| **Person (1:1)** | **friend** | `strings.xml:account_help_feedback_detail` | "Something wrong, or an idea? It goes to the person who makes the app" | Keep or rephrase to "the creator" / "developer" if personal tone desired; avoid generic "person". |
| **Person (1:1)** | **friend** | `strings.xml:shared_group_copy_invite` | "Invite people" | Use "Invite members" or "Invite friends". |
| **Person (1:1)** | **friend** | `strings.xml:shared_group_copy_people` | "%1$d PEOPLE" | Align to "%1$d MEMBERS" or "%1$d FRIENDS". |
| **Person (1:1)** | **friend** | `strings.xml:shared_group_copy_split_explanation` | "Splits reach the other people only inside a shared group." | Replace "other people" with "other members". |
| **Person (1:1)** | **friend** | `strings.xml:action_notice_created_shared` | "Created %1$s. Open it to invite people." | Replace "invite people" with "invite members". |
| **Person (1:1)** | **friend** | `strings.xml:ai_display_notice_body` | "It's never shown to other users, and we never sell your data." | Replace "other users" with "anyone else" or "other people". |
| **Person (1:1)** | **friend** | `item_split_strings.xml:item_split_supporting` | "Pick a person, then tap what they had." | Replace "person" with "friend". |
| **Person (1:1)** | **friend** | `item_split_strings.xml:item_split_people_count` | "%1$d person %1$d people" | Align count phrasing with friends or members. |
| **Person (1:1)** | **friend** | `item_split_strings.xml:item_split_table_full` | "The table is full: 30 people is the most a bill can take." | Keep "people" as generic capacity or align to "friends". |
| **Person (1:1)** | **friend** | `bill_photo_cloud_strings.xml:bill_photo_share_detail` | "People in a split see a smaller copy of the bill." | Replace "People in a split" with "Friends in a split". |
| **Group Participant** | **member** | `strings.xml:settlement_row_groupmate` | "this groupmate" | Replace "this groupmate" with "this member". |
| **Group Participant** | **member** | `strings.xml:notification_screen_payments_hint` | "When a groupmate says money changed hands with you" | Replace "groupmate" with "member". |
| **Group Participant** | **member** | `strings.xml:action_notice_upi_saved` | "UPI ID saved. Groupmates settling up with you will see it." | Replace "Groupmates" with "Members". |
| **Group Participant** | **member** | `strings.xml:action_notice_groupmate` | "a groupmate" | Replace "a groupmate" with "a member". |
| **Group Participant** | **member** | `strings.xml:notification_payment_description` | "When a groupmate says money changed hands with you..." | Replace "groupmate" with "member". |
| **Group Participant** | **member** | `strings.xml:notification_groupmate` | "A groupmate" | Replace "A groupmate" with "A member". |
| **Group Participant** | **member** | `strings.xml:account_name_helper` | "Groupmates see your name and avatar." | Replace "Groupmates" with "Members". |
| **Group Participant** | **member** | `strings.xml:account_colour_wear_description` | "You wear this colour everywhere: on your phone and on every groupmate's." | Replace "groupmate's" with "member's". |
| **Group Participant** | **member** | `strings.xml:create_group_local` | "Only on this phone. The friends in it don't need the app." | Replace "friends" with "members". |
| **Group Participant** | **member** | `strings.xml:splits_groups_empty_body` | "...join one with the code a friend sends." | Replace "a friend sends" with "a member sends" (or leave if invite is from a friend). |
| **Group Participant** | **member** | `strings.xml:upi_copy_supporting` | "People in your shared groups can see it too." | Replace "People" with "Members". |
| **Non-App Participant** | **guest** | `strings.xml:delete_account_goes_body` | "...splits and IOUs with friends who aren't on the app..." | Replace with "...splits and loans with guests...". |
| **Non-App Participant** | **guest** | `strings.xml:friend_form_supporting` | "...and they don't need the app." | Clarify that non-app participants function as guests in groups. |
| **Shared Space** | **group** | `strings.xml:splits_subtitle` | "Who owes whom, with friends and on trips" | Replace "on trips" with "in groups". |
| **Shared Space** | **group** | `strings.xml:group_detail_trip` | "How did this trip go?" | Replace with "How did this group go?" |
| **Shared Space** | **group** | `strings.xml:group_detail_read` | "Read this trip" | Replace with "Read this group". |
| **Shared Space** | **group** | `strings.xml:ai_display_labelresource_tripreview` | "Read this trip" | Replace with "Read this group". |
| **Shared Space** | **group** | `ai_off_nudge_strings.xml:ai_off_nudge_trip_title` | "The trip review is written by AI" | Replace "trip review" with "group review". |
| **Shared Space** | **group** | `ai_off_nudge_strings.xml:ai_off_nudge_trip_body` | "...writes a short review of the trip from its expenses." | Replace "the trip" with "the group". |
| **Ledger Item** | **entry** / **expense** | `strings.xml:action_notice_statement_nothingfound` | "No transactions found in that PDF." | Replace "transactions" with "entries". |
| **Ledger Item** | **entry** / **expense** | `strings.xml:settlement_row_local` | "This changes the record on this phone." | Replace "record" with "entry" or "settlement". |
| **Ledger Item** | **entry** / **expense** | `strings.xml:shared_group_copy_record` | "Record in group" | Align to "Save in group" or "Add to group". |
| **Clearing Balances** | **settle up** / **settlement** | `strings.xml:splits_settle` | "Settle" | Replace "Settle" with "Settle up". |
| **Clearing Balances** | **settle up** / **settlement** | `strings.xml:settlement_row_clear` | "Clear it away" | Replace with "Dismiss" or "Remove". |
