# Reel Brain: launch-readiness items for Kharcha Paani

Source: Notion "Reel Brain" (read-only sweep, 2026-10-24). I listed all 164 rows of the Reel Knowledge data source by title, category and subtopic, then fetched every row that looked relevant. I also ran keyword searches over Reel Brain (Prompt Library, Resources, and the AI Digest Archive / AI Library) for: publish, launch, Play Store, App Store, security, API key, secrets, Supabase, RLS, legal, privacy, checklist, cold start, notifications, onboarding, retention, crash, analytics, monitoring, subscription, paywall and monetise.

Legend for applicability: **[A]** applies to a native Android app as is. **[A~]** applies in adapted form (the reel was about web or iOS, but the principle carries over). **[W]** web or website only. **[iOS]** Apple only.

Owner notes: the "Expense Tracker App" project page (https://app.notion.com/p/3cf56fa960568109abc3c7d308a49cc8) is a **sample template project**: a React/Python web app with a Vercel link. It contains no Kharcha Paani notes. The "Reel-brain" project page (https://app.notion.com/p/3fa56fa9605680448ac0f445d22b80fd) is **blank**. None of the reel pages have owner text in their "My notes" sections; they all still hold the placeholder.

Several reels promise more by DM ("comment X"). Those follow-ups are still "To do", so the full content was never captured: 20 Point Pre-Launch prompts, Security Checks Part 2 resource doc, Apple review guideline list, Cloudflare skill link (the link is known anyway), and ARTEMIS link. Prompt 4 ("Deep Security Audit for Complex Logic": payments, auth and inputs) from the 5 Security Checks reel was not captured anywhere either.

---

## Part 1: Per-reel extraction

### Store / approval

**1. 5 Essential Checks to Get Your iOS App Approved on the App Store in Under 12 Hours**
https://app.notion.com/p/3f356fa9605681469851eb09f06a8ee2
- Store metadata (name, description, screenshots) must exactly match the real UI, with no over-promising. [A~]
- Privacy Policy, Terms of Use and a Developer Support contact are bare minimum. [A~] (Play needs a privacy policy URL, a Data safety form and a contact email.)
- No blank or "coming soon" screens. No crashes or lag for a first-time user. [A]
- Every permission prompt (camera, storage, notifications) must state why it is needed and how it is used. [A]
- Digital goods consumed in the app must use the store's billing; no external payment links. [iOS], with a Play equivalent [A~]. Note: settling real-world debts between friends over UPI is not "digital goods", but keep any future premium tier on Play Billing.

**2. Converting Any Website into a Mobile WebView App Using Capacitor**
https://app.notion.com/p/3fe56fa9605681ba83e1fea16e591185
- WebView wrappers are prototypes, not native apps. Not relevant to Kharcha Paani beyond "a thin wrapper is a weak store submission". [W]

### Legal & compliance

**3. 20 Legal & Compliance Checklist Items to Have Claude Add to Your App** (@krishanu.builds)
https://app.notion.com/p/3f756fa9605681768f18f18c8babb6cc
1. Privacy policy [A]
2. Terms of service [A]
3. Refund policy [A~] (only if you ever charge)
4. Cookie policy [W]
5. Cookie consent banner [W] (Android equivalent: consent before optional analytics or crash SDKs [A~])
6. Check form consents [A]
7. Don't collect unnecessary data (data minimisation) [A]
8. Audit third-party SDKs (what each one collects and sends) [A]
9. Remove dark patterns [A]
10. Remove hidden fees [A]
11. Remove fake reviews [A]
12. Remove unsupported claims (store listing and in-app copy) [A]
13. Accessibility alt text (Compose `contentDescription`) [A]
14. Fix colour contrast ratio [A]
15. Keyboard navigation (Android: TalkBack, focus order, touch-target size) [A~]
16. Show business or developer details [A]
17. Age consent if you collect children's data [A] (Play target-audience declaration)
18. Unsubscribe link in emails [A~] (only if you send email)
19. License every font and image you use [A]
20. Offer a data deletion request option [A] (Play requires in-app account deletion plus a web deletion URL)

### Security of vibe-coded apps

**4. 5 Security Checks and Prompts for Vibe-Coded AI Apps** (@mayankshah_ai, Hinglish)
https://app.notion.com/p/3f756fa9605681acaf89cf61fefb9e04 (prompts also stored in the Prompt Library)
- No secrets in client code. Bots scan GitHub continuously. Cited case: a $55k Google Cloud bill after a Gemini key leaked. [A] Android: anything in the APK or BuildConfig is public, so a Gemini key shipped in the app is extractable.
- Supabase: the anon key is OK client-side **only with RLS on every table**. The service-role key must never be in the client. [A]
- Keep OAuth client secrets and JWT signing secrets on the server. [A]
- Keep `.env` in .gitignore, ship a `.env.example`, rotate any secret that was ever committed (git history keeps it). [A] (Android: `local.properties` / secrets plugin.)
- Map every PII collection point and trace where the data goes. [A]
- Scrub PII and tokens from all logs, then replace them with [REDACTED]. [A] (Logcat and crash reports.)
- List exactly what user data each third-party SDK (analytics, crash, push, AI) receives, and strip extra fields. [A] (This matters for what is sent to Gemini.)
- Hash passwords with bcrypt/argon2/scrypt. [A~] (Not applicable with Google sign-in only.)
- App refuses to start when critical config is missing. Debug mode off by default. [A]
- Remove debug logs, commented-out code, TODOs that mention security bypasses, hard-coded test credentials and test-only endpoints before release. [A]
- Error responses carry a generic message plus a correlation ID. Details go to server logs only. [A]
- Calculate prices and payment amounts on the server, never trust the client. [A] (Balances and settle amounts must be validated server-side.)
- Rate-limit sign-up and sensitive endpoints so bot farms cannot spam accounts. [A]
- Attacker review: IDOR (change an ID in a request and you get someone else's data), access without a token or with an expired token, default admin accounts, privilege escalation via a role field, feature abuse (1000 requests a minute), script/SQL injection in text fields, exposed admin panels, .env, .git or API docs, and business-logic abuse (negative amounts, discount tampering). [A]

**5. 6 Prompts to Hack-Proof Your Vibe Coded App** (@saban.talks)
https://app.notion.com/p/3fa56fa9605681c0b8f0c8b44ba21393
- Tiered rate limits: strict on auth (per IP plus per account, exponential backoff rather than hard lockout), moderate on public endpoints, looser on authenticated actions. Make thresholds configurable. [A] (Supabase Auth rate limits, Edge Function limits.)
- Validate every input against a strict schema (type, length, format) and reject anything that does not match, instead of only escaping it. [A]
- Scan for hard-coded keys, tokens and passwords. Nothing sensitive in the frontend or git. [A]
- Dependency audit: known CVEs, severity, update or replace. [A] (Gradle dependencies.)
- Users never see stack traces, file paths or raw DB errors. Log the full detail server-side. [A]
- File uploads: validate type, size and *content*, not just the extension. Store in isolated, non-executable storage. [A] (Receipt photos and PDFs, Supabase Storage bucket policies.)

**6. How to Improve the Security of Your Vibe Coded Projects (Part 2)** (@kema_the_engineer)
https://app.notion.com/p/3f756fa960568173a5edd2566c1ab161
- Enforce HTTPS everywhere, HSTS, Secure cookies, no mixed content. [W] (Android equivalent: cleartext traffic disabled through the network security config [A~].)
- Session cookies HttpOnly/Secure/SameSite=Strict, 30-minute idle expiry, destroy on logout, rotate the ID on login. [W] (Android equivalent: tokens in encrypted storage, cleared fully on sign-out, refresh-token handling [A~].)
- Every API endpoint verifies authentication and authorisation, and returns only the fields the client needs (no over-fetching). [A] (Supabase `select` column lists, RLS.)
- Clean error messages in production. [A]
- Dependency audit: remove unused, unmaintained and vulnerable packages. [A]

**7. Security Checks for your AI-built App (Part 2)** (@byarnieverma). Items 19 to 36. Part 1 (items 1 to 18) is not saved, and the resource doc DM is not captured.
https://app.notion.com/p/3f156fa96056819c9adcea4df336c479
- XSS [W]; CSRF [W]; insecure file uploads [A]; path traversal [A]; SSRF [A~] (Edge Functions that fetch URLs); broken password reset [A~]; weak session management [A]; JWT secrets [A]; permissive CORS [A~] (Edge Functions); rate limits [A]; exposed environments [A]; default credentials [A]; unsigned webhooks [A~] (verify signatures if you add any); frontend payment checks [A]; IDOR/BOLA [A]; APIs plus user input [A]; exposed logs [A]; exposed source maps [W] (Android analogue: ship R8-minified release builds with no debug symbols in the APK [A~]).

**8. Cloudflare Security Audit Skill for Coding Agents** (@wdf_ai)
https://app.notion.com/p/3f756fa9605681d2b336ea87cc01a7a3
- Install `npx skills add https://github.com/cloudflare/security-audit --skill security-audit`, then say "security audit this codebase". It runs six phases: recon, hunting, validation, structured report, independent verification, neutral reporting. [A]
- The creator's real findings are worth checking in Kharcha Paani: (a) purchased credits stored on the device that could be edited and sent to the server, and (b) a corrupted-data path that wiped a user's server progress. [A] (Check sync and conflict handling so a bad client state cannot overwrite the server ledger.)

**9. Autonomous Web App Penetration Testing with Shannon** (KeygraphHQ)
https://app.notion.com/p/3fb56fa9605681529d41e64a42d55d8e
- An open-source autonomous pentest of a web app or API URL plus source code. It writes a Markdown vulnerability report and takes hours. [A~] (Usable against the Supabase/Edge Function API surface, not the APK.)

**10. Reverse Engineering Android APKs Using apk-reverse Agent Skill**
https://app.notion.com/p/3f756fa9605681f88135e550524b9e9d
- An agent can unpack and patch an APK (demo: game coins set to over 1 billion). Lesson: assume anyone can read and modify your APK. Keep no secrets in it, trust no client-side values, and keep authoritative logic on the server. [A]

**11. (AI Library) Scan your own app for security holes with Visa's open-source agentic harness**
https://app.notion.com/p/3fd56fa9605681749454e2e6695d4bf9
- Run `vvaharness setup`, then `estimate`, then `scan --stop-after s9` (detection only, no edits). Triage findings.json with Claude, and verify each finding by hand. Start with one module. Never run remediate mode on uncommitted code. The free fallback is Android Lint plus Gradle dependency checks. [A]

**12. (AI Library) Meta paused sharing in Muse after shared chats were discoverable via passive DNS**
https://app.notion.com/p/40056fa96056815eae34ca1d80342ec4
- Share URLs built from a title plus a random code leaked through passive DNS. 6,800+ were found and opened without login. Lesson: share links must not embed meaningful text, must require auth or expire, and must be revocable. [A] (Relevant to ask-to-pay / invite / share links.)

### Testing / QA

**13. 20 Point Pre-Launch Website Checklist** (@krishanu.builds). The promised prompt DM was not received.
https://app.notion.com/p/3f156fa96056819db948d3d16e9b56ed
- Privacy policy page [A]; terms page [A]; secrets off the front end [A]; force HTTPS [A~]; cookie banner [W]; meta titles and descriptions [W] (store listing copy [A~]); social preview image [W] (store feature graphic and share previews [A~]); favicon [W] (adaptive and monochrome app icon [A~]); sitemap/robots [W]; alt text [A]; compress images [A] (APK/AAB size, WebP); check load speed [A] (cold start); colour contrast [A]; mobile friendly [A~] (small screens, font scaling, foldables); custom 404 [W] (Android: designed empty and error states [A~]); fix broken links [A] (privacy, support and deep links); form validation [A]; spam/bot protection [A]; set up analytics [A]; one clear call to action per screen [A].

**14. Claude Prompt for Pre-Launch Website Checklist and Testing** (@krishanu.builds)
https://app.notion.com/p/3fe56fa96056811da5a8dad7495284a4
- Go/no-go list: tested on mobile, every link works, contact form works, loads in under 3 seconds, images compressed, HTTPS, Chrome and Safari tested, no typos, WhatsApp button works, domain email, analytics, Google indexing, link preview, backups on, payment page tested. [A~] The items that carry over: real-device tests on low-end phones, every link and intent works (UPI intent, WhatsApp share), typo pass (English and Hinglish copy), support email on your own domain, analytics, WhatsApp link-preview text for shared invites, **backups on** (Supabase PITR or daily backups), and an end-to-end test of the settle-up/UPI flow.

**15. Google ARTEMIS: AI Agent for Android Testing**
https://app.notion.com/p/3fa56fa96056812286b1d63e52abe690
- An open-source Google agent that drives a real Android phone over ADB in plain English, with an MCP server for Claude Code. Use it to script "build, install, sign in with a test account, check for popups, screenshot every screen" smoke tests. Fast and Pro (plan, act, verify) modes. Repo link is still pending (suggested github.com/google/artemis, unverified). [A]

### Performance

**16. 10x App Cold Start Speed Optimization Techniques** (@arjay_the_dev; iOS example)
https://app.notion.com/p/3fa56fa96056816bb469dad3b7d3f681
- Measure cold start on a **bad network** (throttled or in-flight Wi-Fi), not home Wi-Fi. A sequential request waterfall went from 2.8 s to 35 s. [A]
- Optimistic launch: trust the locally cached session and show home at once. Validate the session in the background and sign out only on 401. [A] (supabase-kt session restore must not block first frame.)
- Cache a small home-screen snapshot locally so zero network requests happen before first paint. [A] (Room already gives this. Make sure the first frame reads Room, not the network.)
- Fire independent requests in parallel (`async`/`awaitAll`), so total time is the slowest call rather than the sum. [A]
- Accept the trade-off: briefly stale data for an instant UI. [A]

### UX polish ("doesn't feel vibe-coded")

**17. 5 Skills That Kill AI Slop** (@ux.amish)
https://app.notion.com/p/3f156fa960568129ae19f405e487fac5
- Taste Skill (stronger visual direction) [A~]; Impeccable (hierarchy, typography, spacing, colour, motion) [A~]; Vercel Web Design Guidelines (final UX audit: focus states, forms, accessibility, copy, edge cases) [A~] (the checklist ideas carry over, the rules are web-specific); Emil Kowalski design-engineering skill (animation, easing, micro-interactions, the craft layer that removes the vibe-coded feel) [A~]; Anti UI Slop / uizze (build from real product components and tokens rather than generic SaaS UI) [A~].

**18. Fixing AI-Generated Web Design 'Slop' with Apple Design Skills**
https://app.notion.com/p/3f856fa96056814aac07faad0c8bddc2
- Force a design-principles skill on all generated UI. [A~] (For Kharcha Paani, the Material 3 / android-ux skills fill this role.)

**19. Using Claude to Audit and Delete Dead Code After Vibe Coding**
https://app.notion.com/p/3f756fa9605681e0b2c8f0e465969393
- After every feature, prompt: find dead code, duplicate logic, unused components and unnecessary complexity that was just added, then delete it. [A]

Other UI-resource reels (5 Claude Code Plugins for High-End UI Design, Top 10 Claude Design Skills, 5 Free Tools to Turn Claude into an AI Frontend Designer, Fresh Dev Tools Part 9, Autonomous AI Web Design Workflow) are web-frontend tool lists with no concrete checklist items. [W]

### Launch / marketing / post-launch

**20. 10 Essential Steps Right After Launching a Website** (@udayan.builds)
https://app.notion.com/p/3fd56fa96056818099a8ebdb8523dd6c
- Search Console, sitemap, Bing Webmaster, robots.txt, Analytics, Google Business Profile, unique titles, phone test, PageSpeed, review sites. [W] For the app: a landing or privacy-policy site on its own domain, analytics, testing on real phones, and getting reviews (in-app review prompt) [A~].

**21. 100+ Places to Post Your Startup** (GitHub list)
https://app.notion.com/p/3f256fa9605681a8859cd0167c6a6ec1
- Launch-directory and subreddit list for first users. The repo link DM is still pending. [A]

**22. Brag** (https://app.notion.com/p/3f156fa9605681d6bb59fe3e4e653d5a) and **onetake** (https://app.notion.com/p/3ff56fa9605681b3ad27c30af9d79c57): Claude skills that generate a product-launch video from the codebase. Useful for the store promo video. [A]

**23. Technical SEO Checklist Prompt for Vibe-Coded Websites**
https://app.notion.com/p/3fd56fa960568190aa29c2ceff7dc7e0
- Covers only the landing page. [W]

### Monetisation / payments

**24. YouPI-PG: Open-Source Self-Hosted UPI Payment Gateway** (two saves)
https://app.notion.com/p/3fc56fa96056816d891cdb9b8ca64227 and https://app.notion.com/p/3fe56fa960568152a15ac5923f4234ae
- Zero-fee UPI collection through a dynamic QR or intent link. It confirms payment by reading bank SMS and email alerts. [A~] It is relevant as an idea, but confirming payments by parsing SMS is fragile and SMS permissions are heavily restricted on Play. Kharcha Paani should keep UPI as "open the UPI app plus mark settled", not as payment verification.
- Resource: Polar (https://app.notion.com/p/3f756fa96056819791a5eca5805e2dcd) handles payments and subscriptions for web SaaS. [W]

### Process

**25. The Premortem Prompt Framework** (https://app.notion.com/p/3f856fa960568107aa82e3d91ee3a9bc)
- Ask the model to imagine the launch failed 6 months on and explain why. Gives a cheap risk list before launch. [A]

**26. (AI Library) Forecast a real deadline with a Monte Carlo prompt: your Android app launch** (https://app.notion.com/p/40356fa960568170b65cf51df80d97e8)
- Planning aid only. [A]

**27. (AI Library) Cheapest security wins** (https://app.notion.com/p/40356fa9605681788fd9f6a56250a961)
- Authenticator-app 2FA (not SMS) on the email, GitHub and Google accounts that own the Play Console and Supabase. [A]

### Topics with NO saved reel

Nothing in Reel Brain covers: **push notifications / reminders, retention loops, onboarding flows, crash reporting tools (Crashlytics/Sentry), Play Store-specific policy (Data safety form, closed-testing requirement, target API level), in-app reviews, or app monetisation models (subscriptions, paywalls, ads)**. The only analytics mention is "set up analytics" in the web checklists. These areas need their own research, outside the owner's saved reels.

---

## Part 2: De-duplicated master checklist

Sources are cited by the reel numbers in Part 1. Items marked [W] or [iOS] only are left out unless they have an Android analogue.

### Store / policy
- [ ] Store listing (name, short and full description, screenshots, video) shows only the real current UI, with no over-promising or unsupported claims. **[A]** (1, 3)
- [ ] No placeholder or "coming soon" screens, and no dead buttons, in the release build. **[A]** (1)
- [ ] A first-run user on a fresh install sees no crash, no jank and no blocking spinner. **[A]** (1, 16)
- [ ] Every runtime permission has a pre-prompt rationale (why it is needed and how it is used), requested in context, and the app degrades gracefully on denial. **[A]** (1)
- [ ] Any future digital purchase (premium tier) goes through Google Play Billing, with no external payment links for digital goods. UPI debt settlement between friends is out of scope for this rule. **[A~]** (1)
- [ ] Privacy policy URL, support contact email and developer details are in the listing and inside the app. **[A]** (1, 3, 13)
- [ ] Target-audience / age declaration done. If under-13s are possible, handle consent. **[A]** (3)
- [ ] Store promo video and feature graphic (Brag or onetake skills could generate them). **[A]** (22)

### Legal
- [ ] Privacy policy that matches actual data flows: Supabase, Gemini, Google sign-in, any analytics or crash SDK. **[A]** (3, 4, 13)
- [ ] Terms of service. **[A]** (3, 13)
- [ ] Refund policy, only if anything is ever charged. **[A~]** (3)
- [ ] Consent screen or toggle before any optional analytics, crash or AI data sharing (the Android version of a cookie banner). **[A~]** (3, 13)
- [ ] Form consents are explicit, for example sending a receipt image to AI parsing, or contact access. **[A]** (3)
- [ ] Data minimisation: collect only fields that are needed, and send Gemini only what the parse needs. **[A]** (3, 4)
- [ ] Audit every third-party SDK for what it collects. Keep the results for the Play Data safety form. **[A]** (3, 4)
- [ ] In-app account and data deletion, plus a web deletion request URL. **[A]** (3)
- [ ] No dark patterns, hidden fees or fake reviews/testimonials. **[A]** (3)
- [ ] All fonts, icons and illustrations are licensed. **[A]** (3)
- [ ] Unsubscribe link, only if transactional or marketing email is sent. **[A~]** (3)

### Security
- [ ] **No secrets in the APK.** Assume the APK will be decompiled. The Gemini API key, the service-role key and any OAuth secret must live server-side (Edge Function proxy). Only the Supabase anon key ships, and only with RLS. **[A]** (4, 5, 10, 13)
- [ ] **RLS enabled on every Supabase table and storage bucket**, with policies tested for cross-user reads and writes (IDOR/BOLA: change a group, expense or user ID and confirm you are denied). **[A]** (4, 6, 7)
- [ ] Every API path or RPC verifies auth and membership, and returns only the needed columns (no `select *` over-fetch). **[A]** (6, 7)
- [ ] The server is authoritative for money: validate amounts, splits and settlements server-side or with DB constraints. Never trust client-computed values. Reject negative or overflow amounts. **[A]** (4, 7, 8, 10)
- [ ] Sync/conflict paths cannot let a corrupted or malicious client overwrite or wipe server data. **[A]** (8)
- [ ] Rate limits on auth, invites/ask-to-pay, AI parse calls and file uploads (per user plus per IP, backoff). Make them configurable. Protect the Gemini bill. **[A]** (4, 5, 7)
- [ ] Strict schema validation of every input (length, type, format), both client- and server-side. Reject rather than sanitise. **[A]** (5, 7)
- [ ] Upload safety: check content and size (not just the extension) for receipt images and PDFs, use an isolated bucket and private access. **[A]** (5, 7)
- [ ] Share, invite and ask-to-pay links: unguessable, no meaningful text in the URL, auth-gated or expiring, revocable. **[A]** (12)
- [ ] Tokens kept in encrypted storage. Sign-out clears the session and the local data that should go. Cleartext traffic disabled. **[A~]** (6, 7)
- [ ] No PII, tokens or amounts linked to identity in Logcat, analytics or crash reports. Strip debug logs from release (R8 / `Log` stripping). **[A]** (4, 7)
- [ ] Generic user-facing errors with no stack traces, SQL or paths. Full detail goes only to server logs or the crash tool. **[A]** (4, 5, 6)
- [ ] Release build hygiene: debug flags off, no test accounts or test endpoints, R8 minify on, no default credentials. **[A]** (4, 7)
- [ ] App fails safely when required config is missing (no silent half-working state). **[A]** (4)
- [ ] Dependency audit (Gradle): known CVEs, unmaintained and unused libraries. **[A]** (5, 6)
- [ ] `.gitignore` covers secrets (`local.properties`, keystores, `google-services` as appropriate). Rotate anything ever committed. **[A]** (4, 5)
- [ ] Webhooks, if any are added later, verify signatures. Edge Functions use tight CORS and no open URL fetch (SSRF). **[A~]** (7)
- [ ] Run at least one automated audit: the Cloudflare `security-audit` skill, and/or the Visa harness in detection-only mode, then triage by hand. **[A]** (8, 11)
- [ ] 2FA (authenticator app) on the Google, Play Console, Supabase and GitHub accounts. **[A]** (27)

### Performance
- [ ] Measure cold start on a throttled or bad network and a low-end device, not just fast Wi-Fi. **[A]** (16, 13)
- [ ] First frame renders from local Room data with zero network calls before first paint. **[A]** (16)
- [ ] Optimistic session: restore the cached session instantly, validate in the background, sign out only on a real 401. **[A]** (16)
- [ ] Independent startup and sync requests run in parallel, not as a waterfall. **[A]** (16)
- [ ] Compress bundled images and assets (WebP). Keep AAB size small. **[A]** (13, 14)

### Reliability / testing
- [ ] Real-device smoke test of every flow: install, Google sign-in on a **release-signed** build, add expense, AI parse, settle via UPI intent, share/invite, offline then online, sign out. **[A]** (14, 15)
- [ ] Automate the smoke test with an agent driving a real phone (ARTEMIS over MCP) or instrumented tests, and take screenshots of each screen. **[A]** (15)
- [ ] Every outbound link and intent works: privacy, terms, support email, UPI apps, WhatsApp share and its preview text. **[A]** (13, 14)
- [ ] Typo and copy pass across all strings, including Hindi/Hinglish. **[A]** (14)
- [ ] Backups switched on and a restore tested (Supabase PITR or daily). **[A]** (14)
- [ ] Dead-code and duplicate-logic cleanup pass after each feature, and before launch. **[A]** (19)
- [ ] Premortem: "it is 6 months after launch and it failed; why?" **[A]** (25)

### UX polish
- [ ] Accessibility: `contentDescription` on icons and images, contrast meets WCAG AA, sensible TalkBack focus order, 48dp touch targets, large-font scaling. **[A]** (3, 13)
- [ ] One clear primary action per screen. **[A]** (13)
- [ ] Designed empty, error and loading states. No raw error text. **[A~]** (13, 6)
- [ ] Craft layer: consistent motion and easing, micro-interactions, haptics, consistent tokens and components, no generic default look. Use a design-audit skill pass (Impeccable or Emil-style principles; android-ux / Material 3 for Android). **[A~]** (17, 18)
- [ ] Form validation with inline, human messages. **[A]** (13)

### Notifications / retention
- [ ] No saved reel covers this. Only indirect items apply: permission rationale for notifications (1) and asking for reviews (20). Research separately: reminder notifications, POST_NOTIFICATIONS on Android 13+, re-engagement, in-app review API.

### Monetisation
- [ ] No reel covers app monetisation directly. Rules that apply: use Play Billing for digital goods (1), no hidden fees or dark patterns (3), and do the payment maths server-side (4). The YouPI-PG self-hosted UPI gateway idea relies on SMS parsing, which is a poor fit for a Play app (SMS permission restrictions); keep UPI as intent plus manual mark-as-settled. **[A~]** (24)

### Ops / monitoring
- [ ] Analytics set up, with consent and no PII. **[A]** (13, 14)
- [ ] Crash and error reporting with server-side correlation IDs (implied by 4: generic client error plus correlation ID; detail in logs). The tool choice is not covered in the reels. **[A~]** (4)
- [ ] Watch the Gemini and Supabase usage and billing dashboards for anomalies, and set budget alerts (the leaked-key bill story). **[A]** (4)
- [ ] Post-launch: get listed on launch directories, collect store reviews, keep a landing or privacy page on your own domain. **[A~]** (20, 21)
