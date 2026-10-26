# Play Store listing draft (X1 / T2.9)

English (India) copy for adults tracking spending and splitting with friends. This is a
reviewable text and art handoff; screenshots and the feature graphic still need producing
and owner approval before upload. No Play Console settings are changed by this item.

## Copy to paste

- App name: **Kharcha Paani** (13 characters).
- [Short description](short-description.txt): at most 80 characters, including spaces.
- [Full description](full-description.txt): at most 4,000 characters, including line breaks.
- [Screenshot plan](screenshots.md): six ordered captures, with the first four the minimum set.
- [Feature graphic brief](feature-graphic.md): a 1024×500 asset for the designer.

The `.txt` files contain only listing copy. Captions are the quoted cells in the screenshot
table and the quoted headline in the graphic brief. Internal production notes are not copy.

## Basis and boundaries

Follow [the lane contract](../agents/work-split.md), X1, and the
[Play audit](../production-readiness/audits/play-store.md), §4 (financial wording) and §9
(listing assets). Specs below are the repo's acceptance criteria, not a new policy audit.
[PRD](../../PRD.md) supplies product scope; owner decisions §10 D2/D5 in the
[work order](../production-readiness/README.md#10-owner-decisions-2026-10-24-these-override-the-tiers-above)
control AI disclosure and pricing. No pricing or upcoming features are advertised.

The listing describes records and splits. UPI opens the user's own payment app; Kharcha Paani
does not handle money. AI needs a connection and is optional. The copy discloses Gemini's
possible product-improvement use and human review under D2; recheck against the release's
actual notice and privacy policy if that arrangement changes.

Source checks: `ui/home/CaptureCard.kt` has quick add, voice and bill entry;
`ui/components/GroupDetailSheet.kt` has shared expense and balance views;
`ui/screens/InsightsScreen.kt` has monthly/category views. These paths are relative to
`app/src/main/java/com/vaibhav/kharchapaani/`. The PRD also covers exports and backups.
No future budget limits, automatic bank connections, instant sync or automatic payments
are promised. The product is initially without charges per D5; promotional pricing language
is deliberately absent from the public copy per X1.

## Validate and hand off

Run from the repo root with Python 3 (standard library only):

```text
python docs/store-listing/test_listing.py
```

Tests read the real copy, enforce description limits and restricted vocabulary in public
copy, check six ordered captions and the graphic size, and verify rejection of bad examples.
They cannot verify artwork, privacy, UI accuracy or Play approval; check those manually.

Before upload, the owner should:

- [ ] Compare each claimed feature with the release installed on the phone; confirm the AI
      notice and policy still match the copy.
- [ ] Capture the planned screens, with the fictional fixture and no personal data, then
      compare every rupee amount and caption with the actual screen.
- [ ] Review images at phone size for readable, complete amounts, safe insets and no
      payment-brand marks, restricted words or unintended notifications.
- [ ] Approve the final PNG/JPEG files and paste the two text files into the English (India)
      listing. Console declarations and legal pages remain separate owner/release tasks.

No blocking owner questions. Safe defaults: English (India), the existing app name, six
portrait captures, no payment QR in art, and conservative wording for online features.
