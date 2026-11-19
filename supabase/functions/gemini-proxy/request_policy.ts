// What a request to the AI proxy may contain (hardening, issue #296).
//
// The function checks the shape of each request before it is forwarded: the feature comes from the
// body itself, not only from the X-Kp-Feature header, and the forwarded body is rebuilt from the
// fields the app really sends.
//
// checkRequest decides the feature from the body itself, refuses a body that doesn't match the
// feature named in the header, and rebuilds a body from the few fields the app really sends. The
// app's request shape is in GeminiRepository.kt (jsonRequest, and the statement request): one
// content with text parts and at most one inlineData part, and a generationConfig of
// responseMimeType, temperature, maxOutputTokens and thinkingConfig.thinkingLevel. Everything else
// (tools, systemInstruction, safetySettings, cachedContent, extra generationConfig keys) is dropped.
//
// It never logs or returns any of the body: refusals say which rule failed, nothing more.

export type Feature = "quick_add" | "bill" | "statement" | "insights";
type MediaKind = "audio" | "image" | "pdf";
type Json = Record<string, unknown>;

interface FeatureRule {
  /** The one media part the feature may carry, or null for text only. quick_add may also carry none. */
  media: MediaKind | null;
  /** Whether the media part must be there (a bill without a photo is not a bill). */
  mediaRequired: boolean;
  /** Longest total of all text parts, in characters. */
  maxTextChars: number;
  /** Longest base64 `data` of the media part, in characters. */
  maxMediaChars: number;
  /** Ceiling for generationConfig.maxOutputTokens; also the value used when the app sends none. */
  maxOutputTokens: number;
}

/**
 * Limits are deliberately a little above what the app sends today, so no phone in the field is
 * refused; tighten them once real sizes are measured. The statement figures match the app's own
 * 10 MB PDF limit (base64 is 4/3 the size) and its 32768-token output budget.
 */
export const RULES: Record<Feature, FeatureRule> = {
  quick_add: { media: "audio", mediaRequired: false, maxTextChars: 30_000, maxMediaChars: 4_000_000, maxOutputTokens: 8192 },
  bill: { media: "image", mediaRequired: true, maxTextChars: 30_000, maxMediaChars: 6_000_000, maxOutputTokens: 8192 },
  statement: { media: "pdf", mediaRequired: true, maxTextChars: 30_000, maxMediaChars: 14_000_000, maxOutputTokens: 32768 },
  insights: { media: null, mediaRequired: false, maxTextChars: 150_000, maxMediaChars: 0, maxOutputTokens: 8192 },
};

const THINKING_LEVELS = new Set(["minimal", "low", "medium", "high"]);
const RESPONSE_MIME_TYPES = new Set(["application/json", "text/plain"]);
const MAX_PARTS = 4;

export type Checked = { ok: true; body: string } | { ok: false; reason: string };

const isObject = (value: unknown): value is Json => typeof value === "object" && value !== null && !Array.isArray(value);
const refuse = (reason: string): Checked => ({ ok: false, reason });

function mediaKind(mimeType: unknown): MediaKind | null {
  if (typeof mimeType !== "string") return null;
  const type = mimeType.toLowerCase().split(";")[0].trim();
  if (type.startsWith("audio/")) return "audio";
  if (type === "image/jpeg" || type === "image/png" || type === "image/webp") return "image";
  if (type === "application/pdf") return "pdf";
  return null;
}

/** The sanitised body for [feature], or the rule that refused it. */
export function checkRequest(feature: Feature, body: string): Checked {
  const rule = RULES[feature];
  let request: unknown;
  try {
    request = JSON.parse(body);
  } catch {
    return refuse("the request isn't JSON");
  }
  if (!isObject(request)) return refuse("the request isn't an object");

  const contents = request.contents;
  if (!Array.isArray(contents) || contents.length !== 1 || !isObject(contents[0])) {
    return refuse("expected exactly one content");
  }
  const rawParts = contents[0].parts;
  if (!Array.isArray(rawParts) || rawParts.length === 0 || rawParts.length > MAX_PARTS) {
    return refuse("expected between one and four parts");
  }

  const parts: Json[] = [];
  let textChars = 0;
  let mediaParts = 0;
  let mediaFound: MediaKind | null = null;
  for (const part of rawParts) {
    if (!isObject(part)) return refuse("a part isn't an object");
    if (typeof part.text === "string") {
      if (Object.keys(part).length !== 1) return refuse("a text part has other fields");
      textChars += part.text.length;
      parts.push({ text: part.text });
    } else if (isObject(part.inlineData)) {
      if (Object.keys(part).length !== 1) return refuse("a media part has other fields");
      const { mimeType, data } = part.inlineData;
      const kind = mediaKind(mimeType);
      if (!kind) return refuse("that media type isn't accepted");
      if (typeof data !== "string" || data.length === 0) return refuse("a media part has no data");
      mediaParts += 1;
      mediaFound = kind;
      if (rule.media !== kind) return refuse(`${feature} doesn't take ${kind}`);
      if (data.length > rule.maxMediaChars) return refuse("the media is too large for this feature");
      parts.push({ inlineData: { mimeType: (mimeType as string).split(";")[0].trim(), data } });
    } else {
      return refuse("a part is neither text nor inline media");
    }
  }
  if (mediaParts > 1) return refuse("at most one media part");
  if (rule.mediaRequired && mediaFound === null) return refuse(`${feature} needs its ${rule.media}`);
  if (textChars === 0) return refuse("no text part");
  if (textChars > rule.maxTextChars) return refuse("the text is too long for this feature");

  const config = isObject(request.generationConfig) ? request.generationConfig : {};
  const generationConfig: Json = {};
  if (config.responseMimeType !== undefined) {
    if (typeof config.responseMimeType !== "string" || !RESPONSE_MIME_TYPES.has(config.responseMimeType)) {
      return refuse("that responseMimeType isn't accepted");
    }
    generationConfig.responseMimeType = config.responseMimeType;
  }
  if (config.temperature !== undefined) {
    if (typeof config.temperature !== "number" || !Number.isFinite(config.temperature)) {
      return refuse("temperature isn't a number");
    }
    generationConfig.temperature = Math.min(2, Math.max(0, config.temperature));
  }
  if (config.maxOutputTokens !== undefined) {
    if (typeof config.maxOutputTokens !== "number" || !Number.isFinite(config.maxOutputTokens) || config.maxOutputTokens < 1) {
      return refuse("maxOutputTokens isn't a positive number");
    }
    generationConfig.maxOutputTokens = Math.min(rule.maxOutputTokens, Math.floor(config.maxOutputTokens));
  } else {
    generationConfig.maxOutputTokens = rule.maxOutputTokens;
  }
  if (config.thinkingConfig !== undefined) {
    const level = isObject(config.thinkingConfig) ? config.thinkingConfig.thinkingLevel : undefined;
    if (typeof level !== "string" || !THINKING_LEVELS.has(level)) return refuse("that thinkingConfig isn't accepted");
    generationConfig.thinkingConfig = { thinkingLevel: level };
  }

  return { ok: true, body: JSON.stringify({ contents: [{ parts }], generationConfig }) };
}
