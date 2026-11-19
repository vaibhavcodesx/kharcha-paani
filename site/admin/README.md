# Admin Page (`site/admin`)

One-page dashboard for the owner to manage the closed test without the Supabase table editor (see [docs/designs/admin-page.md](../../docs/designs/admin-page.md) and [docs/engineering/runbooks/admin-page.md](../../docs/engineering/runbooks/admin-page.md)).

## Commands

- `npm ci`: install pinned dependencies.
- `npm run dev:mock`: preview with sample data (variants `?paused ?leftover ?empty ?slow ?error`, never part of the build).
- `npm test`: run unit tests via Vitest.
- `npm run build`: compile TypeScript and build bundle (`tsc --noEmit && vite build`).

## Deployment

Deployed as a Vercel project with root directory `site/admin` and security headers defined in `vercel.json`; the owner does the deploy.

## Security

No secret or service key may ever be in the page; the publishable key in `src/config.ts` is public by design and all actions are authorized in the database.

## Design Tokens

Design tokens live in `src/styles/tokens.css`; new screens use rows on hairlines, not cards.

## CI

Pull requests touching `site/admin/` run `.github/workflows/admin-site.yml` to lint, test, build, and verify no secrets or source maps are in `dist/`.
