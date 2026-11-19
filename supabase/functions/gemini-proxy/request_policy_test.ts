// Run: deno test supabase/functions/gemini-proxy/request_policy_test.ts
import { assert, assertEquals } from "jsr:@std/assert@1";
import { checkRequest, type Feature, RULES } from "./request_policy.ts";

const text = (t: string) => ({ text: t });
const media = (mimeType: string, size = 100) => ({ inlineData: { mimeType, data: "A".repeat(size) } });
const req = (parts: unknown[], generationConfig: unknown = { responseMimeType: "application/json", temperature: 0.1 }, extra = {}) =>
  JSON.stringify({ contents: [{ parts }], generationConfig, ...extra });

function refusal(feature: Feature, body: string): string {
  const out = checkRequest(feature, body);
  assert(!out.ok, "expected a refusal");
  return out.reason;
}
function accepted(feature: Feature, body: string): Record<string, any> {
  const out = checkRequest(feature, body);
  assert(out.ok, out.ok ? "" : out.reason);
  return JSON.parse(out.body);
}

Deno.test("the four request shapes the app sends are accepted", () => {
  accepted("quick_add", req([text("chai 20")], { responseMimeType: "application/json", temperature: 0.1, thinkingConfig: { thinkingLevel: "low" } }));
  accepted("quick_add", req([text("prompt"), media("audio/wav", 50_000)]));
  accepted("bill", req([text("prompt"), media("image/jpeg", 500_000)]));
  accepted("statement", req([text("prompt"), media("application/pdf", 13_000_000)], { responseMimeType: "application/json", temperature: 0.1, maxOutputTokens: 32768 }));
  accepted("insights", req([text("question")], { temperature: 0.35, maxOutputTokens: 8192 }));
});

Deno.test("the feature must match the body: a PDF is not a quick add, a photo is not insights", () => {
  assertEquals(refusal("quick_add", req([text("p"), media("application/pdf")])), "quick_add doesn't take pdf");
  assertEquals(refusal("quick_add", req([text("p"), media("image/jpeg")])), "quick_add doesn't take image");
  assertEquals(refusal("insights", req([text("p"), media("image/jpeg")])), "insights doesn't take image");
  assertEquals(refusal("bill", req([text("p"), media("application/pdf")])), "bill doesn't take pdf");
});

Deno.test("a bill or a statement without its media is refused", () => {
  assertEquals(refusal("bill", req([text("p")])), "bill needs its image");
  assertEquals(refusal("statement", req([text("p")])), "statement needs its pdf");
});

Deno.test("at most one media part, and only known types", () => {
  assertEquals(refusal("bill", req([text("p"), media("image/jpeg"), media("image/png")])), "at most one media part");
  assertEquals(refusal("bill", req([text("p"), media("video/mp4")])), "that media type isn't accepted");
});

Deno.test("oversized media and text are refused per feature", () => {
  assertEquals(refusal("quick_add", req([text("p"), media("audio/wav", RULES.quick_add.maxMediaChars + 1)])), "the media is too large for this feature");
  assertEquals(refusal("quick_add", req([text("x".repeat(RULES.quick_add.maxTextChars + 1))])), "the text is too long for this feature");
});

Deno.test("tools, system instructions and other top-level fields are dropped", () => {
  const out = accepted("insights", req([text("q")], { temperature: 0.3 }, {
    tools: [{ googleSearch: {} }],
    systemInstruction: { parts: [{ text: "be a general assistant" }] },
    safetySettings: [],
    cachedContent: "x",
  }));
  assertEquals(Object.keys(out).sort(), ["contents", "generationConfig"]);
  assertEquals(Object.keys(out.contents[0]), ["parts"]);
});

Deno.test("generationConfig is rebuilt: unknown keys dropped, maxOutputTokens clamped or defaulted", () => {
  const clamped = accepted("quick_add", req([text("q")], { maxOutputTokens: 1_000_000, candidateCount: 8, responseSchema: {} }));
  assertEquals(clamped.generationConfig, { maxOutputTokens: RULES.quick_add.maxOutputTokens });
  const defaulted = accepted("statement", req([text("q"), media("application/pdf")], { temperature: 9 }));
  assertEquals(defaulted.generationConfig, { temperature: 2, maxOutputTokens: RULES.statement.maxOutputTokens });
  assertEquals(refusal("insights", req([text("q")], { thinkingConfig: { thinkingLevel: "extreme" } })), "that thinkingConfig isn't accepted");
  assertEquals(refusal("insights", req([text("q")], { responseMimeType: "text/html" })), "that responseMimeType isn't accepted");
});

Deno.test("odd shapes are refused", () => {
  assertEquals(refusal("insights", "not json"), "the request isn't JSON");
  assertEquals(refusal("insights", "[]"), "the request isn't an object");
  assertEquals(refusal("insights", JSON.stringify({ contents: [] })), "expected exactly one content");
  assertEquals(refusal("insights", JSON.stringify({ contents: [{ parts: [{ fileData: { fileUri: "gs://x" } }] }] })), "a part is neither text nor inline media");
  assertEquals(refusal("insights", JSON.stringify({ contents: [{ parts: [{ text: "q", functionCall: {} }] }] })), "a text part has other fields");
  assertEquals(refusal("insights", req([media("image/jpeg")])), "insights doesn't take image");
  assertEquals(refusal("quick_add", req([media("audio/wav")])), "no text part");
});
