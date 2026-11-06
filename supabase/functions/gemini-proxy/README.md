# gemini-proxy

The app's only way to its AI (plan T1.3, decision D2). It holds the Gemini and Groq keys as secrets,
checks each person's AI reads for the day, and passes the reply back. Code: `index.ts`;
statement cleaning: `statement.ts`; Groq: `groq.ts` (tests: `deno test supabase/functions/gemini-proxy/`).

## Groq for typed quick adds (from 1.7)

A typed line arrives as model `groq-qwen3.8-27b`. The function turns it into a Groq chat request,
and turns Groq's answer back into Gemini's shape, so the app reads both the same way. If Groq can't
answer (no key, busy, slower than 8 s, down), the same request goes to `gemini-3.1-flash-lite` on the
same day's read. Voice, bills, statements and Insights never go to Groq.

Owner steps:
1. **Groq console → API Keys:** create a key for the server only.
2. **Supabase dashboard → Edge Functions → Secrets:** `GROQ_API_KEY` = that key. Until it's set, typed
   lines quietly go to Gemini.
3. **Groq console → Settings → Data Controls:** turn on **Zero Data Retention**. Without it, Groq can
   keep requests for up to 30 days for reliability and abuse checks.

Free tier, per organisation: 1,000 requests a day and 8,000 tokens a minute for qwen3.8-27b, which is
roughly six typed lines a minute across everyone. Past that, Groq answers 429 and Gemini answers instead.

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
