// deno test supabase/functions/gemini-proxy/statement_test.ts
import { assertEquals } from "jsr:@std/assert@1.0.13";
import { cleanStatementReply, cleanStatementText, MAX_ROWS } from "./statement.ts";

const row = (over: Record<string, unknown> = {}) => ({
  date: "2026-10-01",
  type: "expense",
  amount: 250,
  merchant: "Swiggy",
  category: "Food",
  paymentMethod: "UPI",
  note: "UPI/41235/swiggy@icici",
  ...over,
});

Deno.test("rows the server would refuse are dropped", () => {
  const cleaned = JSON.parse(cleanStatementText(JSON.stringify({
    bankOrApp: "PhonePe",
    statementPeriod: "Oct 2026",
    transactions: [row(), row({ amount: 1e12 }), row({ amount: -5 }), row({ amount: "NaN" }), row({ amount: "1200.5" })],
  })));
  assertEquals(cleaned.transactions.map((r: { amount: number }) => r.amount), [250, 1200.5]);
});

Deno.test("text is cut to what the app keeps", () => {
  const cleaned = JSON.parse(cleanStatementText(JSON.stringify({
    transactions: [row({ merchant: "M".repeat(500), note: "N".repeat(900) })],
  })));
  assertEquals(cleaned.transactions[0].merchant.length, 80);
  assertEquals(cleaned.transactions[0].note.length, 200);
});

Deno.test("no more than a thousand rows", () => {
  const many = { transactions: Array.from({ length: 5000 }, () => row()) };
  assertEquals(JSON.parse(cleanStatementText(JSON.stringify(many))).transactions.length, MAX_ROWS);
});

Deno.test("an answer that isn't statement JSON passes through for the app to call unreadable", () => {
  assertEquals(cleanStatementText("sorry, I can't read that"), "sorry, I can't read that");
});

Deno.test("only answer parts are cleaned; thoughts are left alone", () => {
  const envelope = {
    candidates: [{
      finishReason: "STOP",
      content: { parts: [{ text: "thinking", thought: true }, { text: JSON.stringify({ transactions: [row({ amount: 0 })] }) }] },
    }],
  };
  const parts = (cleanStatementReply(envelope).candidates as { content: { parts: { text: string }[] } }[])[0].content.parts;
  assertEquals(parts[0].text, "thinking");
  assertEquals(JSON.parse(parts[1].text).transactions, []);
});
