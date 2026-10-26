"""Builds the public legal pages in site/ from docs/legal/*.md.

    python scripts/legal_site.py

The Markdown in docs/legal is the source; site/ is what gets published (see site/README.md), so the
hosted policy can't drift from the one in the repo. Handles what those pages use: headings,
paragraphs, bullet lists, **bold**, `code` and [links](url).
"""
import html
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
PAGES = {
    "privacy-policy.md": "privacy.html",
    "terms.md": "terms.html",
    "delete-account.md": "delete-account.html",
}

STYLE = """
:root { color-scheme: light dark; --bg: #fbf9f6; --fg: #1f1d1a; --muted: #6b645b; --accent: #c4501c; }
@media (prefers-color-scheme: dark) { :root { --bg: #0e0d0c; --fg: #f4f0e8; --muted: #a39b90; --accent: #ff7a3d; } }
body { margin: 0; background: var(--bg); color: var(--fg); font: 17px/1.6 system-ui, -apple-system, "Segoe UI", sans-serif; }
main { max-width: 42rem; margin: 0 auto; padding: 2.5rem 1rem 4rem; }
h1 { font-size: 1.8rem; line-height: 1.25; margin: 0 0 .5rem; }
h2 { font-size: 1.2rem; margin: 2rem 0 .5rem; }
a { color: var(--accent); }
code { font-size: .92em; }
nav { font-size: .95rem; color: var(--muted); margin-bottom: 2rem; }
nav a { margin-right: 1rem; }
"""


def inline(text):
    text = html.escape(text, quote=False)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"`(.+?)`", r"<code>\1</code>", text)
    text = re.sub(r"\[(.+?)\]\((.+?)\)", r'<a href="\2">\1</a>', text)
    return text


def render(markdown):
    out, para, items = [], [], []

    def flush():
        if para:
            out.append("<p>" + inline(" ".join(para)) + "</p>")
            para.clear()
        if items:
            out.append("<ul>" + "".join("<li>" + inline(i) + "</li>" for i in items) + "</ul>")
            items.clear()

    title = "Kharcha Paani"
    for line in markdown.splitlines():
        if line.startswith("# "):
            flush()
            title = line[2:].strip()
            out.append("<h1>" + inline(title) + "</h1>")
        elif line.startswith("## "):
            flush()
            out.append("<h2>" + inline(line[3:].strip()) + "</h2>")
        elif line.startswith("- "):
            if para:
                flush()
            items.append(line[2:].strip())
        elif line.startswith("  ") and items:
            items[-1] += " " + line.strip()
        elif not line.strip():
            flush()
        else:
            para.append(line.strip())
    flush()
    return title, "\n".join(out)


def page(title, body):
    nav = '<nav><a href="privacy.html">Privacy</a><a href="terms.html">Terms</a><a href="delete-account.html">Delete account</a></nav>'
    return (
        '<!doctype html>\n<html lang="en"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width, initial-scale=1">'
        f"<title>{html.escape(title)}</title><style>{STYLE}</style></head>"
        f"<body><main>{nav}\n{body}\n</main></body></html>\n"
    )


def main():
    site = ROOT / "site"
    site.mkdir(exist_ok=True)
    for source, target in PAGES.items():
        title, body = render((ROOT / "docs" / "legal" / source).read_text(encoding="utf-8"))
        (site / target).write_text(page(title, body), encoding="utf-8", newline="\n")
        print("wrote site/" + target)
    index = '<h1>Kharcha Paani</h1><p>Track what you spend, and split it with friends.</p>'
    (site / "index.html").write_text(page("Kharcha Paani", index), encoding="utf-8", newline="\n")
    print("wrote site/index.html")


if __name__ == "__main__":
    main()
