# gemini-proxy

The app's only way to Google Gemini (plan T1.3, decision D2). It holds the Gemini key as a secret,
checks each person's AI reads for the day, and passes Google's reply back. Code: `index.ts`;
statement cleaning: `statement.ts` (tests: `deno test statement_test.ts`).

## Turning it on (owner, plan step O2)

Order matters: the app release that uses the proxy must not reach people before the function is live.

1. **Apply the migration** `supabase/migrations/20261025000000_ai_daily_reads.sql` (the caps and the
   `take_ai_read` function). Its tests are in `supabase/tests/ai_daily_reads_test.sql`.
2. **A new Gemini key, for the server only.** Google Cloud console → APIs & Services → Credentials →
   Create API key. Restrict it to the **Generative Language API**, with no application restriction
   (it's called from Supabase, not the phone). Set a quota cap and a budget alert. If you move to the
   paid tier (D2's recommendation), enable billing on that project first.
3. **Set it as the function's secret:** Supabase dashboard → Edge Functions → Secrets →
   `GEMINI_API_KEY` = the new key.
4. **Deploy the function** with JWT verification on (the default):
   `supabase functions deploy gemini-proxy` from the repo root, or ask Claude to deploy it with the
   Supabase MCP once you've approved the PR.
5. **Check it:** in the app (a build from this branch), quick-add "chai 40": it fills in as before.
6. **Release** the app version that uses it.
7. **Delete the old key** that shipped inside every APK from 1.0.0 to 1.5.0 (Google Cloud →
   Credentials). From then on, an old APK's AI features stop working, and nothing else of it changes.

## The caps

Per person per day (midnight IST), in `private.ai_read_caps`, so they change without an app update:

| feature | per day |
|---|---|
| quick_add (typed or spoken) | 50 |
| bill (photos, screenshots) | 10 |
| statement | 3 |
| insights (questions and the trip read) | 20 |

To change one: `update private.ai_read_caps set per_day = 30 where feature = 'bill';`. A 0 turns a
kind off. When the free period ends, per-plan caps go here too (decision D5).

A read is spent when the request is accepted, before Google answers, so a failed Google call still
uses one, and a retry on the lighter model (after Google says it's overloaded) uses a second. With
these caps, nobody using the app normally gets close.
