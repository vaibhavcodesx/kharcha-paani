# Play Console: App content answers

Updated 6 November 2026 for 1.7.0. Based on the merged code and the Play research in
[../production-readiness/audits/play-store.md](../production-readiness/audits/play-store.md) §3–§4.
**For owner review**. Not legal advice.

## Privacy policy

- **Privacy policy URL:** `https://vaibhavcodesx.github.io/kharcha-paani-site/privacy.html`
  (once the site in `site/` is published; see `site/README.md`).

## Data safety

Group invite QR scanning reads camera frames on the phone and discards them after decoding.
Only the invite code from a valid scan is sent to the group preview service, as with a pasted invite.

**Does your app collect or share any of the required user data types?** Yes.

**Is all of the user data collected by your app encrypted in transit?** Yes (Supabase, Groq, and Gemini
are HTTPS only; cleartext traffic is disabled).

**Do you provide a way for users to request that their data is deleted?** Yes: in the app (Delete my account),
and at `https://vaibhavcodesx.github.io/kharcha-paani-site/delete-account.html`.

### Data types

"Collected" means it leaves the phone. Purposes: **App functionality** and **Account management**
unless noted. Collection is **required** when signed in unless noted.

| Category → type | Collected | Shared | Ephemeral | Why / notes |
|---|---|---|---|---|
| Personal info → Name | Yes | No | No | Google sign-in, profile name groupmates see |
| Personal info → Email address | Yes | No | No | Google sign-in |
| Personal info → User IDs | Yes | No | No | The account's id |
| Personal info → Other info | Yes | No | No | Names you give friends who aren't on the app, and guests in group splits |
| Financial info → User payment info | Yes (optional) | No | No | Your UPI ID, if you add one; groupmates see it |
| Financial info → Purchase history | Yes | No | No | Expenses, splits, settlements, IOUs |
| Financial info → Other financial info | Yes | **Yes** (see below) | Yes for Gemini | Statement rows read by Gemini |
| Photos and videos → Photos | Yes (optional) | **Yes** | **No** | Bill photos attached to expenses are backed up to the user's account in cloud storage, up to 50 photos per account. New cloud copies are re-encoded, stripped of EXIF/location metadata, and at most 200 KB. They are shared with groupmates on splits unless "Share bill photos" is turned off in Settings. Also read by Gemini when AI bill scanning is used. Deleted when the expense, photo, or account is deleted. |
| Files and docs | Yes (optional) | **Yes** | Yes | Statement PDFs sent to Gemini |
| App activity → Other user-generated content | Yes | **Yes** for the Gemini part | Partly | Notes; quick-add text sent to Groq or Gemini; Insights summaries sent to Gemini |
| App info and performance → Crash logs | Yes (optional) | No | No | Purpose: **Analytics** (app stability). Off switch in Help & about |
| App info and performance → Diagnostics | Yes (optional) | No | No | As above |
| Audio → Voice or sound recordings | Yes (optional) | **Yes** | Yes | Spoken quick-add line, recorded up to 30 seconds in memory and sent to Gemini once, only after the AI notice is accepted and only while online; kept in memory only, never saved on phone or server. Purpose: App functionality. |
| Contacts, Location, Calendar, Messages, Device or other IDs, Web browsing, Health | No | | | No such permission or collection |

**Why "Shared: Yes" for what Gemini reads.** Sending data to a service provider that processes it only
for you isn't "sharing". On Gemini's **free tier**, Google may use what's sent to improve its products,
so it's declared as **shared, for app functionality**, until the app moves to the paid tier. Groq
processes typed quick-add text ephemerally without training.

## Financial features

**My app doesn't provide any financial features.** The app records expenses and IOUs between friends
and opens the user's own UPI app with a payment link; it never lends, holds or moves money.

If a reviewer pushes back, the cautious alternative is **Support services → Other** with:
"Records shared expenses and IOUs between friends; creates UPI payment links opened in the user's own
UPI app; does not lend, hold, or transfer money." Never tick mobile payments, money transfer or loans.

## Other App content items

- **Ads:** No ads.
- **Target audience and content:** 18 and over only. Turn on **Restrict minor access**.
- **Content rating:** answer the questionnaire as a finance/utility app with user-generated text
  shared only within private groups; no violence, gambling, or purchases.
- **App access:** Sign-in is optional (the app can be used locally without an account, but cloud sync
  and shared groups require signing in). The review account and its App access text are in docs/store-listing/reviewer-access.md; it is a Google account with
  sample data (a shared group, a split, a settlement).
- **Government app:** No. **News app:** No. **Health app:** No.
- **Category:** Finance. **Tags:** budgeting, expense tracker.

## Notes for the form (added 2026-11-19, audit P-05; one source of truth)

- **Visible to other users is not 'shared with third parties'.** Bill photos, group expenses, names and UPI IDs are visible to the people in the same group or split. Play treats that separately from sharing with a third party. In the form's notes write: 'Visible only to people the user chooses to group or split with.' Keep this consistent with the privacy policy.
- **Split-by-items link (new).** A host can share a link so friends tick items with no account. What is kept: the bill's items and prices, its total, the host's first name and the first name each guest types. Kept for 24 hours; deleted when the host finishes. No account ids, phone numbers or photos. Type: Personal info > Name (first names), Financial info > Purchase history, not shared with third parties, collected for app functionality.
- **Crash reports.** The owner decided (B-07) they are on by default with an off switch, once the Firebase project is confirmed to receive them. Until that is confirmed (issue #333) keep the declared 'optional, off by default' answer; change this row and the policy together when the default changes.
- **Free versus paid AI tier.** Every Gemini row above assumes the free tier. If the app moves to the paid tier (required for EU/UK users), the 'shared with Google for product improvement' rows change: update this file and the policy in one PR.
- **Camera.** The CAMERA permission is declared for the QR scan and the Bill photo flow. Frames are decoded on the phone and discarded; nothing is collected. Details in docs/store-listing/permissions.md.
- **Minimum-version table.** The app reads one public row (two version numbers) with no sign-in. No personal data.
