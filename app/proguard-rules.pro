# R8 rules for the release build. Most libraries here ship their own (kotlinx.serialization, Room,
# WorkManager, OkHttp, Ktor, supabase-kt); these are the ones they don't.

# Stack traces from a release build: keep line numbers, and name every file "SourceFile" so the
# real file names aren't in the APK. mapping.txt (attached to each GitHub Release) maps them back.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Credential Manager finds its Play services provider by reflection; without this, Google sign-in
# fails on release builds only. From developer.android.com/identity/sign-in/credential-manager.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
