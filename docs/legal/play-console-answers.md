# Play Console: App content answers

Prepared 25 October 2026 for the owner to enter (plan step O7). Based on the code at the time and the
Play research in [../production-readiness/audits/play-store.md](../production-readiness/audits/play-store.md)
§3–§4. **Re-check against the merged manifest and the app before submitting**: anything that changes
what leaves the phone (push notifications in N3, crash reports in T1.6, photos ever syncing) changes
these answers.

Not legal advice.

## Privacy policy

- **Privacy policy URL:** `https://vaibhavcodesx.github.io/kharcha-paani-site/privacy.html`
  (once the site in `site/` is published; see `site/README.md`).

## Data safety

**Does your app collect or share any of the required user data types?** Yes.

**Is all of the user data collected by your app encrypted in transit?** Yes (Supabase and Gemini are
HTTPS only; cleartext traffic is off).

**Do you provide a way for users to request that their data is deleted?** Yes: in the app, and at
`https://vaibhavcodesx.github.io/kharcha-paani-site/delete-account.html`.

### Data types

"Collected" means it leaves the phone. Purposes: **App functionality** and **Account management**
unless noted. Collection is **required** unless noted.

| Category → type | Collected | Shared | Ephemeral | Why / notes |
|---|---|---|---|---|
| Personal info → Name | Yes | No | No | Google sign-in, profile name groupmates see |
| Personal info → Email address | Yes | No | No | Google sign-in |
| Personal info → User IDs | Yes | No | No | The account's id |
| Personal info → Other info | Yes | No | No | Names you give friends who aren't on the app, and guests in group splits |
| Financial info → User payment info | Yes (optional) | No | No | Your UPI ID, if you add one; groupmates see it |
| Financial info → Purchase history | Yes | No | No | Expenses, splits, settlements, IOUs |
| Financial info → Other financial info | Yes | **Yes** (see below) | Yes for Gemini | Statement rows read by Gemini |
| Photos and videos → Photos | Yes (optional) | **Yes** | Yes | Bill photos and screenshots sent to Gemini to be read; the photo itself is kept on the phone only |
| Files and docs | Yes (optional) | **Yes** | Yes | Statement PDFs sent to Gemini |
| App activity → Other user-generated content | Yes | **Yes** for the Gemini part | Partly | Notes; quick-add text and Insights summaries sent to Gemini |
| App info and performance → Crash logs | Yes (optional, once T1.6 ships) | No | No | Purpose: **Analytics** (app stability). Off switch in Help & about |
| App info and performance → Diagnostics | Yes (optional, once T1.6 ships) | No | No | As above |
| Audio, Contacts, Location, Calendar, Messages, Device or other IDs, Web browsing, Health | No | | | No such permission or collection |

**Why "Shared: Yes" for what Gemini reads.** Sending data to a service provider that processes it only
for you isn't "sharing". On Gemini's **free tier**, Google may use what's sent to improve its products,
so it's declared as **shared, for app functionality**, until the app moves to the paid tier (decision
D2's recommendation). After the move, change those rows to "not shared".

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
- **App access:** sign-in is required. Give the reviewer account's details (plan step O8): a Google
  account with sample data (a shared group, a split, a settlement).
- **Government app:** No. **News app:** No. **Health app:** No.
- **Category:** Finance. **Tags:** budgeting, expense tracker.
