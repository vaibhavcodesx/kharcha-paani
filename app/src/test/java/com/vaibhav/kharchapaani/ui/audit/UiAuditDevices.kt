package com.vaibhav.kharchapaani.ui.audit

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A Pixel-width phone: 412×915 dp at 420 dpi, the most common Android size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class UiAuditPixel : UiAuditCapture("pixel")

/** A small phone: 360×640 dp, where crowded rows show first. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class UiAuditSmall : UiAuditCapture("small")

/** The largest system font (200%) on a Pixel-width phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi", fontScale = 2.0f)
class UiAuditLargeFont : UiAuditCapture("font-2x")

/** A brand-new account: nothing logged yet, every empty state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class UiAuditNewUser : UiAuditCapture("new-user", seeded = false)
