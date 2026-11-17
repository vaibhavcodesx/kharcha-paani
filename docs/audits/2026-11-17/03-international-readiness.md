# International readiness, 2026-11-17

The owner's new goal (2026-11-17): launch outside India too, naming the US, the UK and Russia, so
that a paid subscription tier later earns more. This report says how far the app is from that,
measured in the code, and what order makes sense.

**This changes two standing rules, so `PRD.md` needs an owner decision before any of it is built:**
the PRD describes the app as "for Indian friends", and work-order decision D5 says no pricing or
paywall work for the first three months. Nothing below touches pricing; it is only about making the
app usable and lawful elsewhere.

## The blunt summary

Today the app is not "India first", it is **India only**. A person in London or Chicago can install
it, but every amount is shown in rupees, the only way to pay a friend is UPI, the copy speaks
Hinglish, and the AI is told it is an Indian app. None of that is a bug; it is the product as
designed. Going international is a feature project of several weeks, plus legal work, not a
store-listing switch.

What the code says (counted on `origin/main` at `8839b3f`):

| Assumption | Evidence |
|---|---|
| One currency, the rupee | `₹` appears 69 times in 28 Kotlin files. `ui/format/Money.kt` hard-codes the sign and Indian digit grouping (1,00,000) and abbreviates to lakh and crore. **No table has a currency column**, in Room or in Postgres. |
| Paying a friend means UPI | "upi" appears 606 times in 56 Kotlin files and 42 times in `strings.xml`: the profile's UPI ID, the settle-up QR, ask-to-pay links, the `upi://` query in the manifest. There is no other settlement method. |
| English with Hinglish | One `values/strings.xml` (1,126 lines), no translations. 26 strings use "hisaab", "paani", "chai" and the like. |
| The AI is Indian | `domain/AiPrompts.kt:42-49`: "an Indian expense-splitting app", "Indian English and rupees", "never write a dollar sign". The on-phone parser and statement parser expect Indian formats and bank layouts. |
| India time | The AI daily cap resets at midnight `Asia/Kolkata` (`take_ai_read`). Speech recognition asks for Indian English. |
| Amount limits | The ceiling ₹9,99,99,99,999.99 and the "that's ₹10 lakh, right?" check are rupee-sized. |

The good news: strings are almost all in resources already (788 `stringResource` calls, the
hard-coded-text guard is in CI), money formatting goes through one object, and amounts on the
server are `numeric(12,2)`, which fits any two-decimal currency.

## What has to be built, in order

### I-01 · Currency (L, touches Room, Postgres, sync, AI, backup). The foundation.

Decide the model first; it is hard to change later:

- **Recommended for a first international version: one currency per user and one per group.** A
  user picks a home currency at first run (default from the phone's locale). A group has its own
  currency, fixed at creation. Balances are never added across currencies. No exchange rates.
- Multi-currency inside one trip with conversion is what Splitwise charges for. Leave it for the
  paid tier.

Work: a `currency` code (ISO 4217) on `groups` and on the user's settings, and on personal
`transactions` if you want a traveller to log a foreign expense; additive migrations on both sides
(Room v-next with schema JSON and migration test; a Postgres migration, **owner review**); existing
rows default to `INR`; `Money.format(amount, currency)` using the platform's `NumberFormat` for
grouping and symbol, with the Indian grouping kept for INR; minor units from the currency (yen has
none), which touches `roundPaisa`, `SplitRounding` and the item-split's `*_paise` columns; CSV export
and both backup formats carry the code; AI prompts take the currency as a parameter.

Risk: this touches the ledger and balance code the PRD says not to re-architect. It can be done
without changing the pattern, but it needs its own design note and the invariant tests that already
exist (T1–T3) extended to a second currency.

### I-02 · Settling up without UPI (M)

Outside India the settle-up screen has nothing to offer. For version one abroad: hide UPI unless the
group's currency is INR, and let "Mark as paid" stand alone with a free-text "how" (cash, bank
transfer, Venmo, Revolut). Later: a "payment link" field on the profile that takes a PayPal.me,
Venmo or Revolut link and is opened the way the UPI link is. Never move money in the app; that
keeps you out of money-transmitter rules everywhere and keeps the Play Financial features answer
simple.

### I-03 · Language and tone (M, then ongoing)

- Move the Hinglish strings into a `values-en-rIN` resource set and write plain-English defaults.
  The warmth can stay; "hisaab" cannot be the default for someone in Leeds.
- The product name is a decision for the owner. "Kharcha Paani" is a strong name in India and means
  nothing abroad. Play allows a different title per country.
- Russian needs a real translation (Cyrillic, grammatical plurals: Android `plurals` with `one`,
  `few`, `many`), and a check that no layout assumes short English words.
- Dates and numbers: 26 uses of `Locale.getDefault()` and 22 of a fixed locale. Audit each; a fixed
  locale is right for parsing and file formats and wrong for display.
- Speech recognition language should follow the phone, not `en-IN`.

### I-04 · AI prompts and parsers (M)

The prompts, the on-phone quick-add parser's keywords (payment methods like "gpay", "paytm"),
categories, and the bank-statement reader are tuned for India. Abroad: parameterise the prompts by
country and currency, add payment-method words per region, and **ship statements import as
India-only at first** (foreign bank PDFs will misread, and a wrong import is worse than none).

### I-05 · Server and time (S)

Reset AI caps at midnight UTC or per user time zone instead of `Asia/Kolkata`. The Supabase project
is in Tokyo: fine for India and East Asia, 200–300 ms away from Europe and the US east coast, which
the offline-first design mostly hides. Do not move regions for this.

## Law and policy, by market

Not legal advice. These are the items an owner should take to a lawyer or at least read first-hand.

### Everywhere outside India: the Gemini free tier

Google's Gemini API terms require the **paid** service for apps offered to users in the EEA,
Switzerland and the UK; the free tier (where prompts may be used to improve Google's products and
seen by reviewers) is not allowed there (**verify** on the current "Gemini API Additional Terms").
Decision D2 (free tier for the first months) therefore cannot extend to Europe. Sending bank
statements and voice recordings to a free tier would also be very hard to defend under GDPR. The
simplest consistent answer: move to the paid tier before any launch outside India. Check Groq's data
terms in the same pass; the privacy policy currently states Groq keeps nothing, which should be
backed by a setting or a contract, not an assumption.

### EU and UK (GDPR, UK GDPR)

- A lawful basis per purpose, written in the policy (contract for the ledger, consent for AI reads
  and crash reports). The current policy is written for India's DPDP Act only.
- The rights: access, portability (the JSON export nearly covers it), erasure (done), objection.
- Processors and transfers: Supabase (Japan), Google, Groq (US), Firebase. You need their data
  processing agreements accepted and the transfer mechanism named in the policy.
- A non-EU business that regularly offers a service to people in the EU must appoint an EU
  representative (Article 27), and separately one for the UK. Small paid services provide this.
- Friends who are not users: the app stores names of people who never agreed to anything ("friends
  who aren't on the app"). Under GDPR that is personal data of third parties; keep it minimal, never
  message them from the server (already the rule), and say it in the policy.
- Children: the 18+ rule already keeps you clear of most of it.

### United States

No single federal law. The state privacy laws (California and others) have size thresholds a new
app will not meet, but their expectations (a clear policy, deletion, no selling) are already met.
Keep the 18+ gate so COPPA does not apply. Do not describe the app as a payments or lending product;
"records who owes what" is the safe line, as in India. Sales tax on subscriptions is handled by
Google as the merchant of record in most states.

### Russia: reconsider

- **Google Play billing has been suspended for users in Russia since March 2022** (**verify** the
  current status). Paid apps and subscriptions cannot be bought there through Play, which removes
  the reason for choosing Russia, revenue.
- Russia's data-localisation law (242-FZ) requires personal data of Russian citizens to be first
  recorded on servers inside Russia. Supabase has no Russian region.
- Sanctions and service availability: Google sign-in works, but Google Cloud, Groq and others
  restrict or do not serve Russia; an AI feature that fails by country is a support burden.

A free listing in Russia is technically possible. As a revenue market it is the weakest choice on
the list. Canada, Australia, Germany or the UAE (large Indian diaspora, rupee-and-dirham use,
high spending) would each return more for less work.

### The paid tier, when it comes

Out of scope until the owner lifts D5, but two facts shape today's choices: digital subscriptions on
Play must use Play Billing (a 15% fee on the first $1M a year, **verify**), and a developer who
sells on Play has their address shown publicly on a personal account. The AI caps table
(`private.ai_read_caps`) is already the right place to hang plan limits.

## A sequence that keeps the India launch on track

1. **Now:** launch India as planned. Restrict the Play listing to India at first; countries can be
   added later with no penalty. Do not let this report delay the closed test.
2. **Design note first:** `docs/designs/multi-currency.md` for I-01, reviewed by the owner, before
   any code. Update `PRD.md` in the same PR to say what "international" means.
3. **Then** I-01, I-02, I-03's string split, I-05. That is the minimum for an English-speaking
   market.
4. **Soft-launch one market** where the diaspora overlaps with the existing users (UAE, UK or
   Canada), English only, statements import off, paid Gemini tier on, updated policy.
5. **After that** more languages and markets, and the paid tier once the free period ends.
