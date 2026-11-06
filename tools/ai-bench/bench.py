"""Speed and accuracy of Gemini and Groq models on Kharcha Paani's three AI jobs.

    python tools/ai-bench/make_assets.py           # once
    python tools/ai-bench/bench.py --list          # which models the keys can use
    python tools/ai-bench/bench.py                 # everything; writes results/ and a report

Keys come only from %USERPROFILE%/.kharcha-paani/ai-keys.env (GROQ_API_KEY=..., GEMINI_API_KEY=...).
They are sent as headers, never in a URL, and never printed. Every test case is invented.

Timing is wall time from this laptop to the provider and back. The app goes phone -> Supabase
Edge Function -> provider, which adds one hop; the ranking between models is what this measures.
"""
import argparse
import base64
import concurrent.futures as cf
import datetime as dt
import json
import os
import re
import statistics
import sys
import threading
import time

import requests

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from cases import (BILLS, EXPENSE_CATEGORIES, FRIENDS, INCOME_CATEGORIES, PAYMENT_METHODS, TEXT_CASES,  # noqa: E402
                   TODAY, VOICE_CASES)

KEY_FILE = os.path.join(os.path.expanduser("~"), ".kharcha-paani", "ai-keys.env")
GEMINI = "https://generativelanguage.googleapis.com/v1beta"
GROQ = "https://api.groq.com/openai/v1"
CATEGORIES = ", ".join(EXPENSE_CATEGORIES + INCOME_CATEGORIES)
PAYMENTS = ", ".join(PAYMENT_METHODS)
TIMEOUT = 60


def keys():
    found = {}
    if os.path.exists(KEY_FILE):
        for line in open(KEY_FILE, encoding="utf-8-sig"):
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.split("=", 1)
                if v.strip():
                    found[k.strip()] = v.strip().strip('"').strip("'")
    return found


# --- The app's own prompts (GeminiRepository.textPrompt / receiptPrompt), with the same lists. ---

def text_prompt(line, audio=False):
    said = ("The line is in the attached audio: what the person said." if audio else f"The line: {json.dumps(line)}")
    extra = '\n- transcript: what you heard, written out as text ("" if nobody said anything).' if audio else ""
    example_extra = ',"transcript":"burger three seventy mcd gpay"' if audio else ""
    return f"""You turn one line a person typed or said into an entry for an Indian expense app. Amounts are
in rupees.

Today is {TODAY}. Friends on this account: {", ".join(FRIENDS)}.

{said}

Fill in:
- type: "income" only if money came in (salary, refund, client paid, received); otherwise "expense".
- amount: the total as a plain number. "1,299" is 1299, "1.2k" is 1200, "2 lakh" is 200000. If
  several numbers appear, it is the money, not a count ("2 coffees 300" is 300). 0 if there is
  no amount.
- merchant: the place or thing, short and properly capitalised ("Swiggy", "Auto to office",
  "Rohan (loan)"). Never include the amount or the payment method.
- category: exactly one of [{CATEGORIES}].
- paymentMethod: exactly one of [{PAYMENTS}]. If the line doesn't say, use the first
  one in that list.
- date: "YYYY-MM-DD" only if the line says when ("yesterday", "on 12 Sep", "last Friday"),
  worked out from today's date; otherwise "".
- splitWith: the people the line says this was shared with ("with Rohan", "split with Ankit
  bhai"). A friend from the list exactly as spelled there; anyone else as written. Never the
  person themself ("me", "I") and never a group or trip name. [] if none.
- note: the original line.{extra}

Return only this JSON:
{{"type":"expense","amount":370,"merchant":"McDonald's","category":"Food","paymentMethod":"GPay","date":"","splitWith":[],"note":"burger 370 mcd gpay"{example_extra}}}"""


def receipt_prompt():
    return f"""This image is a bill, receipt, invoice or payment-app screenshot (GPay, PhonePe, Paytm, a bank
SMS or confirmation) from India. Today is {TODAY}.

Fill in:
- type: "income" if this shows money received; otherwise "expense".
- amount: the total actually paid, as a plain number: the grand total after tax, discounts and
  tip, not a subtotal or one line item. For a payment screenshot, the amount paid. 0 if you
  can't find it, or if this isn't a bill or payment at all. Never guess.
- merchant: who was paid, as a person would say it ("Starbucks", "Swiggy", "Ramesh Kumar"),
  not a UPI handle or reference number.
- category: exactly one of [{CATEGORIES}].
- paymentMethod: exactly one of [{PAYMENTS}] if the image shows how it was paid,
  otherwise "".
- date: when it was paid, "YYYY-MM-DD", or "YYYY-MM-DDTHH:mm" if a time is shown; "" if no date
  is visible. Indian receipts write dates day first (05/09 is 5 September).
- note: a short description, or the order or reference number if that's all there is.

Return only this JSON:
{{"type":"expense","amount":250,"merchant":"Swiggy","category":"Food","paymentMethod":"GPay","date":"2026-09-24T13:05","note":"Order 4412"}}"""


# --- Providers ---

class Paced:
    """Keeps each model under its free-tier requests per minute."""

    def __init__(self, per_minute):
        self.gap = 60.0 / per_minute
        self.lock = threading.Lock()
        self.last = 0.0

    def wait(self):
        with self.lock:
            delay = self.last + self.gap - time.monotonic()
            if delay > 0:
                time.sleep(delay)
            self.last = time.monotonic()


def gemini_call(key, model, parts, thinking=True):
    body = {"contents": [{"role": "user", "parts": parts}],
            "generationConfig": {"temperature": 0.1, "responseMimeType": "application/json"}}
    if thinking:
        # The app asks Gemini 3 models for "low" thinking on quick reads.
        body["generationConfig"]["thinkingConfig"] = {"thinkingLevel": "low"}
    t = time.perf_counter()
    r = requests.post(f"{GEMINI}/models/{model}:generateContent", headers={"x-goog-api-key": key}, json=body, timeout=TIMEOUT)
    ms = (time.perf_counter() - t) * 1000
    if r.status_code == 400 and thinking and "thinking" in r.text.lower():
        return gemini_call(key, model, parts, thinking=False)
    if r.status_code != 200:
        return None, ms, f"HTTP {r.status_code}: {r.text[:160]}"
    try:
        text = "".join(p.get("text", "") for p in r.json()["candidates"][0]["content"]["parts"] if not p.get("thought"))
        return text, ms, None
    except Exception as e:  # noqa: BLE001
        return None, ms, f"bad response: {e}"


def groq_chat(key, model, content, json_mode=True):
    body = {"model": model, "temperature": 0.1, "messages": [{"role": "user", "content": content}]}
    if json_mode:
        body["response_format"] = {"type": "json_object"}
    if "gpt-oss" in model:
        body["reasoning_effort"] = "low"
    t = time.perf_counter()
    r = requests.post(f"{GROQ}/chat/completions", headers={"Authorization": f"Bearer {key}"}, json=body, timeout=TIMEOUT)
    ms = (time.perf_counter() - t) * 1000
    if r.status_code == 400 and json_mode and "response_format" in r.text:
        return groq_chat(key, model, content, json_mode=False)
    if r.status_code != 200:
        return None, ms, f"HTTP {r.status_code}: {r.text[:160]}"
    return r.json()["choices"][0]["message"]["content"], ms, None


def groq_transcribe(key, model, wav_path):
    t = time.perf_counter()
    with open(wav_path, "rb") as f:
        r = requests.post(f"{GROQ}/audio/transcriptions", headers={"Authorization": f"Bearer {key}"},
                          files={"file": ("clip.wav", f, "audio/wav")}, data={"model": model, "response_format": "json"}, timeout=TIMEOUT)
    ms = (time.perf_counter() - t) * 1000
    if r.status_code != 200:
        return None, ms, f"HTTP {r.status_code}: {r.text[:160]}"
    return r.json().get("text", ""), ms, None


def list_models(k):
    found = {"gemini": [], "groq": []}
    if k.get("GEMINI_API_KEY"):
        r = requests.get(f"{GEMINI}/models?pageSize=200", headers={"x-goog-api-key": k["GEMINI_API_KEY"]}, timeout=30)
        r.raise_for_status()
        for m in r.json().get("models", []):
            name = m["name"].split("/", 1)[1]
            if "generateContent" not in m.get("supportedGenerationMethods", []):
                continue
            if not re.match(r"gemini-(2\.5|[3-9])", name):
                continue
            if any(x in name for x in ("image", "tts", "live", "embedding", "computer-use", "robotics", "audio", "exp", "customtools")):
                continue
            found["gemini"].append(name)
    if k.get("GROQ_API_KEY"):
        r = requests.get(f"{GROQ}/models", headers={"Authorization": f"Bearer {k['GROQ_API_KEY']}"}, timeout=30)
        r.raise_for_status()
        found["groq"] = sorted(m["id"] for m in r.json()["data"] if m.get("active", True))
    return found


def groq_kind(model):
    if "whisper" in model:
        return "stt"
    if any(x in model for x in ("guard", "tts", "playai", "orpheus", "compound", "prompt-guard", "safeguard", "allam")):
        return None
    if any(x in model for x in ("llama-4", "vision", "scout", "maverick", "-vl")):
        return "vision"
    return "text"


# --- Scoring ---

def parse_json(text):
    if not text:
        return None
    m = re.search(r"\{.*\}", text, re.S)
    try:
        return json.loads(m.group(0) if m else text)
    except Exception:  # noqa: BLE001
        return None


def num(v):
    try:
        return float(str(v).replace(",", "").replace("₹", "").strip() or 0)
    except ValueError:
        return None


def close(a, b):
    a, b = a.lower(), b.lower()
    return bool(a) and bool(a in b or b in a or set(re.findall(r"[a-z]+", a)) & set(re.findall(r"[a-z]+", b)))


def score(expected, got):
    if not isinstance(got, dict):
        return {f: False for f in ("amount", "type", "merchant", "category", "payment", "date", "split")}
    pay = expected["payment"] if isinstance(expected["payment"], (tuple, list)) else (expected["payment"],)
    cat = expected["category"] if isinstance(expected["category"], (tuple, list)) else (expected["category"],)
    amount = num(got.get("amount"))
    split = sorted(str(s).strip().lower() for s in (got.get("splitWith") or []))
    return {
        "amount": amount is not None and abs(amount - float(expected["amount"] if "amount" in expected else expected["total"])) < 0.02,
        "type": str(got.get("type", "expense")).lower() == expected.get("type", "expense"),
        "merchant": close(str(got.get("merchant", "")), expected["merchant"]),
        "category": str(got.get("category", "")) in cat,
        "payment": str(got.get("paymentMethod", "")) in pay,
        "date": str(got.get("date", ""))[:10] == expected.get("date", ""),
        "split": split == sorted(s.lower() for s in expected.get("split", [])),
    }


# --- Jobs ---

def jobs_for(models, k):
    out = []
    for m in models["gemini"]:
        out.append(("gemini", m, "text"))
        out.append(("gemini", m, "bill"))
        out.append(("gemini", m, "voice"))
    for m in models["groq"]:
        kind = groq_kind(m)
        if kind == "text":
            out.append(("groq", m, "text"))
        elif kind == "vision":
            out += [("groq", m, "text"), ("groq", m, "bill")]
        elif kind == "stt":
            out.append(("groq", m, "voice"))
    return out


def safe(fn, *a):
    """One call that can't take the whole run down: a timeout is recorded as that entry's error."""
    try:
        return fn(*a)
    except requests.RequestException as e:
        return None, TIMEOUT * 1000.0, f"network: {type(e).__name__}"


def run_job(provider, model, task, k, rpm, voice_text_model):
    pacer = Paced(rpm)
    rows = []
    key = k["GEMINI_API_KEY" if provider == "gemini" else "GROQ_API_KEY"]
    if task == "text":
        cases = [(c, c["line"]) for c in TEXT_CASES]
    elif task == "bill":
        cases = [(b, os.path.join(HERE, "assets", f"bill_{b['id']}.jpg")) for b in BILLS]
    else:
        cases = [(c, os.path.join(HERE, "assets", f"voice_{i:02d}.wav")) for i, c in enumerate(VOICE_CASES)]
    for expected, item in cases:
        pacer.wait()
        stt_ms, heard = 0.0, None
        if provider == "gemini":
            if task == "text":
                parts = [{"text": text_prompt(item)}]
            elif task == "bill":
                parts = [{"text": receipt_prompt()}, {"inlineData": {"mimeType": "image/jpeg", "data": base64.b64encode(open(item, "rb").read()).decode()}}]
            else:
                parts = [{"text": text_prompt(None, audio=True)}, {"inlineData": {"mimeType": "audio/wav", "data": base64.b64encode(open(item, "rb").read()).decode()}}]
            text, ms, err = safe(gemini_call, key, model, parts)
        else:
            if task == "text":
                text, ms, err = safe(groq_chat, key, model, text_prompt(item))
            elif task == "bill":
                img = base64.b64encode(open(item, "rb").read()).decode()
                text, ms, err = groq_chat(key, model, [{"type": "text", "text": receipt_prompt()},
                                                       {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{img}"}}])
            else:
                heard, stt_ms, err = safe(groq_transcribe, key, model, item)
                text, ms = None, stt_ms
                if heard is not None:
                    pacer.wait()
                    text, parse_ms, err = safe(groq_chat, key, voice_text_model, text_prompt(heard))
                    ms = stt_ms + parse_ms
        got = parse_json(text)
        rows.append({"case": expected.get("line") or expected.get("id"), "ms": round(ms), "stt_ms": round(stt_ms) or None,
                     "heard": heard, "error": err, "got": got, "fields": score(expected, got)})
        print(f"  {provider}:{model}:{task} {rows[-1]['case'][:28]!r} {round(ms)} ms {'ERR ' + err[:60] if err else ''}", flush=True)
    return {"provider": provider, "model": model, "task": task, "rows": rows}


def summary(result):
    rows = result["rows"]
    ok = [r for r in rows if not r["error"]]
    ms = sorted(r["ms"] for r in ok)
    fields = [f for r in rows for f in r["fields"].values()]
    return {
        "provider": result["provider"], "model": result["model"], "task": result["task"],
        "n": len(rows), "errors": len(rows) - len(ok),
        "p50_ms": round(statistics.median(ms)) if ms else None,
        "p90_ms": round(ms[min(len(ms) - 1, int(len(ms) * 0.9))]) if ms else None,
        "amount_ok": round(100 * sum(r["fields"]["amount"] for r in rows) / len(rows)),
        "fields_ok": round(100 * sum(fields) / len(fields)) if fields else 0,
        "all_ok": round(100 * sum(all(r["fields"].values()) for r in rows) / len(rows)),
    }


def report(results, path):
    sums = [summary(r) for r in results]
    lines = [f"# AI benchmark: quick add, bill photos and voice ({dt.date.today()})", "",
             "Invented cases from `tools/ai-bench/cases.py`, the app's own prompts, timed from the owner's laptop in India.",
             "Wall time per entry; the app adds one hop through the Supabase Edge Function. Free-tier keys.", ""]
    for task, title in (("text", "Quick add (typed line)"), ("bill", "Bill photo"), ("voice", "Voice")):
        rows = sorted((s for s in sums if s["task"] == task), key=lambda s: (-s["amount_ok"], -s["fields_ok"], s["p50_ms"] or 9e9))
        if not rows:
            continue
        lines += [f"## {title}", "", "| Model | p50 | p90 | Amount right | Fields right | Whole entry right | Errors |", "|---|---:|---:|---:|---:|---:|---:|"]
        for s in rows:
            lines.append(f"| {s['provider']} `{s['model']}` | {s['p50_ms']} ms | {s['p90_ms']} ms | {s['amount_ok']}% | {s['fields_ok']}% | {s['all_ok']}% | {s['errors']}/{s['n']} |")
        lines.append("")
    open(path, "w", encoding="utf-8").write("\n".join(lines))
    return sums


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--only", help="substring filter on model ids")
    ap.add_argument("--models", help="comma-separated exact model ids to run")
    ap.add_argument("--gemini-rpm", type=float, default=5)
    ap.add_argument("--groq-rpm", type=float, default=4.5, help="free gpt-oss is 8K tokens/min: about 5 entries")
    ap.add_argument("--tasks", default="text,bill,voice")
    ap.add_argument("--voice-text-model", default="openai/gpt-oss-20b", help="Groq model that parses Whisper's transcript")
    args = ap.parse_args()
    k = keys()
    if not k:
        sys.exit(f"No keys: put GROQ_API_KEY=... and GEMINI_API_KEY=... in {KEY_FILE}")
    print("keys present:", ", ".join(sorted(k)))
    models = list_models(k)
    if args.list:
        print(json.dumps({p: [(m, groq_kind(m) if p == "groq" else "all") for m in ms] for p, ms in models.items()}, indent=1))
        return
    jobs = [j for j in jobs_for(models, k) if j[2] in args.tasks.split(",") and (not args.only or args.only in j[1]) and (not args.models or j[1] in args.models.split(","))]
    print(f"{len(jobs)} model x task runs")
    results = []
    with cf.ThreadPoolExecutor(max_workers=8) as pool:
        futures = [pool.submit(run_job, p, m, t, k, args.gemini_rpm if p == "gemini" else args.groq_rpm, args.voice_text_model) for p, m, t in jobs]
        for f in cf.as_completed(futures):
            try:
                results.append(f.result())
            except Exception as e:  # noqa: BLE001
                print("run failed:", type(e).__name__, str(e)[:120])
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M")
    os.makedirs(os.path.join(HERE, "results"), exist_ok=True)
    json.dump(results, open(os.path.join(HERE, "results", f"raw-{stamp}.json"), "w", encoding="utf-8"), indent=1, default=str)
    out = os.path.join(HERE, "results", f"report-{stamp}.md")
    report(results, out)
    print("report:", out)


if __name__ == "__main__":
    main()
