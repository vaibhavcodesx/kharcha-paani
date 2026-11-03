# Feature graphic brief

## Deliverable

One **1024×500** pixel landscape graphic: 24-bit PNG without alpha or JPEG, sRGB.
Working source should keep text editable.

**Produced (2026-11-03):** [`assets/feature-graphic.png`](assets/feature-graphic.png), 1024×500 opaque
PNG, from `app/src/test/java/com/vaibhav/kharchapaani/ui/store/FeatureGraphicCapture.kt`. Its text
lives in that Kotlin, so it stays editable; regenerate as in [the screenshot plan](screenshots.md).

## Public copy

- App name: **Kharcha Paani**
- Headline: "Everyday spending. Shared plans."

## Composition

Use the app's existing charcoal background, warm amber accent and off-white type. Use the
existing Kharcha Paani mark from the approved app assets, without redrawing it. Put the app
name and two-line headline on the left, in roughly the left 55% of the canvas. On the right,
use one real crop of screenshot 3's sheet: the Weekend trip, its ₹1,800 spent and the
three-way split. Keep the amount and its label together and legible, and the sheet's full
width in view so no amount is cut at an edge; if the crop is too busy, use the app mark alone.

As built: the logo used on sign-in and the widget (`ic_kharcha_logo`) and the app's wordmark
(`BrandMark`), both shipped assets, scaled rather than redrawn; the headline in the app's own
type; the shipped group sheet drawn from the listing fixture at about half size.
No invented balance cards or decorative charts that could be mistaken for app UI.

Keep key content in the central 80% with at least 50 px horizontal and 40 px vertical inset.
Leave breathing room around the mark and headline. Preview at 512×250: the name and headline
must remain readable. Export opaque pixels; do not depend on a transparent background.

## Avoid

Follow PLAY §4/§9 and the X1 lane: no ranking or price claims, install counts, calls to download,
Play badges, device frames, third-party payment brands/logos or scannable payment codes.
Do not imply the app processes payments. Keep the excluded financial-product words out of
all visible art, including the captured UI crop. No real personal data.

## Handoff checks

- [ ] Inspect dimensions, colour mode and opacity in the exported file.
- [ ] Compare the real UI crop with the capture build; no retouched values or fabricated controls.
- [ ] Check all text and the mark at half size, and check edge crops leave the message intact.
- [ ] Owner approves the graphic alongside the screenshots before upload.
