"""Listing-copy acceptance checks. Run with Python 3; no Android SDK required."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parent
BANNED = re.compile(
    r"\b(?:best|top|new|free|discount|loan|loans|lend|lending|borrow|borrowing|credit|"
    r"gpay|phonepe|paytm)\b|#1|download now|\b\d[\d,.]*\+?\s+installs\b",
    re.IGNORECASE,
)


def read(name):
    return (ROOT / name).read_text(encoding="utf-8-sig").strip()


def validate_copy(text, limit):
    if not text or len(text) > limit:
        raise ValueError("Copy is empty or exceeds the character limit")
    if BANNED.search(text):
        raise ValueError("Restricted public wording")


class ListingTests(unittest.TestCase):
    def test_short_description(self):
        text = read("short-description.txt")
        self.assertEqual(1, len(text.splitlines()))
        validate_copy(text, 80)

    def test_full_description(self):
        text = read("full-description.txt")
        validate_copy(text, 4000)
        for disclosure in (
            "does not hold or transfer money", "internet connection",
            "Google Gemini", "human reviewers may see it", "manual entry",
            "daily usage limits", "18 and over", "Google sign-in is required",
        ):
            self.assertIn(disclosure, text)

    def test_screenshot_captions(self):
        text = read("screenshots.md")
        rows = re.findall(r'^\| (\d) \| (\S+\.png) \| (.+?) \| "(.+?)" \|$', text, re.M)
        self.assertEqual([str(i) for i in range(1, 7)], [row[0] for row in rows])
        self.assertEqual(6, len({row[1] for row in rows}))
        self.assertIn("1080×1920", text)
        for _, _, state, caption in rows:
            self.assertTrue(state)
            validate_copy(caption, 80)

    def test_feature_graphic_copy(self):
        text = read("feature-graphic.md")
        self.assertIn("1024×500", text)
        self.assertIn("24-bit PNG without alpha or JPEG", text)
        headline = re.findall(r'^- Headline: "(.+)"$', text, re.M)
        self.assertEqual(1, len(headline))
        validate_copy(headline[0], 80)
        validate_copy("Kharcha Paani", 30)

    def test_rejects_restricted_copy(self):
        for text in ("Best splits", "#1 tracker", "Free", "Loan tracker", "Lend money",
                     "Borrow today", "Credit", "GPay", "PhonePe", "Paytm",
                     "Download now", "10,000+ installs"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                validate_copy(text, 80)

    def test_character_boundaries(self):
        validate_copy("a" * 80, 80)
        validate_copy("a" * 4000, 4000)
        for text, limit in (("", 80), ("a" * 81, 80), ("a" * 4001, 4000)):
            with self.subTest(limit=limit), self.assertRaises(ValueError):
                validate_copy(text, limit)


if __name__ == "__main__":
    unittest.main(verbosity=2)
