package com.vaibhav.kharchapaani

import android.app.Application
import com.vaibhav.kharchapaani.diagnostics.CrashReporting

/** Starts what has to run before anything else can go wrong: crash reporting (plan T1.6). */
class KharchaPaaniApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporting.start(this)
    }
}
