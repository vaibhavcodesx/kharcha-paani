// What a statement read may bring back into the app (security audit M-2). Gemini answers with
// whatever it read; this keeps a hostile or broken PDF from flooding someone's import list or
// handing the app values the server would refuse later anyway.

/** More rows than any real statement export; a reply past this is cut off here. */
export const MAX_ROWS = 1000;
/** The server's numeric(12,2) ceiling, the same as the app's MAX_AMOUNT_RUPEES. */
export const MAX_AMOUNT = 9_999_999_999.99;
const MAX_MERCHANT = 80;
const MAX_NOTE = 200;
const MAX_SHORT = 60;

type Json = Record<string, unknown>;

function text(value: unknown, max: number): string {
  return typeof value === "string" ? value.trim().slice(0, max) : "";
}

/** One row as the app expects it, or null for a row that can't be an entry. */
export function cleanRow(row: unknown): Json | null {
  if (typeof row !== "object" || row === null) return null;
  const r = row as Json;
  const amount = typeof r.amount === "number" ? r.amount : Number(r.amount);
  if (!Number.isFinite(amount) || amount <= 0 || amount > MAX_AMOUNT) return null;
  return {
    date: text(r.date, 40),
    type: r.type === "income" ? "income" : "expense",
    amount: Math.round(amount * 100) / 100,
    merchant: text(r.merchant, MAX_MERCHANT),
    category: text(r.category, MAX_SHORT),
    paymentMethod: text(r.paymentMethod, MAX_SHORT),
    note: text(r.note, MAX_NOTE),
  };
}

/** The statement JSON the model wrote, cleaned. Anything that isn't that JSON passes through. */
export function cleanStatementText(answer: string): string {
  let parsed: unknown;
  try {
    parsed = JSON.parse(answer);
  } catch {
    return answer;
  }
  if (typeof parsed !== "object" || parsed === null || !Array.isArray((parsed as Json).transactions)) return answer;
  const statement = parsed as Json;
  const rows = (statement.transactions as unknown[])
    .slice(0, MAX_ROWS)
    .map(cleanRow)
    .filter((row): row is Json => row !== null);
  return JSON.stringify({
    bankOrApp: text(statement.bankOrApp, MAX_MERCHANT),
    statementPeriod: text(statement.statementPeriod, MAX_SHORT),
    transactions: rows,
  });
}

/** Cleans the answer parts of a generateContent reply in place; thought parts are left alone. */
export function cleanStatementReply(envelope: Json): Json {
  const candidates = envelope.candidates;
  if (!Array.isArray(candidates)) return envelope;
  for (const candidate of candidates) {
    const parts = (candidate as Json)?.content && ((candidate as Json).content as Json).parts;
    if (!Array.isArray(parts)) continue;
    for (const part of parts as Json[]) {
      if (part.thought === true || typeof part.text !== "string") continue;
      part.text = cleanStatementText(part.text);
    }
  }
  return envelope;
}
