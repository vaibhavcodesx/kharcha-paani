<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Permissions: what the app asks for, why, and where it says so (P-03)

Checked against `app/src/main/AndroidManifest.xml` on 2026-11-19. Play's review and the Data safety form
(`docs/legal/play-console-answers.md`) must say the same as this page.

| Permission | Why | Asked when | What the person sees first | If declined |
|---|---|---|---|---|
| `INTERNET` | Sign-in, sync, AI reads, shared groups | Never (normal permission) | n/a | App works offline (local mode) |
| `POST_NOTIFICATIONS` | Settlement and daily reminders the person turned on | When a reminder is first turned on | The reminder setting itself says what it will send | Reminders are not shown; nothing else changes |
| `RECORD_AUDIO` | Voice entry: the person says an entry | First tap of the voice bar | One-line sheet: "To hear your entry, Kharcha Paani needs the microphone, only while this bar is open." (Continue / Not now) | A message says they can type; typing is unaffected |
| `CAMERA` | (1) Scan a group invite QR code; (2) take a photo of a bill | First tap of Scan or Bill photo | One-line sheet with the reason (QR: scanning an invite; Bill photo: photographing the bill) | A message; they can paste an invite link or choose a bill photo from the gallery |

No location, contacts, SMS, call log, calendar, storage, exact-alarm or accessibility permissions. Photos and
statements are chosen with the system pickers, which need no permission.

## What happens to what the permissions see

- **Microphone:** audio is recorded in memory for up to 30 seconds while the bar is open, sent to the AI service once
  (only after the one-time AI notice is accepted, only online), and never stored on the phone or the server.
- **Camera, QR scan:** frames are decoded on the phone and discarded; only the invite code from a valid scan is used.
  Nothing is stored or sent from the preview.
- **Camera, bill photo:** the system camera takes the photo; it is stored on the phone, and a re-encoded copy
  (location data removed, at most 200 KB) is backed up when signed in (`docs/legal/play-console-answers.md`).

## Why the app declares CAMERA at all

Android refuses to open the system camera for a photo when an app declares the CAMERA permission but does not hold it,
so the Bill photo flow asks for it first (with the sheet above). Declaring it is required by the QR scanner.

## Keep this page true

Any PR that adds or changes a permission updates this page, `play-console-answers.md` and `site/privacy.html` in the
same PR (`docs/agents/PLAYBOOK.md` section 10).
