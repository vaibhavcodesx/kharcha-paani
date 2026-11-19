# Kharcha Paani owner console: design system

A private operations console for one person. It is a working tool first: scannable, fast, honest about state. It borrows the
app's own look (warm charcoal, one saffron accent, a money-statement voice) and two ideas from the DESIGN.md library: Linear's
layered near-black surfaces separated by hairlines, and Revolut's large, confident numerals. Nothing is copied from either.

Mode: Operate. Dials: variance 6, motion 4, density 6. Dark and light, both first-class; dark is the default on phones at night.

## Principles
1. **Numbers are the hero.** Counts, sizes and limits are set large in Space Grotesk with tabular figures. Words around them are quiet.
2. **State is never colour alone.** Every state has a word and an icon (Plenty left, Getting close, At the limit, Paused).
3. **The page answers "what needs me?" first.** Today ends with an attention list computed from real data, not a wall of tiles.
4. **Controls say what they will do.** Changes use Save and Cancel, never save on blur, and show the new value only after the server confirms.
5. **One accent.** Saffron marks the thing you can act on or the thing that is yours. Gold means "getting close", red means "stop or destructive", lime means "fine".
6. **Quiet motion with a reason.** Panels rise in once, bars fill once, tabs slide. Nothing loops. `prefers-reduced-motion` turns it all off.

## Tokens (see `src/styles/tokens.css`)
| Role | Dark | Light |
|---|---|---|
| canvas | `#0E0D0C` | `#FBF9F6` |
| panel (surface) | `#181615` | `#F6F2EC` |
| raised | `#22201D` | `#F3EFE8` |
| hairline | `#2E2B27` | `#E6DFD4` |
| text / muted / faint | `#F4F0E8` / `#A09A8F` / `#8D887E` | `#1F1D1A` / `#5F584F` / `#6E665B` |
| accent / accent text | `#FF7A3D` / `#FF9B6A` | `#C4501C` / `#B04716` |
| ok / warn / danger | `#C6F24C` / `#FFB547` / `#FF6F61` | `#2F7D4F` / `#8F5800` / `#B83A2E` |

Type: Outfit 600 for titles and panel headings (tracking -1 to -0.2); Plus Jakarta Sans for text; Space Grotesk for every number
(tabular). The hero numeral is 88px on desktop, 64px on phones. Sentence case everywhere; no uppercase label rows.

Shape: one scale. Panels 18px, controls 12px, chips and the dock pill-shaped, the primary action keeps the app's leaf corner
(`22px 8px 22px 8px`). Spacing is a 4px scale. Icons are Phosphor, regular weight, filled when selected; no hand-drawn icons.

## Layout
- **Desktop (1024px and up):** a left rail (brand, five destinations, a live status block, theme and sign-out) beside a main area
  up to 72rem. Today is an asymmetric grid: People (wide), AI today (tall), Space, then Needs a look. People is master and detail
  (list left, person right). Limits is a table. Release shows a version track.
- **Phone:** a slim top bar and a floating glass dock. Detail opens as a bottom sheet. Tables become stacked rows.
- Glass (blur) is used on the dock and top bar only, never on content.

## Components
Panel, row on hairline, meter (role=meter with a word and an icon), segmented control, inline-edit number (Change, Save, Cancel,
"was N" for five seconds), confirm sheet with type-to-confirm for destructive changes, skeleton rows shaped like real rows,
command palette (Ctrl or Cmd + K) for jumping and quick actions.

## Never
Eyebrow label rows, gradient text, hero-metric tile grids, nested cards, decorative dots, glass on content, neon glows, emoji as
icons, em-dashes in copy, anything in the page that cannot be explained by a sentence about what it tells the owner.
