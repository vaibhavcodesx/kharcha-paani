# Low-cost AI APIs for Kharcha Paani
Research date: **27 October 2026 (Asia/Kolkata)**. Prices: USD, excluding applicable tax and conversion charges.

## Decision brief
The earlier three-provider comparison was too narrow. The strongest additional candidates are **Alibaba Qwen 3.7 Flash**, **Groq GPT-OSS**, **DeepInfra small models**, **Z.ai free Flash models**, **Fireworks Nemotron**, and **Mistral Small**.

My recommended evaluation order is:
1. Qwen 3.7 Flash through Alibaba's Singapore international deployment for typed expense parsing and receipt-image experiments.
2. Groq GPT-OSS 20B for fast text tasks; DeepInfra GPT-OSS 20B as another low-cost host.
3. DeepInfra Qwen3.5 9B for a small multimodal alternative.
4. Z.ai free Flash models for a genuine no-token-charge experiment, subject to account quotas and retention review.
5. Keep the app's current Gemini path as the quality baseline until the alternatives pass the same extraction cases.

This is a recommendation to benchmark, not a claim that these models are equally accurate or a change to the owner's existing provider decision. No API accounts were funded, no private financial records were sent, and no app/provider migration was performed.

## Scope and evidence
Fifteen services were examined: Alibaba Model Studio/Qwen, Groq, DeepInfra, Z.ai, Fireworks, Together, MiniMax, Mistral, Cloudflare Workers AI, Cerebras, OpenRouter, SiliconFlow, Google Gemini, DeepSeek, and xAI/Grok. This is a broad shortlist, not an exhaustive market census.

Official pages establish prices and contractual statements. Reddit provides self-reported experiences only. Scrapling was used for Reddit search and individual threads, including browser-rendered comments. Plain Reddit JSON endpoints returned 403, so the report does not claim access through them.

Some pricing tables disappear from plain HTML extraction. Browser rendering recovered Kimi and Cerebras tables; official Markdown pages recovered Z.ai, Fireworks, MiniMax and Mistral tables. SiliconFlow's current numeric model table remained unavailable in the retrieved page; it is screened separately rather than assigned a guessed price.

No inference latency, extraction accuracy, model availability in the owner's account, Indian-card acceptance, or account-specific quotas were tested.

## Price comparison
All token prices below are **per 1,000,000 tokens**, for standard synchronous inference unless stated otherwise. Cached input is a separate rate, not an automatic discount on every request. A dash means not established here.

### Direct providers and first-party model APIs

| Service / model | Input | Cached input | Output | Scope or qualification | Official source |
|---|---:|---:|---:|---|---|
| Alibaba Qwen 3.7 Flash | $0.030 | $0.006 implicit | $0.130 | Singapore international; prompt <=32K | [Model](https://www.alibabacloud.com/help/en/model-studio/qwen3-7-flash) |
| Alibaba Qwen 3.7 Flash | $0.100 | $0.020 implicit | $0.400 | Singapore; prompt >32K through 256K | [Model](https://www.alibabacloud.com/help/en/model-studio/qwen3-7-flash) |
| Alibaba Qwen 3.7 Flash | $0.200 | $0.040 implicit | $0.800 | Singapore; prompt >256K through 1M | [Model](https://www.alibabacloud.com/help/en/model-studio/qwen3-7-flash) |
| Alibaba Qwen Flash | $0.050 | $0.010 implicit | $0.400 | Singapore; prompt <=256K; text-only | [Model](https://www.alibabacloud.com/help/en/model-studio/qwen-flash) |
| Alibaba Qwen 3.8 Flash | $0.150 | — | $0.470 | Singapore international | [Pricing](https://www.alibabacloud.com/help/en/model-studio/model-pricing) |
| Alibaba Qwen3 VL Flash | $0.050 | — | $0.400 | Singapore international; prompt <=32K | [Pricing](https://www.alibabacloud.com/help/en/model-studio/model-pricing) |
| Z.ai GLM-4.7-Flash | Free | Free | Free | Text; account limits apply | [Pricing](https://docs.z.ai/guides/overview/pricing.md) |
| Z.ai GLM-4.5-Flash | Free | Free | Free | Text; account limits apply | [Pricing](https://docs.z.ai/guides/overview/pricing.md) |
| Z.ai GLM-4.6V-Flash | Free | Free | Free | Vision; account limits apply | [Pricing](https://docs.z.ai/guides/overview/pricing.md) |
| Z.ai GLM-5.3-Flash | $0.150 | $0.030 | $0.500 | Paid model; different from free GLM-4.x Flash | [Pricing](https://docs.z.ai/guides/overview/pricing.md) |
| Z.ai GLM-OCR | $0.030 | — | $0.030 | Document OCR; token-priced | [Pricing](https://docs.z.ai/guides/overview/pricing.md) |
| MiniMax M3.1 Flash | $0.150 | $0.015 | $0.600 | Standard | [Pricing](https://platform.minimax.io/docs/pricing/overview.md) |
| MiniMax M3 | $0.300 | $0.060 | $1.200 | Prompt <=512K; larger prompts double these rates | [Pricing](https://platform.minimax.io/docs/pricing/overview.md) |
| Mistral Ministral 3 3B | $0.100 | — | $0.100 | Small model; accuracy must be evaluated | [Pricing](https://docs.mistral.ai/inference/pricing.md) |
| Mistral Ministral 3 8B | $0.150 | — | $0.150 | Small model | [Pricing](https://docs.mistral.ai/inference/pricing.md) |
| Mistral Ministral 3 14B | $0.200 | — | $0.200 | Small model | [Pricing](https://docs.mistral.ai/inference/pricing.md) |
| Mistral Small 4 | $0.150 | — | $0.600 | Larger alternative to test for structured extraction | [Pricing](https://docs.mistral.ai/inference/pricing.md) |
| Kimi K2.6 | $0.950 | $0.160 | $4.000 | 262K context; approaching retirement | [Pricing](https://platform.kimi.ai/docs/pricing/chat) |
| Kimi K2.7 Code | $0.950 | $0.190 | $4.000 | Coding-focused; approaching retirement | [Pricing](https://platform.kimi.ai/docs/pricing/chat) |
| Kimi K3.1 Pro | $2.000 | $0.200 | $10.000 | 1M context; cache-write charges also exist | [Pricing](https://platform.kimi.ai/docs/pricing/chat) |
| DeepSeek Flash off-peak | $0.150 | $0.003 | $0.600 | Current direct model: DeepSeek-V4.1-Flash-1017 | [Pricing](https://api-docs.deepseek.com/quick_start/pricing/) |
| DeepSeek Flash peak | $0.300 | $0.006 | $1.200 | Time-dependent price | [Pricing](https://api-docs.deepseek.com/quick_start/pricing/) |
| DeepSeek V4 Pro off-peak | $0.660 | $0.022 | $1.980 | Pro lacks vision in the current table | [Pricing](https://api-docs.deepseek.com/quick_start/pricing/) |
| DeepSeek V4 Pro peak | $1.320 | $0.044 | $3.960 | Time-dependent price | [Pricing](https://api-docs.deepseek.com/quick_start/pricing/) |
| Gemini 3.1 Flash-Lite | $0.250 | $0.025 | $1.500 | Text/image/video input rate | [Pricing](https://ai.google.dev/gemini-api/docs/pricing) |
| Gemini 3.5 Flash-Lite | $0.300 | $0.030 | $2.500 | App's current fallback | [Pricing](https://ai.google.dev/gemini-api/docs/pricing) |
| Gemini 3.6 / 3.8 Flash | $0.750 | $0.075 | $3.750 | Prices through 31 Dec 2026 | [Pricing](https://ai.google.dev/gemini-api/docs/pricing) |
| Grok 4.3 | $1.250 | $0.200 | $2.500 | Prompt below 200K | [Pricing](https://docs.x.ai/developers/pricing) |
| Grok 4.7 | $2.000 | $0.500 | $6.000 | Prompt below 200K | [Pricing](https://docs.x.ai/developers/pricing) |

Alibaba prices vary by deployment scope and prompt size. Singapore Qwen 3.7 rates were cross-checked against both the model page and consolidated pricing page. Its model page lists image/text/video input and structured outputs. The older qwen-flash is text-only, so the shared “Flash” name does not establish equivalent capability.

Kimi K2 models close to newly registered users on **31 October 2026**, with retirement planned by the end of December. K3.1 Pro is not a budget substitute for lightweight extraction. K3 cache writes have separate TTL-dependent pricing. [Kimi pricing](https://platform.kimi.ai/docs/pricing/chat)

DeepSeek weekday peak windows are **06:30–09:30 and 11:30–15:30 IST**, excluding Chinese public holidays. All other times, including weekends, are off-peak. [DeepSeek pricing](https://api-docs.deepseek.com/quick_start/pricing/)

Gemini 3.6/3.8 standard input/output prices become **$1.50/$7.50 on 1 January 2027**. Output includes thinking tokens; explicit cache storage costs extra. [Google pricing](https://ai.google.dev/gemini-api/docs/pricing)

### Inference hosts: compare the model AND the provider

| Host / model | Input | Cached input | Output | Qualification | Official source |
|---|---:|---:|---:|---|---|
| Groq GPT-OSS 20B | $0.075 | — | $0.300 | Production model; text | [Models](https://console.groq.com/docs/models) |
| Groq GPT-OSS 120B | $0.150 | — | $0.600 | Production model; text | [Models](https://console.groq.com/docs/models) |
| Groq Qwen3.8 27B | $0.800 | — | $4.000 | Listed as preview; avoid production dependence | [Models](https://console.groq.com/docs/models) |
| DeepInfra GPT-OSS 20B | $0.030 | — | $0.140 | Standard; BF16 listing | [Model](https://deepinfra.com/openai/gpt-oss-20b) |
| DeepInfra Qwen3.5 9B | $0.100 | — | $0.150 | Standard; multimodal listing | [Model](https://deepinfra.com/Qwen/Qwen3.5-9B) |
| DeepInfra DeepSeek V4.1 Flash 1017 | $0.098 | about $0.003 | $0.294 | Displayed 51% promotion; no end date established | [Model](https://deepinfra.com/deepseek-ai/DeepSeek-V4.1-Flash-1017) |
| DeepInfra DeepSeek V4.1 Flash 1017 baseline | $0.200 | $0.006 | $0.600 | Displayed pre-promotion rates | [Model](https://deepinfra.com/deepseek-ai/DeepSeek-V4.1-Flash-1017) |
| Fireworks Nemotron 3.5 Lightning 30B A3B | $0.050 | $0.010 | $0.200 | Standard | [Pricing](https://docs.fireworks.ai/serverless/pricing.md) |
| Fireworks GLM 5.3 Flash | $0.150 | $0.030 | $0.500 | Standard; US-only variant costs more | [Pricing](https://docs.fireworks.ai/serverless/pricing.md) |
| Fireworks GPT-OSS 120B | $0.150 | $0.015 | $0.600 | Standard | [Pricing](https://docs.fireworks.ai/serverless/pricing.md) |
| Together GLM 5.3 Flash | $0.150 | $0.030 | $0.500 | Standard | [Pricing](https://www.together.ai/pricing) |
| Together MiniMax M3.1 Flash | $0.150 | $0.015 | $0.600 | Standard | [Pricing](https://www.together.ai/pricing) |
| Cloudflare Qwen3 30B A3B FP8 | $0.051 | — | $0.335 | Token-equivalent Workers AI rates | [Pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/) |
| Cerebras GPT-OSS 120B | $0.350 | — | $0.750 | Developer tier, not intended for production | [Pricing](https://www.cerebras.ai/pricing) |
| Cerebras Qwen3.8 27B | $0.990 | — | $1.490 | Developer tier, not intended for production | [Pricing](https://www.cerebras.ai/pricing) |

DeepInfra standard scheduling is best-effort; priority is 1.5x and Flex 0.8x where supported. Flex can be slower or unavailable. A cheap promotion is not a durable budget assumption. [DeepInfra pricing](https://deepinfra.com/pricing)

Fireworks offers 50% Batch discounts, but interactive expense entry needs synchronous service. Together and Fireworks prices are separate host offers, not the original model developer's contractual terms.

OpenRouter is a router with provider-specific prices and additional credit-purchase fees. It is useful for comparing routes and failover, but an advertised lowest model price is not a fixed quote for every backend. Its public fee documentation rendered dynamic placeholders, so no exact top-up fee is asserted here. [FAQ](https://openrouter.ai/docs/faq)

SiliconFlow's international pricing page advertises a $1 starter credit and no minimum commitment. Community references to free models on its China service do not establish identical international availability. Its live model rows were not consistently extractable; confirm the exact route, price and eligibility in its console. [Pricing](https://www.siliconflow.com/pricing)

## Free access and actual limits

| Service | Type of free access | Verified limitation |
|---|---|---|
| Groq | Recurring free API allocation | GPT-OSS 20B/120B: 30 RPM, 1,000 RPD, 8K TPM, 200K TPD in the public free-plan table |
| Z.ai | Models explicitly priced Free | GLM-4.7-Flash, GLM-4.5-Flash and GLM-4.6V-Flash; numeric quotas require account access |
| Cloudflare Workers AI | Recurring compute allowance | 10,000 neurons/day; not 10,000 requests |
| Gemini | Recurring selected-model free quota | Exact model quotas in AI Studio; limits shared at project level |
| Alibaba Model Studio | Trial tokens | Selected Singapore models list 1M free tokens, valid for 90 days under the stated eligibility clock |
| OpenRouter | Selected free models | 50 requests/day normally; 1,000/day after at least $10 in credit purchases |
| Cerebras | One-time promotional credit | $5 after adding a valid payment method; expires after 30 days; developer tier is evaluation-oriented |
| Kimi | Paid entry plus voucher | At least $1 recharge to start; cumulative $5 recharge earns a $5 voucher |
| SiliconFlow international | Starter promotion | Pricing FAQ advertises $1 in credits; not proof of perpetual free inference |
| Mistral | Free-mode included usage | Current docs allow API keys and included usage; exact allowance and limits appear in account settings |
| DeepSeek / Grok / MiniMax / DeepInfra / Fireworks / Together | No recurring general free quota verified here | Do not budget around referral or trial-credit claims without their offer terms |

Sources: [Groq limits](https://console.groq.com/docs/rate-limits), [Z.ai pricing](https://docs.z.ai/guides/overview/pricing.md), [Cloudflare pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/), [Google limits](https://ai.google.dev/gemini-api/docs/rate-limits), [Alibaba pricing](https://www.alibabacloud.com/help/en/model-studio/model-pricing), [OpenRouter FAQ](https://openrouter.ai/docs/faq), [Cerebras](https://www.cerebras.ai/pricing), [Kimi limits](https://platform.kimi.ai/docs/pricing/limits), [Mistral usage](https://docs.mistral.ai/admin/billing-usage/usage-limits.md).

For a nominal 1,000-input/300-output-token operation, Groq's 200K daily token cap allows approximately 153 operations, before retries or longer reasoning. The 1,000-request headline is not a promise of 1,000 operations at that size. Limits apply across the organization. Cached tokens are excluded from Groq's quota accounting.

Published paid limits include:
- Qwen 3.7 Flash Singapore: 15,000 RPM / 5M TPM; account-level, with burst protections. [Model](https://www.alibabacloud.com/help/en/model-studio/qwen3-7-flash), [limits](https://www.alibabacloud.com/help/en/model-studio/rate-limit)
- Groq GPT-OSS models: base Developer table lists 1,000 RPM / 250K TPM. Current billing documentation describes self-serve upgrades; a Reddit claim that the plan is closed is not treated as current official fact. [Models](https://console.groq.com/docs/models), [billing](https://console.groq.com/docs/billing-faqs)
- Kimi: $1 Tier0 gives 1 concurrent request, 3 RPM, 500K TPM, 1.5M TPD; $10 Tier1 gives 15 concurrent requests, 100 RPM, 2M TPM and no listed daily token cap. [Limits](https://platform.kimi.ai/docs/pricing/limits)
- MiniMax M3.1 Flash/M3: 200 RPM / 10M TPM. [Limits](https://platform.minimax.io/docs/guides/rate-limits.md)
- DeepSeek: account concurrency 2,500 Flash / 500 Pro. This measures in-flight requests, not RPM or an uptime guarantee. [Limits](https://api-docs.deepseek.com/quick_start/rate_limit/)
- DeepInfra pricing page: 200 concurrent requests/account, with expansion by request. [Pricing](https://deepinfra.com/pricing)

Actual console allocations and capacity can differ. New keys do not create independent pools where the service enforces account, organization or project quotas.

## Privacy, commercial use and contractual catches

“No training” and “no retention” are different commitments. Neither establishes India-only processing or completes the app's disclosure obligations.

| Service | Verified data-handling position | App consequence |
|---|---|---|
| Alibaba Model Studio | Official FAQ says customer data is not used for model training | Good candidate; confirm deployment scope, processing locations and applicable product terms. FAQ does not establish a universal zero-retention period. |
| DeepInfra | Standard inference inputs/outputs are memory-only; no content logging or training; exceptions for upstream Google/Anthropic and stored/bulk features | Strong published fit for testing open-weight models with private data, subject to exact endpoint and contract review. |
| Groq | Inference customer content not retained by default; reliability/abuse exceptions can retain up to 30 days; ZDR available | Enable suitable Data Controls. Batch and other stateful features have different retention. |
| Fireworks | Trust Center states no training and ZDR for inference; stateful Responses and training features differ | Prefer stateless inference for statement/receipt processing. |
| Together | Storage on by default for product improvement; training opt-in off by default; admins can disable storage for ZDR | Configure organization privacy controls; passthrough models follow upstream policies and are incompatible with that ZDR setting. |
| Z.ai API | API developers' content is not used to develop/improve services without explicit agreement | Free API and consumer-chat terms must be distinguished. Retention and quotas still need account review. |
| Cloudflare Workers AI | No model training on customer content; standard stateless inference ZDR, with documented exceptions | Avoid content logging/caching unless needed. Gateway calls to external providers follow those providers' terms. |
| Mistral | API training opt-out is available; ZDR is a separate paid-plan, approval-based control | Configure the API opt-out independently of chat/Vibe; do not assume paying alone enables ZDR. |
| Google Gemini | Unpaid services may use content and human review; standard paid service excludes product-improvement use | Current free-tier approach has a material privacy/terms tradeoff for identifiable financial records. |
| Kimi | Official help FAQ says API data is not used for training; platform agreement permits broader content use unless restricted in writing | **Unresolved official-source conflict.** Obtain written clarification or enterprise terms before sensitive processing. |
| DeepSeek direct | Downstream applications and output rights are supported by API terms; definite no-training/retention protection was not established | Privacy remains unresolved for identifiable statements. |
| Grok | No API training without permission; current enterprise terms require personal data to use ZDR-enabled APIs | Verify ZDR before submitting personal data; it disables Files, Collections and Batch capabilities. |
| MiniMax / SiliconFlow / Cerebras | Pricing was checked; a complete endpoint-specific data-protection assessment was not established in this pass | Do not mark these privacy-cleared merely because they have low rates or security certifications. |

Data sources: [Alibaba FAQ](https://www.alibabacloud.com/help/en/model-studio/faq-about-alibaba-cloud-model-studio), [DeepInfra](https://docs.deepinfra.com/account/data-privacy), [Groq](https://console.groq.com/docs/your-data), [Fireworks](https://trust.fireworks.ai/faq), [Together](https://docs.together.ai/docs/privacy-and-security), [Z.ai terms](https://docs.z.ai/legal-agreement/terms-of-use), [Cloudflare](https://developers.cloudflare.com/workers-ai/platform/data-usage/), [Mistral training controls](https://help.mistral.ai/en/articles/455207-can-i-opt-out-of-my-input-or-output-data-being-used-for-training), [Mistral ZDR](https://docs.mistral.ai/admin/monitor-comply/zero-data-retention.md), [Google terms](https://ai.google.dev/gemini-api/terms), [Kimi FAQ](https://www.kimi.ai/help/kimi-api/api-data-security), [Kimi agreement](https://platform.kimi.ai/docs/agreement/modeluse.md), [Kimi ZDR](https://platform.kimi.ai/docs/guide/zero-data-retention.md), [DeepSeek API terms](https://cdn.deepseek.com/policies/en-US/deepseek-open-platform-terms-of-service.html), [Grok terms](https://x.ai/legal/terms-of-service-enterprise), [Grok security](https://docs.x.ai/developers/faq/security).

Commercial app integrations are different from reselling a provider account or using a coding subscription as a shared backend. API keys belong on a controlled backend. Check usage policies and model licences for the exact route.

Google adds an 18+ audience restriction and requires paid services for EEA/UK/Swiss users. Z.ai restricts competing-model development and requires appropriate AI-output identification. Kimi restricts certain consequential decisions about people; ordinary expense recording should remain distinct from automated creditworthiness or similar decisions. DeepSeek prohibits exposing keys in client code. [Google](https://ai.google.dev/gemini-api/terms), [Z.ai](https://docs.z.ai/legal-agreement/terms-of-use), [Kimi](https://platform.kimi.ai/docs/agreement/modeluse.md), [DeepSeek](https://cdn.deepseek.com/policies/en-US/deepseek-open-platform-terms-of-service.html)

## What Reddit actually contributes
These are individual reports, not representative samples or verified service incident rates. Advertisements and promotional recommendations were excluded as evidence of reliability.

| Discussion | What was observed | How it affects this decision |
|---|---|---|
| [Groq production alternatives](https://www.reddit.com/r/LLMDevs/comments/1ufz1g0/groq_alternatives_for_production_apps_rate_limits/) | Developer reports speed benefits, growing rate-limit pain and an unavailable upgrade path; comments mix subscriptions and APIs | Validate upgrade eligibility in our own account; current official billing docs describe Developer upgrades, so do not repeat “closed” as a fact |
| [Groq multi-call workflow](https://www.reddit.com/r/AI_Agents/comments/1wvy6b7/multiagent_system_burns_45_llm_calls_per_task_and/) | A GPT-OSS free-tier user says one action consumes four or five calls and quickly hits limits | Count calls, retries and tokens per completed expense, not just per visible user action |
| [Ministral/Hinglish extraction](https://www.reddit.com/r/MistralAI/comments/1x5ca1r/ministral_3_8b_underperformed_my_rulebased_parser/) | A developer reports high classification accuracy but weaker exact extraction on a small English/Hindi/Hinglish dataset | Relevant warning for Kharcha Paani: valid JSON and correct category do not establish exact amount/date extraction |
| [Kimi limiter](https://www.reddit.com/r/kimi/comments/1r8vhfj/whats_with_the_rate_limiter/) | User reports throttling with paid API credits; indexed replies disagree about the cause | Small prepaid balance may not buy useful concurrency; compare the official tier table |
| [Qwen “no limits”](https://www.reddit.com/r/Qwen_AI/comments/1v7a6wa/no_rate_limits_using_qwen/) | The original post describes a free chat experience | Excluded as evidence of unlimited API access |
| [Broad API-host comparison](https://www.reddit.com/r/LocalLLaMA/comments/1il6hm1/which_api_provider_has_most_number_of_models_and/) | Search-indexed comments include both DeepInfra timeout complaints and reports of improvement | Mixed, dated anecdotes justify a measured latency/error trial, not a blanket reliability verdict |

Rendered Reddit pages were successfully read for the first three rows, including comments on the Groq-production and Ministral threads. Other thread bodies were read through Scrapling; where only indexed comments were available, that distinction is explicit. Relative ages are those displayed by Reddit when accessed; they are not fabricated publication dates.

## Monthly cost scenarios
Assumption: one operation uses **1,000 input + 300 total billable output tokens**, without caching, retries, tools or free allowances.

| Model / route | 10,000 operations | 100,000 operations |
|---|---:|---:|
| Qwen 3.7 Flash Singapore <=32K | $0.69 | $6.90 |
| DeepInfra GPT-OSS 20B | $0.72 | $7.20 |
| Fireworks Nemotron 3.5 Lightning | $1.10 | $11.00 |
| DeepInfra Qwen3.5 9B | $1.45 | $14.50 |
| Groq GPT-OSS 20B | $1.65 | $16.50 |
| Z.ai GLM 5.3 Flash | $3.00 | $30.00 |
| Mistral Small 4 / MiniMax M3.1 Flash | $3.30 | $33.00 |
| DeepSeek Flash, entirely off-peak | $3.30 | $33.00 |
| Gemini 3.5 Flash-Lite | $10.50 | $105.00 |
| Gemini 3.6 Flash, current rate | $18.75 | $187.50 |
| Grok 4.3, short context | $20.00 | $200.00 |
| Kimi K2.6 | $21.50 | $215.00 |
| Kimi K3.1 Pro | $50.00 | $500.00 |

These are arithmetic scenarios, not measured app bills. Under this assumption, Qwen 3.7 Flash costs about **96.3% less** than current Gemini 3.6 Flash. Different tokenizers, image encoding, hidden reasoning and extraction quality can materially change the actual saving.

A statement scenario: **20,000 input + 5,000 billable output tokens per document**, 1,000 documents:
- Qwen 3.7 Flash at the <=32K band: **$1.25**.
- Groq GPT-OSS 20B after separately extracting text: **$3.00**, excluding OCR.
- Gemini 3.6 Flash: **$33.75**.
This does not claim those models accept identical PDF payloads. PDF-to-image conversion, OCR, page limits and completeness validation may require extra processing.

Formula:
`cost = uncached_input/1M * input_rate + cached_input/1M * cache_rate + output/1M * output_rate + tools + storage + other charges`

No rupee conversion is presented as a current exchange rate.

## OCR, voice and other costs
Kharcha Paani needs more than a text chatbot.

- **Text PDF first:** extract an existing PDF text layer locally where possible. Sending only necessary rows avoids charging for unnecessary page imagery and repeated full-document context. This is a proposed evaluation route, not an implemented change.
- **Z.ai GLM-OCR:** $0.03/M input and output tokens. Test layout and digit accuracy; token-priced OCR is not directly comparable to per-page billing. [Pricing](https://docs.z.ai/guides/overview/pricing.md)
- **Mistral OCR 4.1:** $4/1,000 pages, or $5/1,000 annotated pages; OCR 3: $2/$3 respectively. A later model call for expense JSON may add cost. [Pricing](https://docs.mistral.ai/inference/pricing.md)
- **Groq Whisper Large V3 Turbo:** $0.04/audio hour; regular V3: $0.111/hour. These are transcription charges, separate from parsing the resulting text. Hindi/Hinglish accuracy remains untested. [Models](https://console.groq.com/docs/models)
- **Web search:** unnecessary for ordinary ledger extraction. Grok Web Search adds $5/1,000 calls, while Z.ai lists $0.01/use. Disable unnecessary external tools when comparing extraction costs. [Grok](https://docs.x.ai/developers/pricing), [Z.ai](https://docs.z.ai/guides/overview/pricing.md)
- **Caching:** repeated system/schema prefixes may benefit; unique statements do not automatically become cheap cache hits. Cache minimums, TTL, write charges and user isolation matter.

## Recommended app-specific evaluation
The product is intended to remain free initially, so spend should be controlled centrally without adding a user paywall. This research does not change the existing owner decision to use unpaid Gemini during the initial period.

Use a common test set:
- 60 typed English/Hindi/Hinglish expense messages, including corrections, “paid by” changes, dates and splits.
- 30 receipt/payment screenshots with blur, long images, Indian digit grouping and multiple totals.
- 10 synthetic statements with known transaction counts, duplicates, refunds, date boundaries and opening/closing balances.

Evaluate amount/date/merchant correctness, missing or invented rows, valid schema, latency p50/p95, 429/5xx rates, truncation, retries and total cost per successfully reviewed operation. Include mixed-language inputs and the app's existing deterministic parser as a baseline.

Candidate routing, contingent on passing those tests:
1. Existing on-phone rules for straightforward quick-add text.
2. Qwen 3.7 Flash for ambiguous text and receipt experiments.
3. Groq or DeepInfra GPT-OSS for text-only comparison; DeepInfra Qwen3.5 9B for multimodal comparison.
4. Current Gemini for hard-document baseline and potential fallback.
5. Z.ai free models for a zero-token-cost track, without assuming unlimited production capacity.

Use exact user-approved amounts and deterministic arithmetic for the ledger. AI output should remain a draft the user can inspect. Validate required fields, totals and row counts before saving imported transactions. Provider retries must not save duplicates.

## Remaining uncertainties
- Accuracy and latency from an Indian device/backend region.
- Actual billing onboarding, quotas and free-credit eligibility in the owner's accounts.
- Kimi's conflicting official data-use statements.
- DeepSeek direct retention/training terms sufficient for private statements.
- SiliconFlow's exact current international model prices and free-model availability.
- MiniMax endpoint-specific retention/no-training commitments.
- Production capacity and contractual terms for Cerebras.
- PDF support and file retention for each chosen endpoint.
- Dynamic OpenRouter credit-purchase fees; inspect checkout before a top-up.

The next useful action is a small synthetic benchmark of Qwen 3.7 Flash, Groq GPT-OSS 20B, DeepInfra GPT-OSS/Qwen, Z.ai Flash and the current Gemini path. The price screen makes these worth testing; it does not establish which one extracts money data correctly.


---

## Addendum A: Qwen 3.7 Flash review, revised recommendation and API signup

**Qwen 3.7 Flash is my first candidate to test for quick adds. For bill photos, I would compare it with DeepInfra before choosing. I cannot yet call it the best-value model.**

I rechecked official documentation on **27 October 2026** and ran five independent critiques followed by peer review. That exposed some weaknesses in my earlier recommendation.

**Where the reviewers agree**

The low price and relevant capabilities check out:

- Singapore pricing for prompts up to 32K tokens: **$0.03 input/$0.13 output per million tokens**.
- Supports **image input and structured outputs**, so both text expenses and bill photos are supported use cases. [Official model documentation](https://www.alibabacloud.com/help/en/model-studio/qwen3-7-flash)

What remains unverified is whether it reliably reads **Indian receipts, Hindi/Hinglish, decimal amounts, refunds, GST and competing totals**. Structured output can contain perfectly formatted but incorrect financial information.

**Where the recommendation faces its strongest challenge**

At your scale, small differences between cheap models barely affect the budget.

Using our previous bill assumptions—3,000 bills/month, each with 2,000 input and 500 output tokens:

| Bill-processing candidate | Estimated monthly cost |
|---|---:|
| Qwen 3.7 Flash through Alibaba | **$0.38** |
| Qwen3.5 9B through DeepInfra | **$0.83** |
| Difference | **$0.45** |

DeepInfra lists its model as multimodal and publishes a standard inference policy of no stored inputs/outputs and no training on submitted data, with documented exceptions. That makes it worth comparing for financial images. **It does not establish better extraction accuracy.** [Model pricing](https://deepinfra.com/Qwen/Qwen3.5-9B), [data policy](https://docs.deepinfra.com/account/data-privacy)

Saving 45 cents monthly would not justify worse accuracy, privacy controls or reliability.

**The blind spots the review caught**

1. **Thinking is enabled by default.** My earlier estimates assumed short billable outputs. Extra reasoning can increase cost and latency. We should explicitly test `enable_thinking=false` against thinking-enabled extraction. [Official vision guide](https://docs.modelstudio.console.alibabacloud.com/en/model-studio/vision)

2. **Receipt resolution affects accuracy and cost.** Alibaba documents image compression and configurable visual-token limits. Small receipt text may require higher resolution; the assumed 2,000 input tokens is therefore only a planning estimate. [Image-processing documentation](https://docs.modelstudio.console.alibabacloud.com/en/model-studio/vision)

3. **Alibaba retains content by default.** Its policy, updated 26 October, says inference prompts and responses are retained for up to 30 days, with exceptions. Enterprise ZDR requires assessment, and its supported Flash list starts at **Qwen 3.8 Flash**. **Qwen 3.7 Flash is not listed as covered.** Unsupported models still use standard retention even inside a ZDR-enabled workspace. [Retention policy](https://www.alibabacloud.com/help/en/model-studio/data-retention-policy)

That third finding materially weakens my earlier blanket recommendation for bills.

**My revised recommendation**

| Task | Recommendation |
|---|---|
| Straightforward quick adds | Keep the existing on-phone parser where it succeeds |
| Ambiguous text/Hinglish | Test Qwen 3.7 Flash first |
| Bill photos | Compare Alibaba Qwen 3.7 Flash, DeepInfra Qwen3.5 9B and existing Gemini |
| Production selection | Choose by accuracy, latency, privacy and total cost per successful extraction |

**Qwen looks exceptionally inexpensive; “best value” still requires an app-specific test.** I have verified documentation, not run live Qwen inference or an accuracy benchmark.

**The first thing to do—and where to get the API**

Get it directly from **Alibaba Cloud Model Studio**:

1. Open the [official Singapore API-key console](https://modelstudio.console.alibabacloud.com/ap-southeast-1/settings/api-key).
2. Create/sign into an Alibaba Cloud account and activate Model Studio.
3. Select **Singapore** and create a **pay-as-you-go API key**. Coding/Token Plan keys are separate products.
4. Use model ID **`qwen3.7-flash`** and copy the API Host displayed with your key. Keys and endpoints are region-specific. [Key instructions](https://www.alibabacloud.com/help/en/model-studio/get-api-key)

The current documented Singapore OpenAI-compatible base URL is:

```text
https://<WorkspaceId>.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1
```

Use your actual workspace ID and the host supplied by the console. Keep the credential on the backend. [Official API quickstart](https://www.alibabacloud.com/help/en/model-studio/first-api-call-to-qwen)

The first useful experiment is **100 representative quick adds and 50 synthetic or de-identified bill images**, compared against Gemini using exact amounts, dates, missed fields, latency and actual billed tokens. That would verify whether Qwen deserves the recommendation.

---

## Addendum B: Alibaba signup obstacle, gateway access, Groq comparison and multi-provider setup

**Don’t continue with Singapore as your account country. You can access Qwen through another API provider—and yes, Kharcha Paani should have multiple providers.** My earlier recommendation missed the India signup obstacle.

I checked these details on **27 October 2026**.

**What to do about Alibaba signup**

The selector in your screenshot is your **account/billing country**. The Singapore API hosting region is a separate setting.

Alibaba explicitly says:

- Your verification phone number must belong to the selected account country.
- Your account country determines billing currency and taxes.
- It cannot be changed after registration. [Official registration guide](https://www.alibabacloud.com/help/en/account/step-1-register-an-alibaba-cloud-account)

Therefore, selecting Singapore explains the +65 requirement. **Don’t use a borrowed or virtual Singapore number to force registration.** If you already completed registration with the wrong country, contact support before adding payment details.

I couldn’t find an official explanation for India’s absence. I also checked Reddit through Scrapling: other Indian developers report the same verification obstacle, but that is anecdotal evidence, not confirmation of a formal India-wide ban. [Indian developers’ discussion](https://www.reddit.com/r/developersIndia/comments/1wakngb/workaround_for_signing_up_on_alibaba_cloud_model/)

**The practical alternative: access the exact Qwen model through a gateway.**

| Access option | Model identifier | Listed input/output per million tokens |
|---|---|---:|
| [OpenRouter](https://openrouter.ai/qwen/qwen3.7-flash) | `qwen/qwen3.7-flash` | From **$0.03 / $0.13** |
| [Vercel AI Gateway](https://vercel.com/ai-gateway/models/qwen3.7-flash) | `alibaba/qwen3.7-flash` | **$0.03 / $0.13** |

These are separate services with their own accounts and billing. Vercel lets you purchase gateway credits and authenticate using its API key; bringing your own Alibaba key is optional. Its API keys also work from external servers, so hosting your app on Vercel is unnecessary. [Authentication](https://vercel.com/docs/ai-gateway/authentication-and-byok), [pricing](https://vercel.com/docs/ai-gateway/pricing)

**These are documented access routes; I haven’t completed signup or payment using your Indian account.** Also, the listed Qwen routes still use Alibaba upstream: changing the gateway does not automatically change Alibaba’s data handling or protect you from an Alibaba outage.

**Qwen versus Groq**

Groq is an inference provider. The relevant comparison is **Qwen 3.7 Flash versus GPT-OSS models hosted by Groq**. Groq is also separate from xAI’s **Grok**.

Prices below are USD per million tokens, before taxes or payment fees.

| Model and host | Input | Output | Receipt images? | Useful advantage |
|---|---:|---:|---|---|
| Qwen 3.7 Flash — Alibaba/gateway | $0.03 | $0.13 | Yes | Very cheap multimodal candidate |
| GPT-OSS 20B — Groq | $0.075 | $0.30 | No | Fast text extraction; strict JSON |
| GPT-OSS 120B — Groq | $0.15 | $0.60 | No | Candidate for harder text requests |
| GPT-OSS 20B — DeepInfra | $0.03 | $0.14 | No | Cheap alternative host for the same model |
| Qwen 3.5 9B — DeepInfra | $0.10 | $0.15 | Yes | Cheap receipt candidate with zero-retention listing |

Sources: [Alibaba Qwen pricing](https://www.alibabacloud.com/help/en/model-studio/qwen3-7-flash), [Groq model catalogue](https://console.groq.com/docs/models), [DeepInfra GPT-OSS](https://deepinfra.com/openai/gpt-oss-20b), [DeepInfra Qwen](https://deepinfra.com/Qwen/Qwen3.5-9B).

**Qwen 3.5 9B is a different model from Qwen 3.7 Flash.** We cannot assume equivalent receipt accuracy.

Groq supports **strict JSON-schema output** on both GPT-OSS models. That is useful for extracting amount, currency, merchant, date and participants into predictable fields. It guarantees the response’s structure; it does not guarantee that the amount or participant is correct. [Structured-output documentation](https://console.groq.com/docs/structured-outputs)

**Is Groq actually faster?**

It has a strong published throughput advantage:

| Route | Reported output throughput |
|---|---:|
| Groq GPT-OSS 20B | Approximately **1,000 tokens/second** |
| Groq GPT-OSS 120B | Approximately **500 tokens/second** |
| Qwen 3.7 Flash through OpenRouter | Approximately **75–82 tokens/second** in its displayed traffic statistics |

These are **different measurement methods**, so they are not a controlled benchmark. Groq publishes model speeds; OpenRouter reports observed gateway traffic. [Groq speeds](https://console.groq.com/docs/models), [OpenRouter statistics](https://openrouter.ai/qwen/qwen3.7-flash)

For illustration, generating 300 tokens at those rates takes about **0.3 seconds versus 3.7–4 seconds**. Actual waiting time also includes networking, processing the input, queuing and reasoning tokens.

**My judgment: Groq is a strong speed candidate for quick adds. I cannot yet promise a specific response time from an Indian phone.** For short structured responses, time to first token and total completion time matter more than headline throughput.

**The setup I recommend for Kharcha Paani**

Start with **three providers: Groq, DeepInfra and paid Gemini**. Keep Qwen 3.7 through a gateway as a comparison candidate while its financial-data handling is resolved.

| Task | First route | Fallback or escalation |
|---|---|---|
| Simple quick add | Existing on-device parser | Groq GPT-OSS 20B when parsing is uncertain |
| AI quick add | Groq GPT-OSS 20B | DeepInfra GPT-OSS 20B for provider failures |
| Complicated split or ambiguous text | Evaluate Groq GPT-OSS 120B | Ask the user if the intended meaning remains unclear |
| Receipt photo | Evaluate DeepInfra Qwen 3.5 9B | Paid Gemini for failed or inconsistent extraction |
| Bank statement | Paid Gemini initially | Extract PDF text locally, then evaluate an independent text-model route |
| Qwen 3.7 trial | OpenRouter or Vercel Gateway | Compare against the above on the same examples |

The distinction matters: **20B → 120B on Groq is a model escalation, but it does not protect against Groq being unavailable.** Groq → DeepInfra provides a separate provider route.

For receipts, I would promote the cheap Qwen model only after checking real Indian receipts: blurred photos, GST, discounts, multiple totals, Hindi text and handwritten amounts. Until it passes, use paid Gemini as the receipt default. None of these models has been tested against your app’s examples yet.

The routing should follow a few simple rules:

- **Timeout, rate limit or server error:** try the independent provider.
- **Invalid result or inconsistent totals:** try a stronger model once.
- **Missing information or genuine ambiguity:** ask the user.
- **At most one automatic fallback** per operation; avoid a long chain of increasingly slow requests.
- Validate money fields and prevent duplicate ledger entries when requests are retried.

**Free limits and privacy affect the choice**

Groq’s listed free limits for GPT-OSS include **30 requests/minute, 1,000 requests/day and 200,000 tokens/day**. Your 400 daily quick adds, under our earlier 1,000-input/300-output assumption, consume roughly **520,000 tokens/day** before bills or statements. Free Groq therefore won’t cover that assumed workload. Paid Developer limits are much higher, and Groq explicitly documents billing for Indian customers. [Limits](https://console.groq.com/docs/rate-limits), [India billing](https://console.groq.com/docs/billing-faqs)

For financial data:

- **Groq:** enable its self-service **Zero Data Retention** setting. Otherwise, reliability and abuse-monitoring exceptions can retain content for up to 30 days. Usage metadata remains collected. [Data policy](https://console.groq.com/docs/your-data)
- **DeepInfra:** both proposed model pages list **zero retention**.
- **Gemini:** use the **paid Standard tier** for this recommendation; Google lists content as not used for product improvement there. [Pricing and data-use distinction](https://ai.google.dev/gemini-api/docs/pricing)
- **Qwen through a gateway:** review the selected upstream route’s policy; a gateway is not a privacy exemption.

**What this changes in your estimated bill**

Using our earlier token assumptions and a 30-day month:

| Configuration for your 100 users | Per month | Daily average |
|---|---:|---:|
| Groq 20B quick adds + DeepInfra Qwen receipts + Gemini statements | **$6.18** | **$0.21** |
| Groq 20B quick adds + Gemini receipts and statements | **$15.48** | **$0.52** |

These assume every quick add reaches AI, no caching, no retries, and the earlier token sizes. Local parsing can reduce cost; image size, reasoning tokens and retries can increase it. Gemini calculations use today’s Standard rates, which are scheduled to increase on **1 January 2027**. [Google’s dated rates](https://ai.google.dev/gemini-api/docs/pricing)

**My recommendation is to try Groq for quick adds, retain paid Gemini for documents, and evaluate DeepInfra’s cheap receipt model before promoting it. You can still test the exact Qwen 3.7 Flash through OpenRouter or Vercel without getting stuck on Alibaba signup.**
