---
trigger: glob
globs: "**/src/test/**, **/src/androidTest/**"
description: "Kharcha Paani test rules: never weaken a test."
---

- Never delete or loosen an assertion, add `@Ignore`, lengthen a sleep or narrow a check to get green. Leave it red and park the item.
- A bug's failing test goes first; let CI fail for the predicted reason. Compose tests use `StandardTestDispatcher` unless a test says otherwise; cancel and await the real ViewModel before closing Room or resetting Main.
- Only invented data in fixtures. Local Room and FileProvider failures on Windows are expected: CI decides.
