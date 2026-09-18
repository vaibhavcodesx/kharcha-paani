package com.vaibhav.kharchapaani

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
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
import com.vaibhav.kharchapaani.ui.theme.KpMotion
import com.vaibhav.kharchapaani.ui.theme.LocalHazeState
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
                SplitFlowApp(
                    viewModel = viewModel,
                    sharedGroups = sharedGroups.controller,
                    selfProfileId = session.userId,
                    authUiState = authUiState,
                    onRetryProfile = authViewModel::retryProfileLoad,
                    onSaveProfile = authViewModel::saveProfile,
                    onSignOut = { authViewModel.signOut(this@MainActivity) },
                )
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
    val rawTransactions by viewModel.rawTransactions.collectAsState()
    val activityFeed by viewModel.activityFeed.collectAsState()
    val totalNetOwed by viewModel.totalNetOwed.collectAsState()
    val monthlyAnalytics by viewModel.monthlyAnalytics.collectAsState()
    val insightsAnalytics by viewModel.insightsAnalytics.collectAsState()
    val insightsMonth by viewModel.insightsMonth.collectAsState()
    val earliestInsightsMonth by viewModel.earliestInsightsMonth.collectAsState()
    val personBalances by viewModel.personBalances.collectAsState()
    val people by viewModel.people.collectAsState()
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
    val isIouOpen by viewModel.isIouOpen.collectAsState()
    val isSimplifyDebtsOpen by viewModel.isSimplifyDebtsOpen.collectAsState()
    val simplifiedDebts by viewModel.simplifiedDebts.collectAsState()
    val isAddFriendOpen by viewModel.isAddFriendOpen.collectAsState()

    // Group Modals
    val isCreateGroupOpen by viewModel.isCreateGroupOpen.collectAsState()
    val createGroupShared by viewModel.createGroupShared.collectAsState()
    val splitGroupPrompt by viewModel.splitGroupPrompt.collectAsState()
    val selectedGroupForDetail by viewModel.selectedGroupForDetail.collectAsState()

    // AI Summary
    val aiSummary by viewModel.aiSummary.collectAsState()
    val askedPrompt by viewModel.askedPrompt.collectAsState()
    val aiAsksLeft by viewModel.aiAsksLeft.collectAsState()
    val tripReview by viewModel.tripReview.collectAsState()
    val isReviewingTrip by viewModel.isReviewingTrip.collectAsState()
    val tripReviewFailed by viewModel.tripReviewFailed.collectAsState()
    val isGeneratingSummary by viewModel.isGeneratingSummary.collectAsState()

    // Statement Import State
    val isImportStatementOpen by viewModel.isImportStatementOpen.collectAsState()
    val isParsingStatement by viewModel.isParsingStatement.collectAsState()
    val statementParseResult by viewModel.statementParseResult.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val sharedGroupsState by sharedGroups.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
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
    val aiSummaryFailed by viewModel.aiSummaryFailed.collectAsState()
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
                        onViewHistory = { viewModel.openPersonLedger(it) }
                    )

                    2 -> InsightsScreen(
                        accountAvatar = authUiState.accountAvatar,
                        onOpenAccount = { isAccountSheetOpen = true },
                        syncStatus = syncStatus,
                        analytics = insightsAnalytics,
                        transactions = rawTransactions,
                        people = people,
                        aiSummary = aiSummary,
                        isGeneratingSummary = isGeneratingSummary,
                        onAsk = { viewModel.askInsights(it) },
                        askedPrompt = askedPrompt,
                        aiAsksLeft = aiAsksLeft,
                        aiAvailable = viewModel.aiAvailable,
                        onExport = { viewModel.openExport() },
                        onEditTransaction = { viewModel.openEditTransaction(it) },
                        aiSummaryFailed = aiSummaryFailed,
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

    if (isAccountSheetOpen) {
        AccountSheet(
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
            onRetryFailedSync = viewModel::retryFailedSync,
            onRetryProfile = onRetryProfile,
            onSignOut = {
                isAccountSheetOpen = false
                onSignOut()
            },
            onDismiss = { isAccountSheetOpen = false }
        )
    }

    // Confirm & Edit Bottom Sheet
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
        onDismiss = { viewModel.dismissIouDialog() }
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
        isReviewingTrip = isReviewingTrip,
        tripReviewFailed = tripReviewFailed,
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
        onDismiss = { viewModel.dismissAddFriend() }
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
