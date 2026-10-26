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

## After a change

Edit `docs/legal/*.md`, run `python scripts/legal_site.py`, copy the HTML into the
`kharcha-paani-site` repository and push. Update "Last updated" at the top of the page you changed.
