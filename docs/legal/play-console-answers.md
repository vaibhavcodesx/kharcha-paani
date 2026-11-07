# Play Console: App content answers

Updated 6 November 2026 for 1.7.0. Based on the merged code and the Play research in
[../production-readiness/audits/play-store.md](../production-readiness/audits/play-store.md) §3–§4.
**For owner review**. Not legal advice.

## Privacy policy

- **Privacy policy URL:** `https://vaibhavcodesx.github.io/kharcha-paani-site/privacy.html`
  (once the site in `site/` is published; see `site/README.md`).

## Data safety

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
| Photos and videos → Photos | Yes (optional) | **Yes** | **No** | Bill photos attached to expenses are backed up to the user's account in cloud storage (re-encoded, stripped of EXIF/location metadata, at most 1 MB) and shared with groupmates on splits unless "Share bill photos" is turned off in Settings. Also read by Gemini when AI bill scanning is used. Deleted when the expense, photo, or account is deleted. |
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
  and shared groups require signing in). Give the reviewer account's details: a Google account with
  sample data (a shared group, a split, a settlement).
- **Government app:** No. **News app:** No. **Health app:** No.
- **Category:** Finance. **Tags:** budgeting, expense tracker.
