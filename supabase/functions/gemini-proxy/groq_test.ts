// deno test supabase/functions/gemini-proxy/groq_test.ts
import { assertEquals } from "jsr:@std/assert@1.0.13";
import { fromGroqChat, toGroqChat } from "./groq.ts";

/** What the app sends for a typed line (GeminiRepository.jsonRequest). */
const typedLine = JSON.stringify({
  contents: [{ parts: [{ text: "Read this as JSON: chai 40 cash" }] }],
  generationConfig: { responseMimeType: "application/json", temperature: 0.1, thinkingConfig: { thinkingLevel: "low" } },
});

Deno.test("a typed line becomes a Groq chat request in JSON mode", () => {
  const chat = toGroqChat(typedLine, "qwen/qwen3.8-27b")!;
  assertEquals(chat.model, "qwen/qwen3.8-27b");
  assertEquals(chat.temperature, 0.1);
  assertEquals(chat.response_format, { type: "json_object" });
  const messages = chat.messages as { role: string; content: string }[];
  assertEquals(messages.map((m) => m.role), ["system", "user"]);
  assertEquals(messages[1].content, "Read this as JSON: chai 40 cash");
});

Deno.test("a photo or a voice clip is not for Groq", () => {
  const withAudio = JSON.stringify({
    contents: [{ parts: [{ text: "Read what's said" }, { inline_data: { mime_type: "audio/mp4", data: "AAAA" } }] }],
    generationConfig: { responseMimeType: "application/json" },
  });
  assertEquals(toGroqChat(withAudio, "qwen/qwen3.8-27b"), null);
  // A part with text and anything else beside it is not plain text either.
  const mixed = JSON.stringify({ contents: [{ parts: [{ text: "x", inlineData: { data: "AAAA" } }] }] });
  assertEquals(toGroqChat(mixed, "qwen/qwen3.8-27b"), null);
});

Deno.test("nothing readable is not for Groq", () => {
  assertEquals(toGroqChat("not json", "m"), null);
  assertEquals(toGroqChat(JSON.stringify({ contents: [{ parts: [{ text: "   " }] }] }), "m"), null);
  assertEquals(toGroqChat(JSON.stringify({ contents: "x" }), "m"), null);
});

Deno.test("without JSON mode, no response_format, and an odd temperature is replaced", () => {
  const chat = toGroqChat(JSON.stringify({ contents: [{ parts: [{ text: "hi" }] }], generationConfig: { temperature: 9 } }), "m")!;
  assertEquals(chat.response_format, undefined);
  assertEquals(chat.temperature, 0.1);
});

Deno.test("Groq's answer comes back as a generateContent envelope", () => {
  const reply = JSON.stringify({ choices: [{ message: { role: "assistant", content: '{"amount":40}' }, finish_reason: "stop" }] });
  assertEquals(fromGroqChat(reply), { candidates: [{ content: { role: "model", parts: [{ text: '{"amount":40}' }] }, finishReason: "STOP" }] });
});

Deno.test("thinking before the answer is dropped", () => {
  const reply = JSON.stringify({ choices: [{ message: { content: '<think>chai is food</think>\n{"amount":40}' }, finish_reason: "stop" }] });
  const parts = ((fromGroqChat(reply)!.candidates as Record<string, unknown>[])[0].content as { parts: { text: string }[] }).parts;
  assertEquals(parts[0].text, '{"amount":40}');
});

Deno.test("a cut-off answer says so, as Gemini would", () => {
  const reply = JSON.stringify({ choices: [{ message: { content: '{"amount":4' }, finish_reason: "length" }] });
  assertEquals((fromGroqChat(reply)!.candidates as Record<string, unknown>[])[0].finishReason, "MAX_TOKENS");
});

Deno.test("no answer means Gemini gets the request instead", () => {
  assertEquals(fromGroqChat("not json"), null);
  assertEquals(fromGroqChat(JSON.stringify({ choices: [] })), null);
  assertEquals(fromGroqChat(JSON.stringify({ choices: [{ message: { content: "" }, finish_reason: "stop" }] })), null);
  assertEquals(fromGroqChat(JSON.stringify({ choices: [{ message: { content: "<think>hmm</think>" }, finish_reason: "stop" }] })), null);
});
