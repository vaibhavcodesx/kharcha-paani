package com.vaibhav.kharchapaani.data.remote

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import okhttp3.Interceptor
import java.security.MessageDigest

/**
 * Who this app is, in the form Google's API-key restrictions expect.
 *
 * A Gemini key compiled into an APK can be pulled out of it by anyone holding the file. The
 * mitigation Google offers is an *application restriction*: the key is bound to a package name and
 * a signing certificate, so an extracted key is refused everywhere except this app.
 *
 * The part that is easy to miss, and that breaks the app if you miss it: the binding is not
 * automatic for plain REST calls. The Maps SDK sends these headers for you; OkHttp does not. Turn
 * the restriction on without sending them and every Gemini request starts coming back 403 — the
 * feature breaks the moment the protection is enabled. Hence [restrictionInterceptor].
 *
 * The fingerprint is read from the installed package at runtime rather than baked in at build
 * time, so a debug build signed with `debug.keystore` and a release build signed with the upload
 * key each report their own, with nothing to configure.
 */
data class AppIdentity(val packageName: String, val certificateSha1: String) {

    companion object {
        private const val TAG = "AppIdentity"

        /** Null when the signature can't be read, in which case no headers are sent. */
        fun of(context: Context): AppIdentity? = try {
            val packageName = context.packageName
            signingCertificate(context, packageName)?.let { AppIdentity(packageName, it) }
        } catch (e: Exception) {
            // Never fatal: an unrestricted key works without these headers, so the worst case of
            // failing here is that a restricted key stops working — visibly, not silently.
            Log.w(TAG, "Couldn't read this app's signing certificate", e)
            null
        }

        /** Upper-case hex SHA-1 with no separators, which is the format the header wants. */
        @Suppress("DEPRECATION")
        private fun signingCertificate(context: Context, packageName: String): String? {
            val manager = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = manager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
            } else {
                manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val first = signatures?.firstOrNull() ?: return null
            val digest = MessageDigest.getInstance("SHA-1").digest(first.toByteArray())
            return digest.joinToString("") { "%02X".format(it) }
        }

        /**
         * Adds `X-Android-Package` and `X-Android-Cert` to every request.
         *
         * Harmless when the key is unrestricted — the headers are simply ignored — so this is on
         * unconditionally rather than behind a flag that could be out of step with the key's
         * actual configuration in Google Cloud.
         */
        fun restrictionInterceptor(identity: AppIdentity?): Interceptor = Interceptor { chain ->
            if (identity == null) return@Interceptor chain.proceed(chain.request())
            val request = chain.request().newBuilder()
                .header("X-Android-Package", identity.packageName)
                .header("X-Android-Cert", identity.certificateSha1)
                .build()
            chain.proceed(request)
        }
    }
}

/** For logs and for reading off the fingerprint to paste into Google Cloud Console. */
fun AppIdentity.describe(): String = "$packageName / $certificateSha1"
