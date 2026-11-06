# R8 rules for the release build. Most libraries here ship their own (kotlinx.serialization, Room,
# WorkManager, OkHttp, Ktor, supabase-kt, Play Core, Firebase); these are the ones they don't.

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

# kotlinx.serialization: the plugin generates each @Serializable class's serializer and the library
# ships rules for them. Kept explicitly too, because a backup or synced row that can't be decoded
# after an update would be data loss (Antigravity's D4 report on #71). JSON names come from the
# generated descriptors, so renaming a property can't change the file format.
-keepclassmembers @kotlinx.serialization.Serializable class com.vaibhav.kharchapaani.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.vaibhav.kharchapaani.** {
    kotlinx.serialization.KSerializer serializer(...);
}
