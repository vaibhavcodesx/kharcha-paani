// Typed quick adds through Groq (owner decision 2026-11-06, tools/ai-bench results).
//
// The app keeps speaking Gemini's generateContent shape to this function. For a Groq model id it
// sends here, the request is turned into a Groq chat completion and Groq's answer is put back into a
// generateContent envelope, so the app reads it exactly as it reads Gemini's. Text only: Groq has no
// model here that reads photos or audio, and anything that isn't plain text falls back to Gemini.

/** The ids the app sends, and the Groq model each one means. */
export const GROQ_MODELS: Record<string, string> = {
  "groq-qwen3.8-27b": "qwen/qwen3.8-27b",
};

/** Where a Groq request goes when Groq can't answer: the same body, on the same day's read. */
export const GROQ_FALLBACK_GEMINI = "gemini-3.1-flash-lite";

/**
 * A one-line read answers in well under this (p90 5 s in the benchmark); past it, Gemini is faster
 * than waiting, and the app's 25 s budget for a typed line still has room for Gemini's answer.
 */
export const GROQ_TIMEOUT_MS = 8_000;

const MAX_COMPLETION_TOKENS = 1024;

type Json = Record<string, unknown>;

const isObject = (value: unknown): value is Json => typeof value === "object" && value !== null && !Array.isArray(value);

/**
 * The Groq chat request for a generateContent [body], or null when it can't be one: not JSON, or
 * any part that isn't text (a photo, a voice clip).
 */
export function toGroqChat(body: string, groqModel: string): Json | null {
  let request: unknown;
  try {
    request = JSON.parse(body);
  } catch {
    return null;
  }
  if (!isObject(request) || !Array.isArray(request.contents)) return null;

  const texts: string[] = [];
  for (const content of request.contents) {
    if (!isObject(content) || !Array.isArray(content.parts)) return null;
    for (const part of content.parts) {
      if (!isObject(part) || typeof part.text !== "string" || Object.keys(part).length !== 1) return null;
      texts.push(part.text);
    }
  }
  const text = texts.join("\n").trim();
  if (!text) return null;

  const config = isObject(request.generationConfig) ? request.generationConfig : {};
  const json = config.responseMimeType === "application/json";
  const temperature = typeof config.temperature === "number" && config.temperature >= 0 && config.temperature <= 2
    ? config.temperature
    : 0.1;

  return {
    model: groqModel,
    temperature,
    max_completion_tokens: MAX_COMPLETION_TOKENS,
    messages: [
      // Groq's JSON mode needs the word JSON in the messages; the app's prompt has it, this makes sure.
      { role: "system", content: json ? "Reply with one JSON object and nothing else." : "Reply briefly." },
      { role: "user", content: text },
    ],
    ...(json ? { response_format: { type: "json_object" } } : {}),
  };
}

const FINISH_REASONS: Record<string, string> = {
  stop: "STOP",
  length: "MAX_TOKENS",
  content_filter: "SAFETY",
};

/**
 * Groq's chat completion as a generateContent envelope, or null when it has no answer to give
 * (then the request goes to Gemini instead).
 */
export function fromGroqChat(reply: string): Json | null {
  let completion: unknown;
  try {
    completion = JSON.parse(reply);
  } catch {
    return null;
  }
  if (!isObject(completion) || !Array.isArray(completion.choices)) return null;
  const choice = completion.choices[0];
  if (!isObject(choice) || !isObject(choice.message)) return null;
  const content = choice.message.content;
  if (typeof content !== "string") return null;

  // A reasoning model can put its thinking first in the content; the app only wants the answer.
  const text = content.replace(/^\s*<think>[\s\S]*?<\/think>\s*/, "").trim();
  if (!text) return null;

  const finishReason = FINISH_REASONS[String(choice.finish_reason)] ?? "OTHER";
  return { candidates: [{ content: { role: "model", parts: [{ text }] }, finishReason }] };
}
