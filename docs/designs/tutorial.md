<!-- status: approved by the Lead 2026-11-19 (owner answer G-22) | owner: lead -->
# Skippable tutorial (D-05)

Owner request: a tutorial that explains the main features, skippable. Issue #314. One Builder PR; yellow (app code, no
server, no schema).

## What the person sees

A short full-screen pager of **five cards**, shown once, after the first launch (guest) or the first sign-in. **Skip**
is on screen on every card and ends it at once; so does the system back button. Swipe or Next moves on; the last card
says "Start". Re-open any time from Settings ("How Kharcha Paani works").

| # | Title | One line |
|---|---|---|
| 1 | Add an expense in a second | Type or say it ("chai 40 gpay"). AI can read bills and statements; you check every entry before saving. |
| 2 | Split with friends | Add friends (they do not need the app), split equally, by amount, or by items from a bill photo. |
| 3 | Groups and trips | Make a group, invite people with a link or a QR code, see who owes whom. |
| 4 | Settle up | Pay a friend through your own UPI app, then record it. The app never holds or moves money. |
| 5 | You are in control | Everything works without an account; turn AI off any time in Settings; delete your data whenever you want. |

Wording says "AI", never a vendor. No mention of the logo or the launcher mark (it is not final).

## How it looks

Plain cards, one icon from the app's existing icon set per card (no illustrations to make, no logo). Page dots show
position and a "2 of 5" label for screen readers. Respect the system's reduced-motion setting (no swipe animation).
Text scales to large font sizes without cutting off. Dark theme only, as the app.

## Mechanics

- A single `tutorialSeen` flag in the existing settings store (the app already persists such flags); phone-only,
  included in Backup if the settings already are. No Room change.
- Shown when `!tutorialSeen` and the main screen is first composed with a signed-in or guest ledger; set to true on
  Skip or Start (not on process death mid-way, so it can show again once).
- Never shown over a join-link flow: if the app was opened from an invite link or share intent, the tutorial waits
  until that flow is done.

## Tests

State holder: first run shows it; Skip sets the flag and hides it; second launch does not show it; Settings entry
re-opens it without clearing the flag. A Compose test that Skip is present on every card and that the last card says
Start. Strings are a hot file (`hot:strings`); add them in one block.

## Not now

Interactive coach marks over the real UI, videos, per-feature tips after the first use, and any server component.
