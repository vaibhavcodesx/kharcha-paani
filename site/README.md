# The public pages: privacy policy, terms, account deletion

Play needs these at a public web address (plan step O6). This repository is private, so they're
published from a small public one. The pages are built from `docs/legal/*.md`; don't edit the HTML
here by hand.

## Publishing (once, about five minutes)

1. Rebuild the pages: `python scripts/legal_site.py`.
2. Create a **public** repository named `kharcha-paani-site` on GitHub (no README). Copy the four
   `.html` files from `site/` into a new, empty folder **outside** this repository, and in that folder:

   ```bash
   git init -b main && git add *.html && git commit -m "Privacy policy, terms, account deletion"
   git remote add origin https://github.com/vaibhavcodesx/kharcha-paani-site.git
   git push -u origin main
   ```

3. In `kharcha-paani-site` → Settings → Pages: Source **Deploy from a branch**, branch **main**, folder
   **/ (root)**. After a minute the pages are at:
   - https://vaibhavcodesx.github.io/kharcha-paani-site/privacy.html
   - https://vaibhavcodesx.github.io/kharcha-paani-site/terms.html
   - https://vaibhavcodesx.github.io/kharcha-paani-site/delete-account.html

These are the addresses the app opens (`util/LegalLinks.kt`) and the ones the Play Console answers use
(`docs/legal/play-console-answers.md`). If you choose another address (your own domain, say), change
`LegalLinks.BASE` and those answers to match.

## The bill-split page

`split.html` is different: it is written by hand (it is a small app, not a document), so edit it
directly. It is the page a "Split by items" link opens (docs/designs/split-by-items.md). Copy it into
`kharcha-paani-site` beside the others; the app's links point at
https://vaibhavcodesx.github.io/kharcha-paani-site/split.html. After changing its arithmetic, run
`node scripts/check_split_page.mjs`.

## After a change

Edit `docs/legal/*.md`, run `python scripts/legal_site.py`, copy the HTML into the
`kharcha-paani-site` repository and push. Update "Last updated" at the top of the page you changed.

## The owner admin page (`admin/`)

The owner admin page source lives at `site/admin/`. Unlike the static legal pages on GitHub Pages, it is a Vite + React + TypeScript app deployed to **Vercel** as its own project under the owner's Vercel account (owner decision 2026-11-19, docs/designs/admin-page.md).

To publish:
1. In Vercel, create a new project importing this repository with Root Directory set to `site/admin`.
2. Vercel runs `npm ci && npm run build` and serves `dist/` using headers configured in `vercel.json`.
3. Add the deployed production URL to Supabase Auth > URL Configuration > Redirect URLs.
4. Follow [docs/engineering/runbooks/admin-page.md](../docs/engineering/runbooks/admin-page.md) for full apply, verify, and rollback steps.
