package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.platform.LocalContext
import com.vaibhav.kharchapaani.util.LegalLinks
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.auth.GoogleSignInFailure
import com.vaibhav.kharchapaani.ui.components.pressBounce
import com.vaibhav.kharchapaani.ui.theme.BrandGradient
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

// Google's "neutral" Sign in with Google button colors; the dark variant would vanish on charcoal.
private val GoogleButtonFill = Color(0xFFF2F2F2)
private val GoogleButtonText = Color(0xFF1F1F1F)

/**
 * Below this height the preview of the app would push the sign-in button off the first screen, so
 * it's left out; the sentence under the title already says the same thing.
 */
private val PreviewMinHeight = 720.dp

/** Past this the text alone fills the screen, and the preview would only add scrolling. */
private const val PreviewMaxFontScale = 1.3f

/**
 * The first thing anyone sees, in the app's own language rather than a one-off style:
 * - the brand gradient on the wordmark, which is this screen's hero the way the month's spend is
 *   Home's (BrandGradient is reserved for heroes and the primary action);
 * - the amber/lime glow the screen always had in its corner, now drifting slowly (SignInBackdrop);
 * - a preview of two real-looking activity rows, split then settled;
 * - the action on a glass pane at thumb reach, like the dock and sheet chrome.
 * Everything arrives once, staggered, on the app's emphasized decelerate curve; nothing moves with
 * system animations turned off.
 */
@Composable
fun SignInScreen(
    isSigningIn: Boolean,
    failure: GoogleSignInFailure?,
    onSignInWithGoogle: () -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val progress = rememberEntranceProgress(reducedMotion)

    Box(modifier.fillMaxSize().background(CharcoalBg)) {
        // Edge to edge, under the status and navigation bars; the content below is inset.
        SignInBackdrop(reducedMotion = reducedMotion, modifier = Modifier.matchParentSize())

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val showPreview = maxHeight >= PreviewMinHeight && LocalDensity.current.fontScale <= PreviewMaxFontScale
            // Pins the intro to the top and the action to the bottom (thumb reach), but scrolls
            // instead of overlapping when a short screen or large font scale can't fit both.
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = Spacing.xl),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                SignInIntro(progress = progress, modifier = Modifier.padding(top = 72.dp))
                if (showPreview) {
                    LedgerPreview(progress = progress, modifier = Modifier.padding(top = Spacing.xxl))
                }
                SignInActions(
                    isSigningIn = isSigningIn,
                    failure = failure,
                    onSignInWithGoogle = onSignInWithGoogle,
                    onDismissFailure = onDismissFailure,
                    modifier = Modifier
                        .padding(top = 40.dp, bottom = Spacing.l)
                        .entrance(progress, EntranceDelay.ACTIONS),
                )
            }
        }
    }
}

@Composable
private fun SignInIntro(progress: () -> Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .entrance(progress, EntranceDelay.LOGO)
                .size(64.dp)
                .clip(KpShapes.medium)
                .background(CharcoalElevated)
                .border(1.dp, WarmAmberPrimary.copy(alpha = 0.4f), KpShapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_kharcha_logo),
                contentDescription = null,
                modifier = Modifier.size(50.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Kharcha Paani",
            style = BrandGradient.text(MaterialTheme.typography.displayLarge),
            modifier = Modifier
                .entrance(progress, EntranceDelay.TITLE)
                .semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.m))
        Text(
            text = "Log what you spend, split it with friends, and settle up without the awkward maths.",
            style = MaterialTheme.typography.bodyLarge.copy(color = MutedWarmGray),
            modifier = Modifier.entrance(progress, EntranceDelay.LINE),
        )
    }
}

@Composable
private fun SignInActions(
    isSigningIn: Boolean,
    failure: GoogleSignInFailure?,
    onSignInWithGoogle: () -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .signInGlass(KpShapes.large)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        if (failure != null) {
            SignInFailureNotice(failure = failure, onDismiss = onDismissFailure)
        }
        Text(
            text = "Sign in once to back up your ledger and share live balances with friends. " +
                "After that it keeps working offline.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onSignInWithGoogle,
            enabled = !isSigningIn,
            shape = KpShapes.medium,
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = GoogleButtonFill,
                contentColor = GoogleButtonText,
                disabledContainerColor = GoogleButtonFill.copy(alpha = 0.7f),
                disabledContentColor = GoogleButtonText,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                // The same press spring and haptic as every other primary control in the app.
                .pressBounce(interactionSource, scaleDown = 0.97f)
                .testTag("sign_in_google_button"),
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(
                    color = GoogleButtonText,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_google_g),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Text(
                text = if (isSigningIn) "Signing in…" else "Continue with Google",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = GoogleButtonText,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        AgreementLine()
    }
}

/**
 * What continuing agrees to, with the pages themselves one tap away (plan T1.2), and the 18+ line:
 * Gemini's terms are 18+, and India's DPDP Act needs a parent's consent for anyone younger, which
 * the app doesn't collect.
 */
@Composable
private fun AgreementLine() {
    val context = LocalContext.current
    val linkStyle = TextLinkStyles(SpanStyle(color = OffWhiteText, textDecoration = TextDecoration.Underline))
    fun link(url: String) = LinkAnnotation.Clickable(tag = url, styles = linkStyle) { LegalLinks.open(context, url) }
    Text(
        text = buildAnnotatedString {
            append("By continuing you agree to the ")
            withLink(link(LegalLinks.TERMS)) { append("Terms") }
            append(" and the ")
            withLink(link(LegalLinks.PRIVACY_POLICY)) { append("Privacy policy") }
            append(", and confirm you're 18 or older.")
        },
        style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray),
        modifier = Modifier.testTag("sign_in_agreement"),
    )
}

@Composable
private fun SignInFailureNotice(
    failure: GoogleSignInFailure,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KpShapes.medium)
            .background(CharcoalCard)
            .border(1.dp, CharcoalBorder, KpShapes.medium)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("sign_in_failure"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (failure == GoogleSignInFailure.Network) Icons.Outlined.CloudOff else Icons.Outlined.Info,
            contentDescription = null,
            tint = WarmAmberLight,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.m))
        Text(
            text = failure.message(),
            style = MaterialTheme.typography.bodyMedium.copy(color = OffWhiteText),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = MutedWarmGray,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal fun GoogleSignInFailure.message(): String = when (this) {
    GoogleSignInFailure.NotConfigured -> "Google sign-in isn't set up in this build yet."
    GoogleSignInFailure.NoGoogleAccount -> "Add a Google account to this phone, then try again."
    GoogleSignInFailure.GoogleUnavailable ->
        "Google sign-in isn't available right now. Check that Google Play services is up to date."
    GoogleSignInFailure.Network -> "You're offline. Connect once to sign in."
    GoogleSignInFailure.Rejected -> "Sign-in didn't go through on our end. Try again in a moment."
    GoogleSignInFailure.PickerBlocked ->
        "Google didn't open the account picker. It holds it back for a few minutes after it's " +
            "been closed several times — wait a moment and try again."
    GoogleSignInFailure.AppNotAccepted ->
        "Google didn't accept this copy of the app for sign-in. That's a setup problem on our side, " +
            "not your account. Try again later, or ask whoever sent you the app."
    GoogleSignInFailure.Dismissed -> "No account picked. Tap Continue with Google when you're ready."
}

@Preview(heightDp = 891, widthDp = 411)
@Composable
private fun SignInScreenPreview() {
    SplitFlowTheme {
        SignInScreen(isSigningIn = false, failure = null, onSignInWithGoogle = {}, onDismissFailure = {})
    }
}

@Preview
@Composable
private fun SignInScreenFailurePreview() {
    SplitFlowTheme {
        SignInScreen(
            isSigningIn = false,
            failure = GoogleSignInFailure.Network,
            onSignInWithGoogle = {},
            onDismissFailure = {},
        )
    }
}
