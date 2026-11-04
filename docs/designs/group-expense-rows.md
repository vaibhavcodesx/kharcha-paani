# Group expense rows: dots, not names

Status: built. The "added by" word was dropped from the row in the end (the second line has no room for it at 360 dp); it stays in the edit sheet. Owner asked for it after v1.6.0 (screenshot of a group's Activity list).

## The problem

A group expense row says who paid and who shared it three times over, in words:

```
Ajay S Cafe                         ₹270
2 Nov · You paid                yours ₹40
Paid by  ● You
Split with  ● You  ● Aaru  ● Pruthva
● Shardool
```

That is four to five lines, about 120 dp per row, so a group with 70 expenses is a long scroll of
the same words. The coloured dots were introduced to say who without the names (every person has
one colour that is the same everywhere: avatar, Activity row, group donut); the group sheet kept
both. The names win and the dots decorate.

## The row

Two lines, about 56 dp.

```
[icon]  Ajay S Cafe                    ₹270
        2 Nov   ◉ │ ● ● ● +1       yours ₹40
```

- Line 1: merchant, amount.
- Line 2: date, then **one ring dot** for who paid, a hairline gap, then **filled dots** for who it
  was split with (the same dots Activity rows use, `SharerFaces` style: 8 dp, up to 4, then `+N`).
  The right column keeps "yours ₹40".
- The ring is the payer's own colour (the dot inside it is the same colour, so it is the person's dot wherever it appears), filled dots are the sharers'. A ring is "paid", a dot is
  "shares", and one line under the group's Activity heading says so once: `◉ paid · ● split between`.
- Names are not on the row. They are one tap away (the edit sheet) and in the accessibility label,
  which reads "Paid by You. Split with You, Aaru, Pruthva and 1 more." Colour never carries meaning
  alone.
- "Added by Mayank" (adder is not the payer) was the only thing the old text line said that the dots
  cannot. It stays as a small trailing word on line 2 only in that case: `2 Nov · by Mayank`.

## What stays

- Test tags `group_expense_<id>_payer_<id>` and `group_expense_<id>_share_<id>` stay on the dots, so
  the colour tests keep their meaning.
- Tap to edit where it is allowed, nothing new.
- Settlements in the group keep their current row; they are already two lines.

## Not doing

Swipe actions, grouping by day, or collapsing: separate asks.
