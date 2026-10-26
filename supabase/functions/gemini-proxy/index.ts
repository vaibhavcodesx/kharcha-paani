// gemini-proxy: the only way the app reaches Google Gemini (plan T1.3, decision D2).
//
// The app sends exactly the generateContent request it used to send to Google, to
// /functions/v1/gemini-proxy/models/<model>:generateContent, with the person's session token and an
// X-Kp-Feature header (quick_add, bill, statement or insights). This function:
//   1. lets the request through only for a signed-in person (Supabase verifies the JWT before this
//      code runs, verify_jwt = true);
//   2. spends one of that person's AI reads for today through public.take_ai_read, which refuses
//      once the day's cap for that kind is used (caps in private.ai_read_caps);
//   3. forwards the body to Google with the key from the GEMINI_API_KEY secret, which never leaves
//      the server;
//   4. checks the reply is a generateContent envelope, and for statements cleans the rows
//      (statement.ts);
//   5. never logs a request or a reply body: they're people's bills and bank statements.
//
// Google's own error replies are passed through unchanged, so the app's failure classifier keeps
// telling a quota from a refused key from a block. The daily cap answers 429 with status DAILY_CAP.

import { createClient } from "jsr:@supabase/supabase-js@2.49.4";
import { cleanStatementReply } from "./statement.ts";

/** The models the app uses: the main one and its lighter fallback. Anything else is refused. */
const MODELS = new Set(["gemini-3.6-flash", "gemini-3.5-flash-lite"]);
const FEATURES = new Set(["quick_add", "bill", "statement", "insights"]);
/** A statement PDF at the app's 10 MB limit, base64-encoded, plus the prompt. */
const MAX_BODY_BYTES = 15 * 1024 * 1024;

function reply(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function refuse(status: number, reason: string, message: string): Response {
  return reply(status, { error: { code: status, status: reason, message } });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return refuse(405, "METHOD_NOT_ALLOWED", "POST only");

  const model = new URL(req.url).pathname.match(/\/models\/([^/:]+):generateContent$/)?.[1];
  if (!model || !MODELS.has(model)) return refuse(404, "NOT_FOUND", "unknown model");
  const feature = req.headers.get("x-kp-feature") ?? "";
  if (!FEATURES.has(feature)) return refuse(400, "INVALID_ARGUMENT", "unknown feature");
  const authorization = req.headers.get("authorization");
  if (!authorization) return refuse(401, "UNAUTHENTICATED", "sign in first");

  if (Number(req.headers.get("content-length") ?? "0") > MAX_BODY_BYTES) return refuse(413, "TOO_LARGE", "request too large");
  const body = await req.text();
  if (body.length > MAX_BODY_BYTES) return refuse(413, "TOO_LARGE", "request too large");

  // As the caller, so take_ai_read counts against auth.uid() and nobody else.
  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: allowed, error } = await supabase.rpc("take_ai_read", { p_feature: feature });
  if (error) {
    console.error(`take_ai_read failed for ${feature}: ${error.code}`);
    return refuse(error.code === "42501" ? 401 : 500, "QUOTA_CHECK_FAILED", "couldn't check today's reads");
  }
  if (allowed !== true) return refuse(429, "DAILY_CAP", "That's today's AI reads used up.");

  const google = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
    method: "POST",
    headers: { "content-type": "application/json", "x-goog-api-key": Deno.env.get("GEMINI_API_KEY")! },
    body,
  });
  const text = await google.text();
  if (!google.ok) {
    // The status only: Google's error body can quote the request.
    console.warn(`Gemini ${model} answered HTTP ${google.status} for ${feature}`);
    return new Response(text, { status: google.status, headers: { "content-type": "application/json" } });
  }

  let envelope: Record<string, unknown>;
  try {
    envelope = JSON.parse(text);
  } catch {
    return refuse(502, "BAD_GATEWAY", "Gemini's reply wasn't JSON");
  }
  if (typeof envelope !== "object" || envelope === null || !("candidates" in envelope || "promptFeedback" in envelope)) {
    return refuse(502, "BAD_GATEWAY", "Gemini's reply wasn't a generateContent reply");
  }
  return reply(200, feature === "statement" ? cleanStatementReply(envelope) : envelope);
});
