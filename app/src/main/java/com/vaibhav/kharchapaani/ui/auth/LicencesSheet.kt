package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.components.KpModalSheet
import com.vaibhav.kharchapaani.ui.components.SheetScaffold
import com.vaibhav.kharchapaani.ui.components.SheetTitle
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.util.LegalLinks

/** One piece of open-source software the app ships, with its licence and where to read it. */
data class OpenSourcePart(val name: String, val licence: String, val url: String)

/**
 * What the app is built on. Kept by hand: it changes only when a dependency is added, and a list
 * someone can read beats a generated dump of every transitive artifact.
 */
val OpenSourceParts = listOf(
    OpenSourcePart("Android Jetpack (AndroidX): Compose, Room, WorkManager, DataStore and more", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    OpenSourcePart("Kotlin, kotlinx.coroutines and kotlinx.serialization", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    OpenSourcePart("supabase-kt", "MIT License", "https://github.com/supabase-community/supabase-kt/blob/master/LICENSE"),
    OpenSourcePart("Ktor", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    OpenSourcePart("OkHttp", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    OpenSourcePart("Coil", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    OpenSourcePart("ZXing", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    OpenSourcePart("Haze", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    OpenSourcePart("Multiplatform Settings", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    OpenSourcePart("Java library desugaring (desugar_jdk_libs)", "GPL v2 with the Classpath Exception", "https://openjdk.org/legal/gplv2+ce.html"),
    OpenSourcePart("Outfit, Plus Jakarta Sans and Space Grotesk typefaces", "SIL Open Font License 1.1", "https://openfontlicense.org"),
)

@Composable
fun LicencesSheet(isOpen: Boolean, onDismiss: () -> Unit) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) { LicencesContent() }
}

@Composable
fun LicencesContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SheetScaffold(modifier = modifier) {
        SheetTitle(title = "Open-source licences", supporting = "Kharcha Paani is built on these, each used under its licence. Tap one to read it.")
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            OpenSourceParts.forEach { part ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Read the ${part.licence}") { LegalLinks.open(context, part.url) }
                        .padding(vertical = 4.dp)
                        .testTag("licence_row"),
                ) {
                    Text(part.name, style = MaterialTheme.typography.titleSmall, color = OffWhiteText)
                    Text(part.licence, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
