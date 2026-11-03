---
trigger: glob
globs: "**/domain/*.kt, **/data/repository/*.kt, **/ui/viewmodel/*.kt, **/data/db/**, **/data/sync/**"
description: "Kharcha Paani money and data invariants."
---

- `-1L` is me. Positive balance: they owe me. Only confirmed settlements move a balance.
- Use `roundPaisa`, `Money.parseInput`, `Money.format`. Shares add up to the amount to the paisa.
- Stored category, payment and type ids never change. `data/db` and `data/sync` aren't yours: leave them to Claude.
- Don't re-architect the ledger, debt simplification, AI pipeline or groups model.
- Prove it with a real Room database and a real balance, not a hand-built object.
