package com.vaibhav.kharchapaani

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.vaibhav.kharchapaani.ui.components.AddFriendDialog
import com.vaibhav.kharchapaani.ui.components.ConfirmBottomSheet
import com.vaibhav.kharchapaani.ui.components.CreateGroupDialog
import com.vaibhav.kharchapaani.ui.components.GroupDetailSheet
import com.vaibhav.kharchapaani.data.remote.SupabaseClientProvider
import com.vaibhav.kharchapaani.data.sync.SupabaseLedgerChanges
import com.vaibhav.kharchapaani.data.sync.SyncDoorbell
import com.vaibhav.kharchapaani.domain.InviteLink
import com.vaibhav.kharchapaani.ui.groups.JoinGroupSheet
import com.vaibhav.kharchapaani.ui.groups.SharedGroupPanel
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsController
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsViewModel
import com.vaibhav.kharchapaani.ui.components.IouDialog
import com.vaibhav.kharchapaani.ui.components.PersonLedgerSheet
import com.vaibhav.kharchapaani.ui.components.SettleUpDialog
import com.vaibhav.kharchapaani.ui.components.SimplifyDebtsDialog
import com.vaibhav.kharchapaani.ui.components.StatementImportDialog
import com.vaibhav.kharchapaani.ui.components.StatementParsingLoadingDialog
import com.vaibhav.kharchapaani.ui.screens.HomeScreen
import com.vaibhav.kharchapaani.ui.screens.InsightsScreen
import com.vaibhav.kharchapaani.ui.screens.SplitsScreen
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
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
    onSignOut: () -> Unit,
) {
    var isAccountSheetOpen by rememberSaveable { mutableStateOf(false) }
    val currentTab by viewModel.currentTab.collectAsState()
    val rawTransactions by viewModel.rawTransactions.collectAsState()
    val filteredTransactions by viewModel.filteredTransactions.collectAsState()
    val totalNetOwed by viewModel.totalNetOwed.collectAsState()
    val monthlyAnalytics by viewModel.monthlyAnalytics.collectAsState()
    val personBalances by viewModel.personBalances.collectAsState()
    val people by viewModel.people.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val rawGroups by viewModel.rawGroups.collectAsState()
    val isParsing by viewModel.isParsing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    // Confirm Bottom Sheet State
    val isConfirmSheetOpen by viewModel.isConfirmSheetOpen.collectAsState()
    val editingTxId by viewModel.editingTransactionId.collectAsState()
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
    val selectedGroupForDetail by viewModel.selectedGroupForDetail.collectAsState()

    // AI Summary
    val aiSummary by viewModel.aiSummary.collectAsState()
    val isGeneratingSummary by viewModel.isGeneratingSummary.collectAsState()

    // Statement Import State
    val isImportStatementOpen by viewModel.isImportStatementOpen.collectAsState()
    val isParsingStatement by viewModel.isParsingStatement.collectAsState()
    val statementParseResult by viewModel.statementParseResult.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val sharedGroupsState by sharedGroups.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearStatusMessage()
        }
    }
    LaunchedEffect(sharedGroupsState.message) {
        sharedGroupsState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            sharedGroups.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CharcoalBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = CharcoalSurface,
                contentColor = OffWhiteText,
                modifier = Modifier
                    .background(CharcoalSurface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "Activity",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            "Activity",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CharcoalBg,
                        selectedTextColor = WarmAmberPrimary,
                        indicatorColor = WarmAmberPrimary,
                        unselectedIconColor = MutedWarmGray,
                        unselectedTextColor = MutedWarmGray
                    ),
                    modifier = Modifier.testTag("tab_activity")
                )

                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { viewModel.setTab(1) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Splits & Groups",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            "Splits & Trips",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CharcoalBg,
                        selectedTextColor = WarmAmberPrimary,
                        indicatorColor = WarmAmberPrimary,
                        unselectedIconColor = MutedWarmGray,
                        unselectedTextColor = MutedWarmGray
                    ),
                    modifier = Modifier.testTag("tab_splits")
                )

                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Insights",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            "Insights",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CharcoalBg,
                        selectedTextColor = WarmAmberPrimary,
                        indicatorColor = WarmAmberPrimary,
                        unselectedIconColor = MutedWarmGray,
                        unselectedTextColor = MutedWarmGray
                    ),
                    modifier = Modifier.testTag("tab_insights")
                )
            }
        },
        floatingActionButton = {
            if (currentTab == 0) {
                FloatingActionButton(
                    onClick = { viewModel.openManualAdd("expense") },
                    containerColor = WarmAmberPrimary,
                    contentColor = CharcoalBg,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 70.dp)
                        .testTag("fab_add_transaction")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transaction",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "MainTabs"
            ) { targetTab ->
                when (targetTab) {
                    0 -> HomeScreen(
                        transactions = filteredTransactions,
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
                        onManualAdd = { viewModel.openManualAdd(it) },
                        onEditTransaction = { viewModel.openEditTransaction(it) },
                        onDeleteTransaction = { viewModel.deleteTransaction(it) },
                        onOpenIouDialog = { viewModel.openIouDialog() },
                        onOpenSimplifyDebts = { viewModel.openSimplifyDebts() },
                        accountInitial = authUiState.accountInitial,
                        onOpenAccount = { isAccountSheetOpen = true }
                    )

                    1 -> SplitsScreen(
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
                        analytics = monthlyAnalytics,
                        transactions = rawTransactions,
                        people = people,
                        aiSummary = aiSummary,
                        isGeneratingSummary = isGeneratingSummary,
                        onGenerateAiSummary = { viewModel.generateAiFinancialSummary() },
                        onExportCsv = { viewModel.exportCsv(it) },
                        onEditTransaction = { viewModel.openEditTransaction(it) }
                    )
                }
            }
        }
    }

    if (isAccountSheetOpen) {
        val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
        AccountSheet(
            state = authUiState,
            syncStatus = syncStatus,
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
        type = txType,
        amount = txAmount,
        merchant = txMerchant,
        category = txCategory,
        paymentMethod = txPaymentMethod,
        note = txNote,
        date = txDate,
        groupId = txGroupId,
        availableGroups = rawGroups,
        isSplit = isSplit,
        splitMode = splitMode,
        paidByPersonId = paidByPersonId,
        shares = shares,
        isEditing = editingTxId != null,
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
        onDismiss = { viewModel.dismissConfirmSheet() }
    )

    // Settle Up Dialog
    val currentPersonBalance = settleUpPerson?.let { p ->
        personBalances.find { it.person.id == p.id }
    }
    SettleUpDialog(
        isOpen = isSettleUpOpen,
        person = settleUpPerson,
        suggestedAmount = currentPersonBalance?.netBalance ?: 0.0,
        onConfirm = { fromId, toId, amt, note ->
            viewModel.recordSettlementPayment(fromId, toId, amt, note)
        },
        onDismiss = { viewModel.dismissSettleUp() }
    )

    // Direct IOU Dialog
    IouDialog(
        isOpen = isIouOpen,
        people = people,
        onConfirm = { fromId, toId, amt, note ->
            viewModel.recordIou(fromId, toId, amt, note)
        },
        onDismiss = { viewModel.dismissIouDialog() }
    )

    // Simplify Debts Dialog
    SimplifyDebtsDialog(
        isOpen = isSimplifyDebtsOpen,
        simplifiedDebts = simplifiedDebts,
        onDismiss = { viewModel.dismissSimplifyDebts() }
    )

    // Create Group Dialog
    CreateGroupDialog(
        isOpen = isCreateGroupOpen,
        allPeople = people,
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
                onCreateInvite = { sharedGroups.createInvite(group) },
                onShareInvite = { link -> shareInviteLink(context, group.name, link) },
                onRevokeInvite = { sharedGroups.revokeInvite() },
                onSetArchived = { sharedGroups.setArchived(group, it) },
                onLeave = { sharedGroups.leaveGroup(group) { viewModel.dismissGroupDetail() } },
            )
        },
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

    // Add Friend Dialog
    AddFriendDialog(
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
            }
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
