package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.auth.GoogleSignInFailure
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

// Google's "neutral" Sign in with Google button colors; the dark variant would vanish on charcoal.
private val GoogleButtonFill = Color(0xFFF2F2F2)
private val GoogleButtonText = Color(0xFF1F1F1F)

@Composable
fun SignInScreen(
    isSigningIn: Boolean,
    failure: GoogleSignInFailure?,
    onSignInWithGoogle: () -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBg)
            .drawBehind {
                // Radius scales with the window so the glow reads the same on any density/size.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(WarmAmberPrimary.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset.Zero,
                        radius = size.maxDimension,
                    )
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Pins the intro to the top and the CTA to the bottom (thumb reach), but scrolls instead
        // of overlapping when a short screen or large font scale can't fit both.
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SignInIntro(modifier = Modifier.padding(top = 72.dp))
            SignInActions(
                isSigningIn = isSigningIn,
                failure = failure,
                onSignInWithGoogle = onSignInWithGoogle,
                onDismissFailure = onDismissFailure,
                modifier = Modifier.padding(top = 40.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SignInIntro(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
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
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Log what you spend, split it with friends, and settle up without the awkward maths.",
            style = MaterialTheme.typography.bodyLarge.copy(color = MutedWarmGray),
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
            colors = ButtonDefaults.buttonColors(
                containerColor = GoogleButtonFill,
                contentColor = GoogleButtonText,
                disabledContainerColor = GoogleButtonFill.copy(alpha = 0.7f),
                disabledContentColor = GoogleButtonText,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
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
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (isSigningIn) "Signing in…" else "Continue with Google",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = GoogleButtonText,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
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
        Spacer(Modifier.width(12.dp))
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

private fun GoogleSignInFailure.message(): String = when (this) {
    GoogleSignInFailure.NotConfigured -> "Google sign-in isn't set up in this build yet."
    GoogleSignInFailure.NoGoogleAccount -> "Add a Google account to this phone, then try again."
    GoogleSignInFailure.GoogleUnavailable ->
        "Google sign-in isn't available right now. Check that Google Play services is up to date."
    GoogleSignInFailure.Network -> "You're offline. Connect once to sign in."
    GoogleSignInFailure.Rejected -> "Sign-in didn't go through on our end. Try again in a moment."
}

@Preview
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
