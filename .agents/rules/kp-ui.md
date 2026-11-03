---
trigger: glob
globs: "**/ui/**/*.kt, **/res/values/*.xml, **/widget/*.kt, **/MainActivity.kt"
description: "Kharcha Paani UI limits: no design decisions, existing tokens and components only, friendly copy."
---

Read `docs/antigravity/HANDBOOK.md` section 4 before changing UI.

- You don't make visual decisions: no new screens, colours, sizes, radii, fonts, icons, animation or layouts. Copy the nearest existing screen. If that isn't enough, park the item.
- Use `Spacing`, `KpShapes`, `MoneyText`, theme colours and `ui/components/`. Never a raw `Color(...)`, `.dp` or `.sp` you chose yourself.
- Money never ellipsizes. Text goes in `strings.xml`. Interactive icons get a spoken label.
- Nothing one person sends or shows another may say "you owe", "overdue" or "pay now".
- Kotlin initialisers run in declaration order; UI changes need a test that builds the real `MainViewModel` or app shell.
