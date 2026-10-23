import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.androidx.room)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

/**
 * Where a release build gets its signing key.
 *
 * Two sources, in order: a `keystore.properties` at the repo root (gitignored — for building on
 * this machine), then environment variables (for CI, where the values come from GitHub Secrets).
 * Neither is committed, and the private key itself never is.
 *
 * Null when any part is missing or the keystore file is not where it says, which leaves the
 * release build unsigned instead of failing.
 */
data class ReleaseSigning(
  val storePath: String,
  val storePassword: String,
  val keyAlias: String,
  val keyPassword: String,
)

val releaseSigning: ReleaseSigning? = run {
  val properties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
  }
  fun value(propertyName: String, envName: String): String? =
    properties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
      ?: System.getenv(envName)?.takeIf { it.isNotBlank() }

  val storePath = value("storeFile", "KEYSTORE_PATH") ?: return@run null
  val storePassword = value("storePassword", "STORE_PASSWORD") ?: return@run null
  val keyAlias = value("keyAlias", "KEY_ALIAS") ?: "upload"
  val keyPassword = value("keyPassword", "KEY_PASSWORD") ?: return@run null

  // A path that points at nothing is the same as no credentials at all. The resolved path goes in
  // the message because the usual cause is invisible otherwise: java.util.Properties treats a
  // backslash as an escape character, so a Windows path written with backslashes in
  // keystore.properties arrives here with them stripped. Use forward slashes.
  val resolved = rootProject.file(storePath)
  if (!resolved.exists()) {
    logger.warn(
      "Release signing: no keystore at '${resolved.absolutePath}' (read as '$storePath'), so the " +
        "release APK will be UNSIGNED and will not install. On Windows, write the path in " +
        "keystore.properties with forward slashes."
    )
    return@run null
  }
  ReleaseSigning(resolved.absolutePath, storePassword, keyAlias, keyPassword)
}

/**
 * `-PomitAiKey` builds an APK with no Gemini key in it at all.
 *
 * The key is compiled into BuildConfig and is extractable from any APK that carries it, so this
 * is the build to hand to someone whose use of it you cannot vouch for. `GeminiRepository`
 * already treats a blank key as "no key", and `MainViewModel.aiAvailable` hides the whole AI
 * section when that is the case, so the app degrades to everything-but-the-AI rather than
 * showing buttons that cannot work.
 */
val omitAiKey = providers.gradleProperty("omitAiKey").isPresent

android {
  namespace = "com.vaibhav.kharchapaani"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.vaibhav.kharchapaani"
    minSdk = 24
    targetSdk = 36
    // Bump both for every release you publish. versionCode is what Android compares to decide
    // "is this an update?" — it must strictly increase or the new APK refuses to install over
    // the old one. versionName is the human label and should match the git tag: tag v1.1.0,
    // versionName "1.1.0".
    versionCode = 5
    versionName = "1.4.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Replaces the field the Secrets plugin would have emitted, so the APK carries no key.
    if (omitAiKey) buildConfigField("String", "GEMINI_API_KEY", "\"\"")
  }

  signingConfigs {
    // Only created when credentials are actually available. Declaring it unconditionally meant
    // `assembleRelease` failed for anyone without the private key — including CI, and including
    // a fresh clone — because storePassword came back null from an unset environment variable.
    if (releaseSigning != null) {
      create("release") {
        storeFile = file(releaseSigning.storePath)
        storePassword = releaseSigning.storePassword
        keyAlias = releaseSigning.keyAlias
        keyPassword = releaseSigning.keyPassword
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Null leaves the APK unsigned rather than failing the build. An unsigned APK will not
      // install on a phone, which is the right outcome: a release build with no key is for
      // checking that the release variant compiles, not for handing to anyone.
      signingConfig = if (releaseSigning != null) signingConfigs.getByName("release") else null
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  // MigrationTestHelper builds old schema versions from these JSON files. Robolectric only sees
  // the app's merged debug assets (not the test source set's), so they go in debug; release
  // APKs don't carry them.
  sourceSets { getByName("debug") { assets.directories.add("$projectDir/schemas") } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Committed JSON snapshot per Room version: the reference every migration is written and tested
// against. A version bump without a matching snapshot and Migration is a review red flag.
room { schemaDirectory("$projectDir/schemas") }

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  // Ignored here so the plugin does not emit the field; the empty one below takes its place.
  if (omitAiKey) ignoreList.add("GEMINI_API_KEY")
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  // QR codes for the UPI payment request (pure Java, no Android dependencies).
  implementation(libs.zxing.core)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)

  // Google Sign-In via Credential Manager, exchanged for a Supabase session (not Firebase Auth).
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.auth)
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.realtime)
  implementation(libs.haze)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.multiplatform.settings)
  implementation(libs.androidx.work.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
