# Research prompt: free LLM APIs for Kharcha Paani

Paste everything below the line into a research agent (Claude Code, Codex or Antigravity) with
web access. The goal is a dated, sourced list of **free** LLM API tiers the app can use, alone or
several together. Cheap paid APIs are a separate pass later. Read
[`AI_API_COMPARISON_2026-10-27.md`](AI_API_COMPARISON_2026-10-27.md) first: it covers paid prices, and
this pass must not repeat it.

---

## Task

Find every platform that gives a **free API tier** for LLMs usable by a small Android app, and rank
them for Kharcha Paani's three AI jobs. "Free" counts if it recurs (daily or monthly) or is a large
one-time credit; say which. Free tiers with limits are fine, because the app can spread load across
several providers. Write the result to `docs/research/free-llm-apis-<YYYY-MM-DD>.md`.

### The app and its jobs

Kharcha Paani is an Indian expense and split app (Kotlin, Android). The phone calls a Supabase Edge
Function (`gemini-proxy`), which holds the API key and forwards to the model. Users are in India, and
typing is often Hinglish. Three jobs, in order of how much speed matters:

1. **Quick add (text):** "chai 40 cash", "dinner 1200 with Rohan and Ankit", "kal 5 sau ka petrol".
   The model returns JSON: amount, merchant, category, payment method, date, split people. The
   response has to come back fast, because people get frustrated waiting. Aim for under 1.5 s end to
   end from India.
2. **Bill photo (vision):** a phone photo of an Indian receipt (GST lines, discounts, several
   totals, sometimes Hindi or handwriting), returning the same JSON. Aim for under 4 s.
3. **Voice (audio):** a 3–10 s spoken line in English, Hindi or Hinglish. This is either audio in →
   JSON out directly, or speech-to-text followed by job 1.

Optionally, statements: a bank or UPI PDF statement with 20–200 rows, where speed matters less.

### What to find, per platform

For each platform, and for each model on it that can do one of the jobs:
- **Free tier terms:** RPM, RPD, TPM, TPD, monthly credits, expiry, whether a card is needed, and
  whether India sign-up and India billing work.
- **Capabilities:** text, image input, audio input or speech-to-text, strict JSON or structured output,
  context size.
- **Speed:** published or measured time to first token and tokens/second. Note any region (India,
  Singapore, US).
- **Data use on the free tier:** training on inputs, retention days, human review, ZDR options. The
  app handles real people's spending, so a free tier that trains on inputs is a real cost. Say it
  plainly.
- **Commercial use allowed on the free tier?** Rate limits per key, per account or per project. Is
  running several free accounts against the terms? Don't recommend anything that breaks them.
- **API shape:** OpenAI-compatible or not, so the proxy can switch providers with little code.
- **Stability:** preview vs production models, deprecation notices, outage history.

### Where to look (use all of them)

1. **Official docs and pricing pages** for each platform: the source of truth. Quote the exact limit
   rows with the page URL and the date read.
2. **Context7** (`resolve-library-id`, then `query-docs`) for each provider's SDK and API docs:
   rate-limit headers, structured output and vision/audio request formats.
3. **Reddit, through Scrapling** (`mcp__scrapling__*`, starting with `make_request`; use
   `stealthy_fetch` if blocked). Plain `reddit.com/.json` endpoints return 403, so render the pages.
   Search r/LocalLLaMA, r/LLMDevs, r/developersIndia, r/AI_Agents, r/OpenAI, r/ClaudeAI,
   r/GoogleGeminiAI, r/SaaS and r/androiddev. Queries: "free LLM API", "free tier rate limits",
   "Groq free tier", "Gemini free tier limits", "OpenRouter free models", "Cerebras free", "Mistral
   free API", "GitHub Models free", "Cloudflare Workers AI free", "free vision API", "free whisper API",
   "India sign up". Read the comments, not just titles. Treat Reddit as anecdotes: say so, and give
   the post's age.
4. **The web** (`firecrawl_search`, WebSearch): "awesome free LLM API" lists on GitHub (for
   example cheahjs/free-llm-api-resources), recent comparison blog posts, and provider changelogs.
   Check every claim from a list against the official page.
5. **YouTube** (agent-reach or web search for transcripts): recent videos comparing free APIs or
   measuring speed. Note the date; treat them as leads, not facts.
6. **Provider status pages** for outage history over the last 90 days.

Platforms to cover at least, adding any new ones you find:
- Google Gemini (AI Studio free tier)
- Groq
- Cerebras
- OpenRouter (`:free` models)
- Mistral (free mode)
- GitHub Models
- Cloudflare Workers AI
- Hugging Face Inference Providers
- Together (free models)
- SambaNova Cloud
- NVIDIA NIM (build.nvidia.com)
- Cohere (trial keys)
- Z.ai (free GLM Flash)
- SiliconFlow
- Alibaba Model Studio (trial)
- DeepInfra (trials)
- Fireworks (credits)
- Chutes
- Scaleway
- Novita
- AI21
- Kluster
- Hyperbolic
- Lepton
- Deepgram and AssemblyAI (free speech-to-text credits)
- ElevenLabs speech-to-text

### Output format

1. **A one-screen answer:** the best free setup for each job. Give a first choice and one fallback
   on a different provider, with the reason, and the daily capacity it gives (for example "about
   1,200 quick adds a day before the fallback is needed").
2. **One table per job:** platform, model, free limits, speed, JSON support, data use, India sign-up,
   source and date.
3. **Capacity maths:** at about 1,000 input and 300 output tokens per quick add, and about 2,000 + 500
   per bill, how many adds and bills a day each free tier covers. Then combined, with the app
   rotating providers within their terms.
4. **Catches:** training on free-tier data, terms that forbid apps or several accounts, preview
   models, keys that need a card.
5. **What to benchmark next:** the shortlist to put through the app's own test set (60 quick adds,
   30 bills, 20 voice lines, timed from India).
6. **Every claim gets a link and the date it was read.** If a page couldn't be read, say so; never
   guess a limit.

Not in scope: paid prices (already in the 2026-10-27 report), self-hosting, and anything that sends
real user data during the research.
