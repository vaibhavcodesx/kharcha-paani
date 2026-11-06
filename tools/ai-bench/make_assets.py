"""Draws the invented test bills (PNG, then JPEG the way the app sends them) and speaks the voice lines.

Run once: python tools/ai-bench/make_assets.py  -> tools/ai-bench/assets/
"""
import io
import os
import random
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cases import BILLS, VOICE_CASES  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets")
FONTS = "C:/Windows/Fonts"


def font(name, size):
    for candidate in (name, "arial.ttf"):
        try:
            return ImageFont.truetype(os.path.join(FONTS, candidate), size)
        except OSError:
            continue
    return ImageFont.load_default()


def ddmmyyyy(iso):
    y, m, d = iso.split("-")
    return f"{d}/{m}/{y}"


def money(v):
    return f"{v:,.2f}"


def receipt(bill):
    w, h = 900, 1500
    img = Image.new("RGB", (w, h), (250, 248, 242))
    d = ImageDraw.Draw(img)
    mono, bold = font("consola.ttf", 30), font("arialbd.ttf", 44)
    y = 50
    d.text((w // 2, y), bill["merchant"].upper(), font=bold, fill=(20, 20, 20), anchor="mt"); y += 70
    for line in ("Shop 12, MG Road, Bengaluru 560001", "GSTIN: 29ABCDE1234F1Z5", f"Date: {ddmmyyyy(bill['date'])}  Time: 13:05",
                 f"Bill No: {random.randint(1000, 9999)}"):
        d.text((w // 2, y), line, font=mono, fill=(40, 40, 40), anchor="mt"); y += 42
    y += 20; d.line((40, y, w - 40, y), fill=(0, 0, 0), width=2); y += 25
    sub = 0
    for name, price in bill["items"]:
        d.text((50, y), name, font=mono, fill=(0, 0, 0)); d.text((w - 50, y), money(price), font=mono, fill=(0, 0, 0), anchor="ra")
        sub += price; y += 44
    y += 10; d.line((40, y, w - 40, y), fill=(0, 0, 0), width=1); y += 25
    rows = [("Sub Total", sub)]
    if bill.get("discount"): rows.append(("Discount", -bill["discount"]))
    if bill.get("cgst"): rows += [("CGST @2.5%", bill["cgst"]), ("SGST @2.5%", bill["sgst"])]
    if bill.get("tip"): rows.append(("Tip", bill["tip"]))
    for label, value in rows:
        d.text((50, y), label, font=mono, fill=(0, 0, 0)); d.text((w - 50, y), money(value), font=mono, fill=(0, 0, 0), anchor="ra"); y += 44
    y += 10
    d.text((50, y), "GRAND TOTAL", font=font("consolab.ttf", 38), fill=(0, 0, 0))
    d.text((w - 50, y), "Rs. " + money(bill["total"]), font=font("consolab.ttf", 38), fill=(0, 0, 0), anchor="ra"); y += 70
    pay = bill["payment"] if isinstance(bill["payment"], str) else bill["payment"][0]
    d.text((50, y), f"Paid by: {pay}", font=mono, fill=(0, 0, 0)); y += 60
    d.text((w // 2, y), "Thank you! Visit again", font=mono, fill=(60, 60, 60), anchor="mt")
    return img.crop((0, 0, w, min(h, y + 80)))


def upi(bill, received=False):
    w, h = 1080, 1900
    img = Image.new("RGB", (w, h), (255, 255, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, w, 220), fill=(32, 33, 36))
    d.text((60, 110), "Payment received" if received else "Payment successful", font=font("arialbd.ttf", 52), fill=(255, 255, 255), anchor="lm")
    d.ellipse((w // 2 - 90, 330, w // 2 + 90, 510), fill=(30, 142, 62))
    d.text((w // 2, 420), "OK", font=font("arialbd.ttf", 70), fill=(255, 255, 255), anchor="mm")
    d.text((w // 2, 640), "₹" + f"{bill['total']:,.0f}", font=font("arialbd.ttf", 120), fill=(0, 0, 0), anchor="mm")
    d.text((w // 2, 780), ("From " if received else "Paid to ") + bill["merchant"], font=font("arial.ttf", 54), fill=(30, 30, 30), anchor="mm")
    d.text((w // 2, 860), bill["merchant"].split()[0].lower() + "@okaxis", font=font("arial.ttf", 40), fill=(110, 110, 110), anchor="mm")
    d.text((w // 2, 1000), f"{int(bill['date'][8:])} Nov 2026, 7:42 pm", font=font("arial.ttf", 44), fill=(60, 60, 60), anchor="mm")
    d.text((w // 2, 1080), "UPI transaction ID: 6031" + str(random.randint(10 ** 7, 10 ** 8)), font=font("arial.ttf", 38), fill=(110, 110, 110), anchor="mm")
    return img


def utility(bill):
    img = receipt({**bill, "cgst": 0, "sgst": 0})
    d = ImageDraw.Draw(img)
    d.text((50, img.height - 40), "Consumer No: 9000123456  Due: 20/11/2026", font=font("consola.ttf", 26), fill=(80, 0, 0))
    return img


def as_app_jpeg(img):
    """The app's bitmapToBase64: long side at most 1600 px, JPEG quality 85."""
    if max(img.size) > 1600:
        s = 1600 / max(img.size)
        img = img.resize((int(img.width * s), int(img.height * s)), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=85)
    return buf.getvalue()


def bills():
    random.seed(7)
    os.makedirs(OUT, exist_ok=True)
    for bill in BILLS:
        kind = bill["kind"]
        img = upi(bill) if kind == "upi" else upi(bill, True) if kind == "upi_in" else utility(bill) if kind == "utility" else receipt(bill)
        if bill.get("rotate"):
            img = img.rotate(bill["rotate"], expand=True, fillcolor=(120, 110, 100))
        if bill.get("blur"):
            img = img.filter(ImageFilter.GaussianBlur(1.6))
        # A phone photo, not a scan: a little noise and warmth.
        img = Image.blend(img, Image.effect_noise(img.size, 18).convert("RGB"), 0.06)
        with open(os.path.join(OUT, f"bill_{bill['id']}.jpg"), "wb") as f:
            f.write(as_app_jpeg(img))
    print("bills:", len(BILLS))


def voices():
    os.makedirs(OUT, exist_ok=True)
    for i, c in enumerate(VOICE_CASES):
        path = os.path.join(OUT, f"voice_{i:02d}.wav")
        script = (
            "Add-Type -AssemblyName System.Speech;"
            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;"
            "$f = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono);"
            f"$s.SetOutputToWaveFile('{path}', $f); $s.Rate = 1; $s.Speak('{c['line']}'); $s.Dispose()"
        )
        subprocess.run(["powershell", "-NoProfile", "-Command", script], check=True)
    print("voices:", len(VOICE_CASES))


if __name__ == "__main__":
    bills()
    voices()
