package com.vaibhav.kharchapaani.ui.shell

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Draws the app under the status and navigation bars, with light icons whatever the phone's own
 * theme: the app is always dark. The automatic style followed the phone, so in light mode (most
 * phones) the clock and battery were dark on near-black, and 3-button navigation got a white strip.
 */
fun ComponentActivity.applySystemBars() = enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
)
