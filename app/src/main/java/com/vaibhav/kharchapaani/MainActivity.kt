package com.vaibhav.kharchapaani

import java.util.UUID
import java.io.File
import com.vaibhav.kharchapaani.ui.components.BillPhotoSource
import androidx.core.content.FileProvider
import androidx.activity.result.PickVisualMediaRequest
import android.content.ActivityNotFoundException
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vaibhav.kharchapaani.util.NotificationHelper
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.vaibhav.kharchapaani.ui.components.ExportSheet
import com.vaibhav.kharchapaani.ui.components.SettlementAnswerSheet
import com.vaibhav.kharchapaani.ui.components.UpiSheet
import com.vaibhav.kharchapaani.ui.components.CategoriesSheet
import com.vaibhav.kharchapaani.ui.components.SubscriptionsSheet
import com.vaibhav.kharchapaani.domain.Categories
import com.vaibhav.kharchapaani.ui.shell.KpSnackbarHost
import com.vaibhav.kharchapaani.ui.shell.snackbarClearance
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.vaibhav.kharchapaani.ui.shell.AppTab
import com.vaibhav.kharchapaani.ui.shell.KpDock
import com.vaibhav.kharchapaani.ui.shell.tabSwipe
import com.vaibhav.kharchapaani.ui.theme.KpMotion
import com.vaibhav.kharchapaani.ui.theme.LocalHazeState
import com.vaibhav.kharchapaani.ui.theme.LocalMyColour
import com.vaibhav.kharchapaani.ui.theme.MyColour
import com.vaibhav.kharchapaani.ui.components.hueColour
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.repeatOnLifecycle
import com.vaibhav.kharchapaani.ui.session.AccountSession
import com.vaibhav.kharchapaani.ui.session.AccountSessionHolder
import com.vaibhav.kharchapaani.ui.session.AccountSessionState
import com.vaibhav.kharchapaani.ui.session.LedgerOpenFailedScreen
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.ui.auth.AccountSheet
import com.vaibhav.kharchapaani.ui.auth.ProfileState
import com.vaibhav.kharchapaani.ui.auth.ColourChoice
import com.vaibhav.kharchapaani.domain.Hues
import com.vaibhav.kharchapaani.ui.auth.AuthUiState
import com.vaibhav.kharchapaani.ui.auth.AuthViewModel
import com.vaibhav.kharchapaani.ui.auth.SignInScreen
import com.vaibhav.kharchapaani.ui.components.AddFriendSheet
import com.vaibhav.kharchapaani.ui.components.ConfirmBottomSheet
import com.vaibhav.kharchapaani.ui.components.CreateGroupSheet
import com.vaibhav.kharchapaani.ui.components.GroupDetailSheet
import com.vaibhav.kharchapaani.data.remote.SupabaseClientProvider
import com.vaibhav.kharchapaani.data.sync.SupabaseLedgerChanges
import com.vaibhav.kharchapaani.data.sync.SyncDoorbell
import com.vaibhav.kharchapaani.domain.InviteLink
import com.vaibhav.kharchapaani.ui.groups.JoinGroupSheet
import com.vaibhav.kharchapaani.ui.groups.ShareSplitPrompt
import com.vaibhav.kharchapaani.ui.groups.SharedGroupPanel
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsController
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsViewModel
import com.vaibhav.kharchapaani.ui.components.IouSheet
import com.vaibhav.kharchapaani.ui.components.PersonLedgerSheet
import com.vaibhav.kharchapaani.ui.components.SettleUpSheet
import com.vaibhav.kharchapaani.ui.components.SimplifyDebtsSheet
import com.vaibhav.kharchapaani.ui.components.StatementImportDialog
import com.vaibhav.kharchapaani.ui.components.StatementParsingLoadingDialog
import com.vaibhav.kharchapaani.ui.screens.HomeScreen
import com.vaibhav.kharchapaani.ui.screens.InsightsScreen
import com.vaibhav.kharchapaani.ui.screens.SplitsScreen
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import com.vaibhav.kharchapaani.widget.SplitFlowWidgetProvider

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val sessionHolder: AccountSessionHolder by viewModels()

    // Share/widget intents wait here until the signed-in account's ledger is open.
    private var queuedIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // On recreation (e.g. rotation) the launch intent was already handled; re-handling it
        // would parse the same shared screenshot twice.
        if (savedInstanceState == null) queuedIntent = intent

        setContent {
            SplitFlowTheme {
                val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
                val sessionState by sessionHolder.state.collectAsStateWithLifecycle()

                val authState = authUiState.authState
                LaunchedEffect(authState) {
                    when (authState) {
                        AuthState.SignedOut -> sessionHolder.onAccountChanged(null)
                        is AuthState.SignedIn -> authState.userId?.let(sessionHolder::onAccountChanged)
                        AuthState.Initializing -> Unit
                    }
                }

                val gate = when (authState) {
                    AuthState.Initializing -> AuthGate.Loading
                    AuthState.SignedOut -> AuthGate.SignIn
                    is AuthState.SignedIn -> when (sessionState) {
                        is AccountSessionState.Ready -> AuthGate.App
                        is AccountSessionState.Failed -> AuthGate.LedgerFailed
                        AccountSessionState.None, AccountSessionState.Opening -> AuthGate.Loading
                    }
                }
                Crossfade(targetState = gate, label = "AuthGate") { target ->
                    when (target) {
                        // Stored sessions and ledger files open in milliseconds; a blank frame
                        // avoids flashing the sign-in screen at someone already signed in.
                        AuthGate.Loading -> Box(Modifier.fillMaxSize().background(CharcoalBg))
                        AuthGate.SignIn -> SignInScreen(
                            isSigningIn = authUiState.isSigningIn,
                            failure = authUiState.signInFailure,
                            onSignInWithGoogle = { authViewModel.signInWithGoogle(this@MainActivity) },
                            onDismissFailure = authViewModel::dismissSignInFailure,
                        )
                        AuthGate.LedgerFailed -> LedgerOpenFailedScreen(
                            onRetry = sessionHolder::retryOpening,
                            onSignOut = { authViewModel.signOut(this@MainActivity) },
                        )
                        AuthGate.App -> {
                            val session = (sessionState as? AccountSessionState.Ready)?.session
                            if (session != null) {
                                AccountScopedApp(
                                    session = session,
                                    authUiState = authUiState,
                                    queuedIntent = queuedIntent,
                                    onIntentHandled = { queuedIntent = null },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        queuedIntent = intent
    }

    @Composable
    private fun AccountScopedApp(
        session: AccountSession,
        authUiState: AuthUiState,
        queuedIntent: Intent?,
        onIntentHandled: () -> Unit,
    ) {
        // key(): saveable UI state (open sheets, tabs) must not carry over to another account.
        key(session.userId) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides session) {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.factory(application, session.database, session.userId),
                )
                val sharedGroups: SharedGroupsViewModel = viewModel(
                    factory = SharedGroupsViewModel.factory(application, session.database, session.userId),
                )
                // App start and every return to the foreground: push anything queued, pull the rest.
                LifecycleStartEffect(viewModel) {
                    viewModel.requestSync()
                    onStopOrDispose { }
                }
                // While in the foreground, changes other people or devices make ring for a sync.
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(session.userId, lifecycle) {
                    val changes = SupabaseLedgerChanges(SupabaseClientProvider.get(application), session.userId)
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        SyncDoorbell(changes = changes::changes, ring = viewModel::requestSync).run()
                    }
                }
                LaunchedEffect(viewModel, queuedIntent) {
                    if (queuedIntent != null) {
                        routeIntent(queuedIntent, viewModel, sharedGroups.controller)
                        onIntentHandled()
                    }
                }
                // Your own colour, everywhere the app draws "you" (design item 11). Provided around
                // the whole app: the screens, the sheets and the dock all read it.
                val myColour = authUiState.hue?.let { MyColour(hueColour(it), Hues.hex(it)) } ?: LocalMyColour.current
                CompositionLocalProvider(LocalMyColour provides myColour) {
                SplitFlowApp(
                    viewModel = viewModel,
                    sharedGroups = sharedGroups.controller,
                    selfProfileId = session.userId,
                    authUiState = authUiState,
                    onRetryProfile = authViewModel::retryProfileLoad,
                    onSaveProfile = authViewModel::saveProfile,
                    onSignOut = { authViewModel.signOut(this@MainActivity) },
                    onSaveHue = authViewModel::saveHue,
                )
                }
            }
        }
    }

    private fun routeIntent(intent: Intent, viewModel: MainViewModel, sharedGroups: SharedGroupsController) {
        // 0. Group invite link. Nothing is joined until the person confirms the preview.
        if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == InviteLink.SCHEME) {
            viewModel.setTab(1)
            sharedGroups.openJoin(intent.dataString.orEmpty())
            return
        }
        // 1. Shared Image (e.g. screenshot shared from GPay, Paytm, Gallery)
        if (Intent.ACTION_SEND == intent.action && intent.type?.startsWith("image/") == true) {
            val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
            }
            if (imageUri != null) {
                viewModel.handleSharedImageUri(this, imageUri)
            }
        }
        // 2. Shared PDF Bank / UPI Statement
        else if (Intent.ACTION_SEND == intent.action && intent.type == "application/pdf") {
            val pdfUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
            }
            if (pdfUri != null) {
                viewModel.handleSharedPdfUri(this, pdfUri)
            }
        }
        // 3. Shared Text (e.g. SMS, WhatsApp payment text, or transaction message)
        else if (Intent.ACTION_SEND == intent.action && (intent.type?.startsWith("text/") == true || intent.type == null)) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.parseInputText(sharedText)
            }
        }
        // 4. Widget Actions
        else if (intent.action == SplitFlowWidgetProvider.ACTION_QUICK_ADD) {
            viewModel.setTab(0)
            viewModel.openManualAdd("expense")
        } else if (intent.action == SplitFlowWidgetProvider.ACTION_VOICE_LOG) {
            viewModel.setTab(0)
            viewModel.requestVoiceCapture()
        } else if (intent.action == NotificationHelper.ACTION_ANSWER_SETTLEMENT) {
            viewModel.setTab(1)
            intent.getLongExtra(NotificationHelper.EXTRA_SETTLEMENT_ID, -1L).takeIf { it > 0 }?.let(viewModel::openSettlementAnswer)
        } else if (intent.action == SplitFlowWidgetProvider.ACTION_VIEW_SPLITS) {
            viewModel.setTab(1)
        }
    }
}

private enum class AuthGate { Loading, SignIn, LedgerFailed, App }

@Composable
fun SplitFlowApp(
    viewModel: MainViewModel,
    sharedGroups: SharedGroupsController,
    selfProfileId: String,
    authUiState: AuthUiState,
    onRetryProfile: () -> Unit,
    onSaveProfile: (name: String, emoji: String?) -> Unit,
    onSignOut: () -> Unit,
    onSaveHue: (hue: Int, automatic: Boolean) -> Unit = { _, _ -> },
) {
    var isAccountSheetOpen by rememberSaveable { mutableStateOf(false) }
    val askBeforeDiscarding by viewModel.askBeforeDiscarding.collectAsStateWithLifecycle()
    val upiPayee by viewModel.upiPayee.collectAsStateWithLifecycle()
    val categoryPrefs by viewModel.categoryPrefs.collectAsStateWithLifecycle()
    val isCategoriesOpen by viewModel.isCategoriesOpen.collectAsStateWithLifecycle()
    val isSubscriptionsOpen by viewModel.isSubscriptionsOpen.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val isUpiOpen by viewModel.isUpiOpen.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsState()
    val billPhotoIds by viewModel.billPhotoIds.collectAsStateWithLifecycle()
    val rawTransactions by viewModel.rawTransactions.collectAsState()
    val activityFeed by viewModel.activityFeed.collectAsState()
    val totalNetOwed by viewModel.totalNetOwed.collectAsState()
    val monthlyAnalytics by viewModel.monthlyAnalytics.collectAsState()
    val insightsAnalytics by viewModel.insightsAnalytics.collectAsState()
    val insightsMonth by viewModel.insightsMonth.collectAsState()
    val earliestInsightsMonth by viewModel.earliestInsightsMonth.collectAsState()
    val personBalances by viewModel.personBalances.collectAsState()
    val people by viewModel.people.collectAsState()

    // One colour per person (design item 11). Groupmates' chosen colours are what mine keeps clear
    // of; everyone on this phone is what a new friend's colour keeps clear of.
    val groupmateHues = remember(people) { people.filter { it.profileId != null }.mapNotNull { it.hue } }
    val myHue = authUiState.hue
    val phoneColours = remember(people, myHue) { people.map { it.avatarColor } + listOfNotNull(myHue?.let(Hues::hex)) }
    LaunchedEffect(myHue) { viewModel.fitColoursAround(myHue) }
    val colourClash = remember(people, myHue) {
        myHue?.let { mine -> people.firstOrNull { it.profileId != null && it.hue != null && Hues.distance(it.hue, mine) < Hues.MIN_GAP }?.name }
    }
    // Someone who never chose gets one, picked clear of their groupmates, once their profile loads.
    val profileLoaded = authUiState.profile is ProfileState.Loaded
    LaunchedEffect(profileLoaded, myHue == null, groupmateHues) {
        if (profileLoaded && myHue == null) Hues.firstPick(selfProfileId, groupmateHues)?.let { onSaveHue(it, true) }
    }
    val groups by viewModel.groups.collectAsState()
    val rawGroups by viewModel.rawGroups.collectAsState()
    val isParsing by viewModel.isParsing.collectAsState()
    val voiceCaptureRequested by viewModel.voiceCaptureRequested.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    // Confirm Bottom Sheet State
    val isConfirmSheetOpen by viewModel.isConfirmSheetOpen.collectAsState()
    val editingTxId by viewModel.editingTransactionId.collectAsState()
    val editingAddedBy by viewModel.editingAddedBy.collectAsState()
    val txType by viewModel.type.collectAsState()
    val txAmount by viewModel.amount.collectAsState()
    val txMerchant by viewModel.merchant.collectAsState()
    val txCategory by viewModel.category.collectAsState()
    val txPaymentMethod by viewModel.paymentMethod.collectAsState()
    val txNote by viewModel.note.collectAsState()
    val txDate by viewModel.date.collectAsState()
    val txGroupId by viewModel.groupId.collectAsState()
    val isSplit by viewModel.isSplit.collectAsState()
    val splitMode by viewModel.splitMode.collectAsState()
    val paidByPersonId by viewModel.paidByPersonId.collectAsState()
    val shares by viewModel.shares.collectAsState()

    // Dialogs & Modals
    val selectedPersonForLedger by viewModel.selectedPersonForLedger.collectAsState()
    val isSettleUpOpen by viewModel.isSettleUpOpen.collectAsState()
    val settleUpPerson by viewModel.settleUpPerson.collectAsState()
    val settleUpHisaab by viewModel.settleUpHisaab.collectAsStateWithLifecycle()
    val askedAt by viewModel.askedAt.collectAsStateWithLifecycle()
    val isIouOpen by viewModel.isIouOpen.collectAsState()
    val isSimplifyDebtsOpen by viewModel.isSimplifyDebtsOpen.collectAsState()
    val simplifiedDebts by viewModel.simplifiedDebts.collectAsState()
    val splitLocked by viewModel.splitLocked.collectAsStateWithLifecycle()
    val settlementsAwaitingMe by viewModel.settlementsAwaitingMe.collectAsStateWithLifecycle()
    val settlementsAwaitingThem by viewModel.settlementsAwaitingThem.collectAsStateWithLifecycle()
    val answeringSettlement by viewModel.answeringSettlement.collectAsStateWithLifecycle()
    val isAddFriendOpen by viewModel.isAddFriendOpen.collectAsState()

    // Group Modals
    val isCreateGroupOpen by viewModel.isCreateGroupOpen.collectAsState()
    val createGroupShared by viewModel.createGroupShared.collectAsState()
    val splitGroupPrompt by viewModel.splitGroupPrompt.collectAsState()
    val selectedGroupForDetail by viewModel.selectedGroupForDetail.collectAsState()

    // AI Summary
    val askBoard by viewModel.askBoard.collectAsState()
    val aiAsksLeft by viewModel.aiAsksLeft.collectAsState()
    val tripReview by viewModel.tripReview.collectAsState()

    // Statement Import State
    val isImportStatementOpen by viewModel.isImportStatementOpen.collectAsState()
    val isParsingStatement by viewModel.isParsingStatement.collectAsState()
    val statementParseResult by viewModel.statementParseResult.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val sharedGroupsState by sharedGroups.state.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // The daily reminder needs notifications. Asked for once by the app itself, the first time
    // there's an entry of the person's own — never on a first launch before they've seen the app —
    // and after that only from the reminder's row in settings.
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val notificationPermissionAsked by viewModel.notificationPermissionAsked.collectAsStateWithLifecycle()
    var notificationsAllowed by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        // Back from the system's notification settings, or the permission dialog.
        notificationsAllowed = NotificationHelper.canNotify(context)
        onPauseOrDispose { }
    }
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = NotificationHelper.canNotify(context)
    }
    val hasOwnEntry = rawTransactions.any { it.transaction.isOwnedByMe }
    LaunchedEffect(hasOwnEntry, notificationPermissionAsked, reminder.enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasOwnEntry && reminder.enabled &&
            !notificationPermissionAsked && !NotificationHelper.canNotify(context)
        ) {
            viewModel.markNotificationPermissionAsked()
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val allowNotifications: () -> Unit = {
        val activity = context as? Activity
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val canStillAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
            // Never asked, or declined once (Android shows the dialog a second time, then stops).
            (!notificationPermissionAsked || activity?.shouldShowRequestPermissionRationale(permission) == true)
        if (canStillAsk) {
            viewModel.markNotificationPermissionAsked()
            askForNotifications.launch(permission)
        } else {
            // Declined for good, or turned off in system settings: only the settings page can fix it.
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            }
            context.startActivity(intent)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val messageScope = rememberCoroutineScope()
    // Shown from a separate scope: clearing the message changes these keys, which would cancel a
    // snackbar shown from inside the effect the moment it appeared.
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            messageScope.launch { snackbarHostState.showSnackbar(it) }
            viewModel.clearStatusMessage()
        }
    }
    val undoPrompt by viewModel.undoPrompt.collectAsState()
    LaunchedEffect(undoPrompt) {
        val prompt = undoPrompt ?: return@LaunchedEffect
        viewModel.clearUndoPrompt()
        // A second delete replaces the first's snackbar, which commits the first.
        snackbarHostState.currentSnackbarData?.dismiss()
        messageScope.launch {
            var undone = false
            try {
                undone = snackbarHostState.showSnackbar(
                    message = prompt.message,
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long,
                ) == SnackbarResult.ActionPerformed
            } finally {
                // Also when the snackbar is cut short (the screen goes away): the swipe meant delete.
                if (undone) viewModel.undoDelete(prompt.txId) else viewModel.commitDelete(prompt.txId)
            }
        }
    }
    LaunchedEffect(sharedGroupsState.message) {
        sharedGroupsState.message?.let {
            messageScope.launch { snackbarHostState.showSnackbar(it) }
            sharedGroups.clearMessage()
        }
    }
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val parseNotice by viewModel.parseNotice.collectAsState()

    val hazeState = rememberHazeState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .statusBarsPadding()
                // Sideways between tabs, only where nothing on screen wanted the drag (see TabSwipe).
                .tabSwipe(current = currentTab, count = AppTab.entries.size, onSelect = { viewModel.setTab(it) })
        ) {
            // On tablets and unfolded phones, full-width rows and figures are hard to read.
            AnimatedContent(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp),
                targetState = currentTab,
                transitionSpec = { KpMotion.tabTransition(forward = targetState > initialState) },
                label = "MainTabs"
            ) { targetTab ->
                when (targetTab) {
                    0 -> HomeScreen(
                        activity = activityFeed,
                        billPhotoIds = billPhotoIds,
                        people = people,
                        totalNetOwed = totalNetOwed,
                        monthlyAnalytics = monthlyAnalytics,
                        isParsing = isParsing,
                        searchQuery = searchQuery,
                        selectedFilter = selectedFilter,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onFilterChange = { viewModel.setSelectedFilter(it) },
                        onParseText = { viewModel.parseInputText(it) },
                        onParseImage = { viewModel.parseReceiptImage(it) },
                        onParsePdf = { bytes, name -> viewModel.parseBankOrUpiStatement(bytes, name) },
                        onEditTransaction = { viewModel.openEditTransaction(it) },
                        onDeleteTransaction = { viewModel.deleteTransaction(it) },
                        onRetractSettlement = { viewModel.retractSettlement(it) },
                        onAnswerSettlement = { viewModel.openSettlementAnswer(it) },
                        onOpenIouDialog = { viewModel.openIouDialog() },
                        onOpenSimplifyDebts = { viewModel.openSimplifyDebts() },
                        accountAvatar = authUiState.accountAvatar,
                        startVoiceCapture = voiceCaptureRequested,
                        syncStatus = syncStatus,
                        onShowMessage = viewModel::showMessage,
                        onVoiceCaptureStarted = viewModel::onVoiceCaptureStarted,
                        onOpenAccount = { isAccountSheetOpen = true }
                    )

                    1 -> SplitsScreen(
                        accountAvatar = authUiState.accountAvatar,
                        onOpenAccount = { isAccountSheetOpen = true },
                        syncStatus = syncStatus,
                        personBalances = personBalances,
                        groups = groups,
                        onOpenAddFriend = { viewModel.openAddFriend() },
                        onDeleteFriend = { viewModel.deleteFriend(it) },
                        onOpenCreateGroup = { viewModel.openCreateGroup() },
                        onJoinGroup = { sharedGroups.openJoin() },
                        onSelectGroup = { viewModel.selectGroupForDetail(it) },
                        onDeleteGroup = { viewModel.deleteGroup(it) },
                        onOpenSimplifyDebts = { viewModel.openSimplifyDebts() },
                        onOpenIouDialog = { viewModel.openIouDialog() },
                        onSettleUp = { viewModel.openSettleUp(it) },
                        onViewHistory = { viewModel.openPersonLedger(it) },
                        awaitingMe = settlementsAwaitingMe,
                        awaitingThem = settlementsAwaitingThem,
                        onAnswerSettlement = { viewModel.openSettlementAnswer(it) },
                        onWithdrawSettlement = { viewModel.retractSettlement(it) },
                        askedAt = askedAt,
                    )

                    2 -> InsightsScreen(
                        accountAvatar = authUiState.accountAvatar,
                        onOpenAccount = { isAccountSheetOpen = true },
                        syncStatus = syncStatus,
                        analytics = insightsAnalytics,
                        transactions = rawTransactions,
                        people = people,
                        askBoard = askBoard,
                        onAsk = { viewModel.askInsights(it) },
                        aiAsksLeft = aiAsksLeft,
                        aiAvailable = viewModel.aiAvailable,
                        onExport = { viewModel.openExport() },
                        onEditTransaction = { viewModel.openEditTransaction(it) },
                        month = insightsMonth,
                        earliestMonth = earliestInsightsMonth,
                        onMonthChange = { viewModel.setInsightsMonth(it) },
                    )
                }
            }
        }
        // Only the dock samples the content: sheets and dialogs are other windows (see LocalHazeState).
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            KpSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = snackbarClearance()),
            )
            KpDock(
                currentTab = AppTab.entries[currentTab],
                onSelectTab = { viewModel.setTab(it.ordinal) },
                onAdd = { viewModel.openManualAdd("expense") },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    // OpenDocument rather than GetContent: it returns a URI that survives the process being
    // rebuilt, and a backup is exactly the thing someone picks from Drive or Files rather than
    // from a gallery.
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isAccountSheetOpen = false
            viewModel.importLocalBackup(context, uri)
        }
    }

    if (isAccountSheetOpen) {
        AccountSheet(
            colour = ColourChoice(taken = groupmateHues, clashWith = colourClash),
            onSaveHue = { onSaveHue(it, false) },
            state = authUiState,
            syncStatus = syncStatus,
            askBeforeDiscarding = askBeforeDiscarding,
            upiId = upiPayee?.upiId,
            onOpenUpi = {
                isAccountSheetOpen = false
                viewModel.openUpi()
            },
            onOpenCategories = {
                isAccountSheetOpen = false
                viewModel.openCategories()
            },
            onOpenSubscriptions = {
                isAccountSheetOpen = false
                viewModel.openSubscriptions()
            },
            onSaveProfile = onSaveProfile,
            onAskBeforeDiscardingChange = viewModel::setAskBeforeDiscarding,
            onOpenExport = {
                isAccountSheetOpen = false
                viewModel.openExport()
            },
            onBackUp = {
                isAccountSheetOpen = false
                viewModel.exportLocalBackup(context)
            },
            onRestore = { pickBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onRetryFailedSync = viewModel::retryFailedSync,
            onRetryProfile = onRetryProfile,
            onSignOut = {
                isAccountSheetOpen = false
                onSignOut()
            },
            onDismiss = { isAccountSheetOpen = false },
            reminder = reminder,
            notificationsAllowed = notificationsAllowed,
            onReminderEnabledChange = viewModel::setReminderEnabled,
            onReminderTimeChange = viewModel::setReminderTime,
            onAllowNotifications = allowNotifications,
        )
    }

    SettlementAnswerSheet(
        item = answeringSettlement,
        onConfirm = { viewModel.answerSettlement(confirm = true) },
        onReject = { note -> viewModel.answerSettlement(confirm = false, note = note) },
        onDismiss = { viewModel.dismissSettlementAnswer() },
    )

    // Confirm & Edit Bottom Sheet
    // Bill photos: the system camera writes into cache/camera through the FileProvider, and the
    // photo picker hands back a picture. The app declares no CAMERA permission, so neither asks
    // for one. The camera's original is deleted once it's read; only the re-encoded copy is kept.
    val billPhoto by viewModel.billPhoto.collectAsStateWithLifecycle()
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val takeBillPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = cameraFile?.let(::File) ?: return@rememberLauncherForActivityResult
        cameraFile = null
        if (taken) viewModel.attachBillPhoto(context, Uri.fromFile(file)) { file.delete() } else file.delete()
    }
    val pickBillPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attachBillPhoto(context, uri)
    }
    val addBillPhoto: (BillPhotoSource) -> Unit = { source ->
        when (source) {
            BillPhotoSource.Camera -> {
                val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
                cameraFile = file.path
                try {
                    takeBillPhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                } catch (e: ActivityNotFoundException) {
                    cameraFile = null
                    viewModel.showMessage("No camera app on this phone. Choose a photo from the gallery instead.")
                }
            }
            BillPhotoSource.Gallery -> pickBillPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    ConfirmBottomSheet(
        isOpen = isConfirmSheetOpen,
        isParsing = isParsing,
        notice = parseNotice,
        type = txType,
        amount = txAmount,
        merchant = txMerchant,
        category = txCategory,
        paymentMethod = txPaymentMethod,
        note = txNote,
        date = txDate,
        groupId = txGroupId,
        // Archived shared groups are read-only (decision 4), so they can't be picked for new expenses.
        availableGroups = rawGroups.filterNot { it.isShared && it.isArchived },
        isSplit = isSplit,
        splitMode = splitMode,
        paidByPersonId = paidByPersonId,
        shares = shares,
        isEditing = editingTxId != null,
        addedBy = editingAddedBy,
        splitLocked = splitLocked,
        onTypeChange = { viewModel.updateType(it) },
        onAmountChange = { viewModel.updateAmount(it) },
        onMerchantChange = { viewModel.updateMerchant(it) },
        onCategoryChange = { viewModel.updateCategory(it) },
        onPaymentMethodChange = { viewModel.updatePaymentMethod(it) },
        onNoteChange = { viewModel.updateNote(it) },
        onDateChange = { viewModel.updateDate(it) },
        onGroupIdChange = { viewModel.updateGroupId(it) },
        onIsSplitChange = { viewModel.setIsSplit(it) },
        onSplitModeChange = { viewModel.setSplitMode(it) },
        onPaidByPersonIdChange = { viewModel.setPaidByPersonId(it) },
        onTogglePersonShare = { viewModel.togglePersonShare(it) },
        onExactAmountChange = { id, amt -> viewModel.updateExactShareAmount(id, amt) },
        onPercentageChange = { id, pct -> viewModel.updateSharePercentage(id, pct) },
        onOpenAddFriend = { viewModel.openAddFriend() },
        onSave = { viewModel.saveCurrentTransaction() },
        onDismiss = { viewModel.dismissConfirmSheet() },
        hasUnsavedInput = { askBeforeDiscarding && viewModel.hasUnsavedEntry() },
        categories = Categories.categoriesFor(txType, categoryPrefs),
        paymentMethods = Categories.paymentMethods(categoryPrefs),
        billPhoto = billPhoto,
        onAddBillPhoto = addBillPhoto,
        onRemoveBillPhoto = viewModel::removeBillPhoto,
    )

    val isExportOpen by viewModel.isExportOpen.collectAsState()
    ExportSheet(
        isOpen = isExportOpen,
        transactions = rawTransactions,
        initialMonth = insightsMonth,
        earliestMonth = earliestInsightsMonth,
        onExport = { viewModel.exportCsv(context, it) },
        onDismiss = { viewModel.closeExport() },
    )

    // Settle up
    val currentPersonBalance = settleUpPerson?.let { p ->
        personBalances.find { it.person.id == p.id }
    }
    SettleUpSheet(
        isOpen = isSettleUpOpen,
        person = settleUpPerson,
        suggestedAmount = currentPersonBalance?.netBalance ?: 0.0,
        groupOptions = remember(settleUpPerson, people, groups) {
            viewModel.groupOptionsFor(listOfNotNull(settleUpPerson?.id))
        },
        onCreateGroup = { viewModel.openCreateSharedGroup() },
        onConfirm = { fromId, toId, amt, note, groupId ->
            viewModel.recordSettlementPayment(fromId, toId, amt, note, groupId)
        },
        onDismiss = { viewModel.dismissSettleUp() },
        upiPayee = upiPayee,
        hisaab = settleUpHisaab,
        onAsk = { amount, message -> settleUpPerson?.let { viewModel.askToPay(context, it, amount, message) } },
    )

    SubscriptionsSheet(
        isOpen = isSubscriptionsOpen,
        subscriptions = subscriptions,
        prefs = categoryPrefs,
        onAdd = { new, chargeFromStart -> viewModel.addSubscription(new, chargeFromStart) },
        onCancel = { viewModel.cancelSubscription(it) },
        onDelete = { viewModel.deleteSubscription(it) },
        onDismiss = { viewModel.closeSubscriptions() },
    )

    CategoriesSheet(
        isOpen = isCategoriesOpen,
        prefs = categoryPrefs,
        onChange = { viewModel.updateCategoryPrefs(it) },
        onDismiss = { viewModel.closeCategories() },
    )

    UpiSheet(
        isOpen = isUpiOpen,
        saved = upiPayee,
        defaultName = authUiState.displayName.orEmpty(),
        onSave = { viewModel.saveUpi(it) },
        onRemove = { viewModel.saveUpi(null) },
        onDismiss = { viewModel.closeUpi() },
    )

    // IOU
    IouSheet(
        isOpen = isIouOpen,
        people = people,
        groupOptionsFor = remember(people, groups) { { personId -> viewModel.groupOptionsFor(listOf(personId)) } },
        onCreateGroup = { viewModel.openCreateSharedGroup() },
        onConfirm = { fromId, toId, amt, note, groupId ->
            viewModel.recordIou(fromId, toId, amt, note, groupId)
        },
        onDismiss = { viewModel.dismissIouDialog() },
        upiPayee = upiPayee,
    )

    // Simplify Debts Dialog
    SimplifyDebtsSheet(
        isOpen = isSimplifyDebtsOpen,
        simplifiedDebts = simplifiedDebts,
        onDismiss = { viewModel.dismissSimplifyDebts() }
    )

    // New group
    CreateGroupSheet(
        isOpen = isCreateGroupOpen,
        allPeople = people,
        initialShared = createGroupShared,
        onSaveGroup = { name, desc, emoji, color, memberIds, shared ->
            viewModel.createNewGroup(name, desc, emoji, color, memberIds, shared)
        },
        onDismiss = { viewModel.dismissCreateGroup() }
    )

    // Group Detail Sheet
    GroupDetailSheet(
        groupDetails = selectedGroupForDetail,
        onAddExpense = { groupId ->
            viewModel.openManualAddForGroup(groupId)
        },
        onEditTransaction = { viewModel.openEditTransaction(it) },
        onSettleUpWith = { personId ->
            people.firstOrNull { it.id == personId }?.let { person ->
                viewModel.dismissGroupDetail()
                viewModel.openSettleUp(person)
            }
        },
        onExportGroup = { groupId -> viewModel.exportGroupCsv(context, groupId) },
        onAskTripReview = { groupId -> viewModel.askTripReview(groupId) },
        tripReview = tripReview,
        aiAsksLeft = aiAsksLeft,
        aiAvailable = viewModel.aiAvailable,
        onDismiss = {
            sharedGroups.dismissInvite()
            viewModel.dismissGroupDetail()
        },
        sharedGroupPanel = { details ->
            val group = details.group
            SharedGroupPanel(
                group = group,
                members = details.members,
                isCreator = group.createdByProfileId == selfProfileId,
                invite = sharedGroupsState.invite?.takeIf { it.groupId == group.id },
                isLeaving = sharedGroupsState.leavingGroupId == group.id,
                unsyncedChanges = details.unsyncedChanges,
                onSyncNow = { viewModel.requestSync() },
                onCreateInvite = { sharedGroups.createInvite(group) },
                onShareInvite = { link -> shareInviteLink(context, group.name, link) },
                onRevokeInvite = { sharedGroups.revokeInvite() },
                onSetArchived = { sharedGroups.setArchived(group, it) },
                onLeave = { sharedGroups.leaveGroup(group) { viewModel.dismissGroupDetail() } },
            )
        },
    )

    ShareSplitPrompt(
        options = splitGroupPrompt,
        onShareIn = viewModel::shareSplitInGroup,
        onCreateGroup = viewModel::createGroupFromSplitPrompt,
        onKeepLocal = viewModel::saveSplitOnThisPhone,
        onDismiss = viewModel::dismissSplitGroupPrompt,
    )

    JoinGroupSheet(
        state = sharedGroupsState.join,
        onInputChange = sharedGroups::onJoinInputChange,
        onJoin = sharedGroups::confirmJoin,
        onDismiss = sharedGroups::dismissJoin,
    )

    // Person Ledger Sheet
    if (selectedPersonForLedger != null) {
        val ledgerFlow = viewModel.getPersonLedgerFlow(selectedPersonForLedger!!.id)
        val ledgerItems by ledgerFlow.collectAsState(initial = emptyList())
        PersonLedgerSheet(
            person = selectedPersonForLedger,
            ledgerItems = ledgerItems,
            onSettleUp = {
                viewModel.closePersonLedger()
                viewModel.openSettleUp(it)
            },
            onDismiss = { viewModel.closePersonLedger() }
        )
    }

    // Add friend
    AddFriendSheet(
        isOpen = isAddFriendOpen,
        onAdd = { name, colorHex ->
            viewModel.addNewFriend(name, colorHex)
        },
        onDismiss = { viewModel.dismissAddFriend() },
        takenColours = phoneColours,
    )

    // Bank & UPI Statement Parsing Loading Overlay
    if (isParsingStatement) {
        StatementParsingLoadingDialog(
            onDismiss = { viewModel.dismissStatementImportDialog() }
        )
    }

    // Bank & UPI Statement Import Dialog
    if (statementParseResult != null) {
        StatementImportDialog(
            parseResult = statementParseResult!!,
            onConfirmImport = { items ->
                viewModel.confirmImportStatementTransactions(items)
            },
            onDismiss = {
                viewModel.dismissStatementImportDialog()
            },
            prefs = categoryPrefs,
        )
    }
}

private fun shareInviteLink(context: Context, groupName: String, link: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Join \"$groupName\" on Kharcha Paani to split expenses together: $link")
    }
    context.startActivity(Intent.createChooser(send, "Share invite"))
}
