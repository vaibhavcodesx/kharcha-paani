package com.vaibhav.kharchapaani.ui.viewmodel

import com.vaibhav.kharchapaani.domain.receiptNotice
import com.vaibhav.kharchapaani.domain.quickAddNotice
import com.vaibhav.kharchapaani.domain.AiFailure
import com.vaibhav.kharchapaani.domain.AiAskResult
import com.vaibhav.kharchapaani.data.repository.ReceiptReadResult
import android.app.Application
import android.content.ClipData
import android.content.Context
import com.vaibhav.kharchapaani.ask.AskShare
import com.vaibhav.kharchapaani.domain.HisaabItemiser
import com.vaibhav.kharchapaani.domain.UpiLink
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.withTransaction
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.data.model.ParsedTransactionResult
import com.vaibhav.kharchapaani.data.model.PersonBalance
import com.vaibhav.kharchapaani.data.model.PersonLedgerItem
import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SimplifiedDebt
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.data.model.StatementParseResult
import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import com.vaibhav.kharchapaani.data.repository.ArchivedGroupException
import com.vaibhav.kharchapaani.ui.components.splitProblem
import com.vaibhav.kharchapaani.data.remote.AppIdentity
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.repository.LocalBackupRepository
import com.vaibhav.kharchapaani.data.repository.toBackup
import com.vaibhav.kharchapaani.data.repository.ParseChoices
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.repository.NotYoursToWithdrawException
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.data.sync.SyncScheduler
import com.vaibhav.kharchapaani.data.sync.TransactionSyncEngine
import com.vaibhav.kharchapaani.BuildConfig
import com.vaibhav.kharchapaani.data.auth.AuthRepository
import com.vaibhav.kharchapaani.data.remote.SupabaseClientProvider
import com.vaibhav.kharchapaani.domain.AI_DAILY_CAP
import com.vaibhav.kharchapaani.domain.AiContext
import com.vaibhav.kharchapaani.domain.AiPrompt
import com.vaibhav.kharchapaani.domain.AskBoard
import com.vaibhav.kharchapaani.domain.AskState
import com.vaibhav.kharchapaani.domain.LocalBackup
import com.vaibhav.kharchapaani.domain.BackupSettings
import com.vaibhav.kharchapaani.domain.BackupReadResult
import com.vaibhav.kharchapaani.domain.DailySpend
import com.vaibhav.kharchapaani.domain.GroupInsights
import com.vaibhav.kharchapaani.ui.format.MonthNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import com.vaibhav.kharchapaani.domain.StatementReadFailure
import com.vaibhav.kharchapaani.domain.StatementReadResult
import com.vaibhav.kharchapaani.domain.ActivityFeed
import com.vaibhav.kharchapaani.domain.CsvFile
import com.vaibhav.kharchapaani.domain.DebtSimplifier
import com.vaibhav.kharchapaani.domain.ExportRange
import com.vaibhav.kharchapaani.domain.Categories
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.domain.EntryDraft
import com.vaibhav.kharchapaani.domain.UpiPayee
import com.vaibhav.kharchapaani.domain.MonthAnalytics
import com.vaibhav.kharchapaani.domain.SharedRecordGroups
import com.vaibhav.kharchapaani.domain.SplitRounding
import com.vaibhav.kharchapaani.ui.components.NewSubscription
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.reminder.ReminderRule
import com.vaibhav.kharchapaani.reminder.ReminderScheduler
import com.vaibhav.kharchapaani.reminder.ReminderSettings
import com.vaibhav.kharchapaani.util.NotificationHelper
import com.vaibhav.kharchapaani.widget.SplitFlowWidgetProvider
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scoped to one signed-in account: [db] is that account's ledger file. A different account gets a
 * new instance (see AccountSessionHolder), so no state or Flow from one account outlives a switch.
 */
class MainViewModel(
    application: Application,
    private val db: AppDatabase,
    private val userId: String,
) : AndroidViewModel(application) {

    private val ledgerRepo = LedgerRepository(db, onLocalTransactionChange = ::requestSync, selfProfileId = userId)

    private val authRepo = AuthRepository(
        supabase = SupabaseClientProvider.get(application),
        googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
    )

    val syncStatus: StateFlow<SyncStatus> = combine(
        db.syncDao().observeUnsyncedChangeCount(),
        db.syncDao().observeFailedCount(),
        db.syncDao().observeMeta(TransactionSyncEngine.LAST_SYNCED_AT),
        SyncScheduler.observeIsSyncing(application, userId),
    ) { unsynced, failed, lastSyncedAt, isSyncing ->
        SyncStatus(unsynced, failed, lastSyncedAt?.toLongOrNull(), isSyncing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus())

    fun requestSync() {
        SyncScheduler.requestSync(getApplication(), userId)
    }

    fun retryFailedSync() {
        viewModelScope.launch {
            db.withTransaction {
                db.syncDao().requeueFailedTransactions()
                db.syncDao().requeueFailedGroups()
                db.syncDao().requeueFailedSettlements()
            }
            requestSync()
        }
    }
    // Identity passed in so requests carry the headers a key restricted to this app requires.
    private val geminiRepo = GeminiRepository(appIdentity = AppIdentity.of(application))

    private val settings = AppSettings(application)

    init {
        // A subscription is a rule; its charges become real entries when their date has arrived.
        viewModelScope.launch {
            if (ledgerRepo.runDueSubscriptions() > 0) SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    val askBeforeDiscarding: StateFlow<Boolean> = settings.askBeforeDiscarding
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAskBeforeDiscarding(ask: Boolean) {
        viewModelScope.launch { settings.setAskBeforeDiscarding(ask) }
    }

    val categoryPrefs: StateFlow<CategoryPrefs> = settings.categoryPrefs(userId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, CategoryPrefs())

    fun updateCategoryPrefs(change: (CategoryPrefs) -> CategoryPrefs) {
        viewModelScope.launch { settings.updateCategoryPrefs(userId, change) }
    }

    /** The daily "nothing logged today" reminder (on at 9 PM unless changed). See DailyReminderWorker. */
    val reminder: StateFlow<ReminderSettings> = settings.reminder
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReminderSettings())

    /** Whether the app has already asked for notification permission by itself (it does so once). */
    val notificationPermissionAsked: StateFlow<Boolean> = settings.notificationPermissionAsked
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setReminderEnabled(enabled)
            ReminderScheduler.apply(getApplication(), settings.reminder.first(), replace = true)
        }
    }

    fun setReminderTime(minuteOfDay: Int) {
        viewModelScope.launch {
            settings.setReminderMinute(minuteOfDay)
            ReminderScheduler.apply(getApplication(), settings.reminder.first(), replace = true)
        }
    }

    fun markNotificationPermissionAsked() {
        viewModelScope.launch { settings.markNotificationPermissionAsked() }
    }

    init {
        // Books the reminder if nothing is booked yet (first run, or after it was switched back on
        // elsewhere); KEEP leaves an existing booking alone, so opening the app never moves it.
        viewModelScope.launch { ReminderScheduler.apply(getApplication(), settings.reminder.first(), replace = false) }
        // A reminder still in the shade after something has been logged today would be wrong.
        viewModelScope.launch {
            ledgerRepo.allTransactions.collect { transactions ->
                val zone = ZoneId.systemDefault()
                if (!ReminderRule.nothingLoggedToday(transactions, LocalDate.now(zone), zone)) {
                    NotificationHelper.clearDailyReminder(getApplication())
                }
            }
        }
    }

    val subscriptions: StateFlow<List<SubscriptionEntity>> = ledgerRepo.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSubscriptionsOpen = MutableStateFlow(false)
    val isSubscriptionsOpen: StateFlow<Boolean> = _isSubscriptionsOpen.asStateFlow()

    fun openSubscriptions() {
        _isSubscriptionsOpen.value = true
    }

    fun closeSubscriptions() {
        _isSubscriptionsOpen.value = false
    }

    fun addSubscription(new: NewSubscription, chargeFromStart: Boolean) {
        viewModelScope.launch {
            ledgerRepo.saveSubscription(
                SubscriptionEntity(
                    name = new.name,
                    amount = new.amount,
                    category = new.category,
                    paymentMethod = new.paymentMethod,
                    cycle = new.cycle.name,
                    startDate = new.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                ),
                chargeFromStart = chargeFromStart,
            )
            val added = ledgerRepo.runDueSubscriptions()
            _statusMessage.value = when {
                added == 1 -> "${new.name} added, with its charge so far."
                added > 1 -> "${new.name} added, with $added charges so far."
                else -> "${new.name} added. Its first charge lands when it's due."
            }
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    fun cancelSubscription(id: Long) {
        viewModelScope.launch {
            ledgerRepo.cancelSubscription(id)
            _statusMessage.value = "Cancelled. Charges already logged stay."
        }
    }

    fun deleteSubscription(id: Long) {
        viewModelScope.launch { ledgerRepo.deleteSubscription(id) }
    }

    private val _isCategoriesOpen = MutableStateFlow(false)
    val isCategoriesOpen: StateFlow<Boolean> = _isCategoriesOpen.asStateFlow()

    fun openCategories() {
        _isCategoriesOpen.value = true
    }

    fun closeCategories() {
        _isCategoriesOpen.value = false
    }

    private fun parseChoices() = ParseChoices.of(categoryPrefs.value)

    val upiPayee: StateFlow<UpiPayee?> = settings.upiPayee(userId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isUpiOpen = MutableStateFlow(false)
    val isUpiOpen: StateFlow<Boolean> = _isUpiOpen.asStateFlow()

    fun openUpi() {
        _isUpiOpen.value = true
    }

    fun closeUpi() {
        _isUpiOpen.value = false
    }

    fun saveUpi(payee: UpiPayee?) {
        viewModelScope.launch {
            settings.setUpiPayee(userId, payee)
            _isUpiOpen.value = false
            // Kept on this phone *and* published to the profile. Local-only was the original
            // decision, and it meant the one person who needed the ID — the groupmate about to pay
            // — was the only one who could not see it. Only groupmates can read it
            // (profiles_select_self_or_groupmate); it is not public.
            val published = runCatching { authRepo.updateUpiId(userId, payee?.upiId) }.isSuccess
            _statusMessage.value = when {
                payee == null && published -> "UPI ID removed."
                payee == null -> "Removed here. It'll clear for your groups next time you're online."
                published -> "UPI ID saved. Groupmates settling up with you will see it."
                else -> "Saved on this phone. It'll reach your groups next time you're online."
            }
        }
    }

    /**
     * Runs once the whole view model is built.
     *
     * Deliberately at the bottom of the class, not the top: Kotlin runs initialisers and `init`
     * blocks in declaration order, so an `init` above `settings` gets a null `settings` and takes
     * the app down on launch. It did, once.
     */
    init {
        publishUpiIfMissing()
        viewModelScope.launch { ledgerRepo.repairPersonColours() }
    }

    /** Sends this account's UPI ID up if the profile doesn't have it yet (first run after update). */
    private fun publishUpiIfMissing() {
        viewModelScope.launch {
            val mine = settings.upiPayee(userId).first() ?: return@launch
            runCatching {
                val profile = authRepo.fetchProfile(userId)
                if (profile?.upiId != mine.upiId) authRepo.updateUpiId(userId, mine.upiId)
            }
        }
    }

    // Navigation Tab (0: Home/Activity, 1: Splits & Groups, 2: Analytics)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Search and Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    val people: StateFlow<List<PersonEntity>> = ledgerRepo.allPeople
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personBalances: StateFlow<List<PersonBalance>> = ledgerRepo.personBalances
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<GroupWithDetails>> = ledgerRepo.groupsWithDetails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawGroups: StateFlow<List<GroupEntity>> = ledgerRepo.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawTransactions: StateFlow<List<FullTransaction>> = ledgerRepo.fullTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyAnalytics: StateFlow<MonthlyAnalytics> = ledgerRepo.monthlyAnalytics
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            MonthlyAnalytics(0.0, 0.0, 0.0, emptyList(), "None", 0, "Food", 0.0)
        )

    // Outlives the screen so a delete still waiting on its Undo is committed when the app closes.
    private val deleteScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pendingDeletes = PendingDeletes(deleteScope, ::commitTransactionDelete)

    private val _undoPrompt = MutableStateFlow<UndoPrompt?>(null)
    /** A delete that just happened and can still be undone, for the snackbar. */
    val undoPrompt: StateFlow<UndoPrompt?> = _undoPrompt.asStateFlow()

    private val _insightsMonth = MutableStateFlow(YearMonth.now())
    /** The month Insights shows; this month until another is picked. */
    val insightsMonth: StateFlow<YearMonth> = _insightsMonth.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val insightsAnalytics: StateFlow<MonthlyAnalytics> = _insightsMonth
        .flatMapLatest { ledgerRepo.monthlyAnalyticsFor(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            MonthlyAnalytics(0.0, 0.0, 0.0, emptyList(), "None", 0, "Food", 0.0)
        )

    /** The first month with anything logged, as far back as the month picker goes. */
    val earliestInsightsMonth: StateFlow<YearMonth> = ledgerRepo.allTransactions
        .map { MonthAnalytics.earliestMonth(it, LocalDate.now(), ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YearMonth.now())

    fun setInsightsMonth(month: YearMonth) {
        val picked = month.coerceAtMost(YearMonth.now())
        if (picked == _insightsMonth.value) return
        _insightsMonth.value = picked
        // A review of one month doesn't belong under another, and that goes for all three at once.
        askJob?.cancel()
        _askBoard.value = _askBoard.value.cleared()
    }

    /** Recent Activity: transactions plus settlements and IOUs, filtered and searched together. */
    val activityFeed: StateFlow<List<ActivityItem>> = combine(
        combine(rawTransactions, pendingDeletes.ids) { transactions, hidden ->
            if (hidden.isEmpty()) transactions else transactions.filterNot { it.transaction.id in hidden }
        },
        ledgerRepo.allSettlements,
        combine(people, rawGroups) { people, groups -> people to groups },
        searchQuery,
        selectedFilter
    ) { transactions, settlements, (people, groups), query, filter ->
        ActivityFeed.build(transactions, settlements, people, groups, query, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retractSettlement(settlementId: Long) {
        viewModelScope.launch {
            try {
                ledgerRepo.deleteSettlement(settlementId)
            } catch (e: ArchivedGroupException) {
                _statusMessage.value = archivedMessage(e)
                return@launch
            } catch (e: NotYoursToWithdrawException) {
                // Theirs to withdraw; mine to answer (design item 10).
                openSettlementAnswer(settlementId)
                return@launch
            }
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    /** Once my own colour is known (or changes), friends on this phone move off it. */
    fun fitColoursAround(myHue: Int?) {
        if (myHue == null) return
        viewModelScope.launch { ledgerRepo.repairPersonColours(myHue) }
    }

    // ---- Settlement confirmation (design item 10) ----

    /** Settlements between members, as feed items, for the ones waiting on my answer. */
    private val settlementItems: StateFlow<List<ActivityItem.Settlement>> = combine(
        ledgerRepo.allSettlements, people, rawGroups,
    ) { settlements, people, groups ->
        val names = people.associate { it.id to it.name }
        val groupNames = groups.associate { it.id to it.name }
        settlements.filter { it.groupId != null }.map { settlement ->
            ActivityItem.Settlement(
                settlement,
                fromName = if (settlement.fromPersonId == -1L) "You" else names[settlement.fromPersonId] ?: "Someone",
                toName = if (settlement.toPersonId == -1L) "You" else names[settlement.toPersonId] ?: "Someone",
                groupName = settlement.groupId?.let(groupNames::get),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** What groupmates say changed hands with me, waiting for me to confirm or dispute. */
    val settlementsAwaitingMe: StateFlow<List<ActivityItem.Settlement>> = settlementItems
        .map { items -> items.filter { it.awaitsMyAnswer }.sortedBy { it.settlement.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Mine, still waiting for the other person: shown so I know, with how long it's waited. */
    val settlementsAwaitingThem: StateFlow<List<ActivityItem.Settlement>> = settlementItems
        .map { items -> items.filter { it.recordedByMe && it.settlement.status == SettlementStatus.PENDING }.sortedBy { it.settlement.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _answeringSettlementId = MutableStateFlow<Long?>(null)
    val answeringSettlement: StateFlow<ActivityItem.Settlement?> = combine(_answeringSettlementId, settlementItems) { id, items ->
        id?.let { wanted -> items.firstOrNull { it.settlement.id == wanted && it.awaitsMyAnswer } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun openSettlementAnswer(settlementId: Long) {
        _answeringSettlementId.value = settlementId
    }

    fun dismissSettlementAnswer() {
        _answeringSettlementId.value = null
    }

    fun answerSettlement(confirm: Boolean, note: String = "") {
        val id = _answeringSettlementId.value ?: return
        _answeringSettlementId.value = null
        viewModelScope.launch {
            try {
                ledgerRepo.respondToSettlement(id, confirm, note)
            } catch (e: ArchivedGroupException) {
                _statusMessage.value = archivedMessage(e)
                return@launch
            }
            NotificationHelper.clearSettlementReminder(getApplication(), id)
            _statusMessage.value = if (confirm) "Confirmed. It counts for both of you now." else "Marked not right. It won't count."
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    // Overall Net Balance with friends
    val totalNetOwed: StateFlow<Double> = personBalances.combine(MutableStateFlow(Unit)) { balances, _ ->
        balances.sumOf { it.netBalance }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Capture & Confirm Bottom Sheet State
    private val _isConfirmSheetOpen = MutableStateFlow(false)
    val isConfirmSheetOpen: StateFlow<Boolean> = _isConfirmSheetOpen.asStateFlow()

    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

    // Set by the widget's Speak button; Home starts listening once, then clears it.
    private val _voiceCaptureRequested = MutableStateFlow(false)
    val voiceCaptureRequested: StateFlow<Boolean> = _voiceCaptureRequested.asStateFlow()

    fun requestVoiceCapture() {
        _voiceCaptureRequested.value = true
    }

    fun onVoiceCaptureStarted() {
        _voiceCaptureRequested.value = false
    }

    private val _editingTransactionId = MutableStateFlow<Long?>(null)
    val editingTransactionId: StateFlow<Long?> = _editingTransactionId.asStateFlow()

    /** Who added the expense being edited, when it's a groupmate's that this account paid for. */
    val editingAddedBy: StateFlow<String?> = combine(_editingTransactionId, rawTransactions, people) { id, transactions, people ->
        val tx = id?.let { editing -> transactions.firstOrNull { it.transaction.id == editing } }?.transaction
        tx?.takeUnless { it.isOwnedByMe }?.let { mine -> people.firstOrNull { it.profileId == mine.ownerProfileId }?.name ?: "a groupmate" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _type = MutableStateFlow("expense")
    val type: StateFlow<String> = _type.asStateFlow()

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _merchant = MutableStateFlow("")
    val merchant: StateFlow<String> = _merchant.asStateFlow()

    private val _category = MutableStateFlow("Food")
    val category: StateFlow<String> = _category.asStateFlow()

    private val _paymentMethod = MutableStateFlow("PopUPI")
    val paymentMethod: StateFlow<String> = _paymentMethod.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    private val _date = MutableStateFlow(System.currentTimeMillis())
    val date: StateFlow<Long> = _date.asStateFlow()

    private val _groupId = MutableStateFlow<Long?>(null)
    val groupId: StateFlow<Long?> = _groupId.asStateFlow()

    private val _isSplit = MutableStateFlow(false)
    val isSplit: StateFlow<Boolean> = _isSplit.asStateFlow()

    private val _splitMode = MutableStateFlow(SplitMode.EQUAL)
    val splitMode: StateFlow<SplitMode> = _splitMode.asStateFlow()

    private val _paidByPersonId = MutableStateFlow(-1L) // -1 is Me
    val paidByPersonId: StateFlow<Long> = _paidByPersonId.asStateFlow()

    private val _shares = MutableStateFlow<List<ShareInput>>(emptyList())
    val shares: StateFlow<List<ShareInput>> = _shares.asStateFlow()

    // Dialogs & Modals
    private val _selectedPersonForLedger = MutableStateFlow<PersonEntity?>(null)
    val selectedPersonForLedger: StateFlow<PersonEntity?> = _selectedPersonForLedger.asStateFlow()

    private val _isSettleUpOpen = MutableStateFlow(false)
    val isSettleUpOpen: StateFlow<Boolean> = _isSettleUpOpen.asStateFlow()
    private val _settleUpPerson = MutableStateFlow<PersonEntity?>(null)
    val settleUpPerson: StateFlow<PersonEntity?> = _settleUpPerson.asStateFlow()

    /** What the friend on the settle sheet owes you, line by line, for asking them to pay (ask-to-pay). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val settleUpHisaab: StateFlow<HisaabItemiser.Hisaab?> = _settleUpPerson
        .flatMapLatest { person -> person?.let { p -> ledgerRepo.getLedgerForPerson(p.id).map { HisaabItemiser.itemise(it) } } ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Friends you've asked to pay you back and who still owe you: person id to when you last asked. */
    val askedAt: StateFlow<Map<Long, Long>> = ledgerRepo.currentAsks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _isIouOpen = MutableStateFlow(false)
    val isIouOpen: StateFlow<Boolean> = _isIouOpen.asStateFlow()

    private val _isSimplifyDebtsOpen = MutableStateFlow(false)
    val isSimplifyDebtsOpen: StateFlow<Boolean> = _isSimplifyDebtsOpen.asStateFlow()

    private val _simplifiedDebts = MutableStateFlow<List<SimplifiedDebt>>(emptyList())
    val simplifiedDebts: StateFlow<List<SimplifiedDebt>> = _simplifiedDebts.asStateFlow()

    private val _isAddFriendOpen = MutableStateFlow(false)
    val isAddFriendOpen: StateFlow<Boolean> = _isAddFriendOpen.asStateFlow()

    // Group Modals
    private val _isCreateGroupOpen = MutableStateFlow(false)
    val isCreateGroupOpen: StateFlow<Boolean> = _isCreateGroupOpen.asStateFlow()

    private val _selectedGroupIdForDetail = MutableStateFlow<Long?>(null)
    // Follows live data: a sync can add members or rename, archive or remove the open group.
    val selectedGroupForDetail: StateFlow<GroupWithDetails?> = combine(_selectedGroupIdForDetail, groups) { id, all ->
        id?.let { selected -> all.firstOrNull { it.group.id == selected } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Shown at the top of the quick-add sheet, e.g. when a bill couldn't be read. */
    private val _parseNotice = MutableStateFlow<String?>(null)
    val parseNotice: StateFlow<String?> = _parseNotice.asStateFlow()

    fun showMessage(text: String) {
        _statusMessage.value = text
    }


    // Statement Import State
    private val _isImportStatementOpen = MutableStateFlow(false)
    val isImportStatementOpen: StateFlow<Boolean> = _isImportStatementOpen.asStateFlow()

    private val _isParsingStatement = MutableStateFlow(false)
    val isParsingStatement: StateFlow<Boolean> = _isParsingStatement.asStateFlow()

    private val _statementParseResult = MutableStateFlow<StatementParseResult?>(null)
    val statementParseResult: StateFlow<StatementParseResult?> = _statementParseResult.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setTab(tab: Int) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFilter(filter: String) {
        _selectedFilter.value = filter
    }

    // The defaults chosen in Categories & payment methods (UPI and Food out of the box), falling back
    // to the first choice still offered if the chosen one has since been hidden. See Categories.
    private fun defaultCategory(type: String): String = Categories.defaultCategory(type, categoryPrefs.value)

    private fun defaultPaymentMethod(): String = Categories.defaultPaymentMethod(categoryPrefs.value)

    fun openManualAdd(defaultType: String = "expense") {
        _parseNotice.value = null
        _editingTransactionId.value = null
        lockedShares = null
        _splitLocked.value = false
        _type.value = defaultType
        _amount.value = ""
        _merchant.value = ""
        _category.value = defaultCategory(defaultType)
        _paymentMethod.value = defaultPaymentMethod()
        _note.value = ""
        _date.value = System.currentTimeMillis()
        _groupId.value = null
        _isSplit.value = false
        _splitMode.value = SplitMode.EQUAL
        _paidByPersonId.value = -1L
        initSharesForCurrentPeople()
        _isConfirmSheetOpen.value = true
    }

    fun openManualAddForGroup(targetGroupId: Long) {
        // A notice left from an earlier bill that couldn't be read doesn't belong on a fresh entry.
        _parseNotice.value = null
        _editingTransactionId.value = null
        lockedShares = null
        _splitLocked.value = false
        _type.value = "expense"
        _amount.value = ""
        _merchant.value = ""
        _category.value = defaultCategory("expense")
        _paymentMethod.value = defaultPaymentMethod()
        _note.value = ""
        _date.value = System.currentTimeMillis()
        _groupId.value = targetGroupId
        _isSplit.value = true
        _splitMode.value = SplitMode.EQUAL
        _paidByPersonId.value = -1L

        // Initialize shares with members of this group
        val grp = groups.value.find { it.group.id == targetGroupId }
        val memberIds = grp?.members?.map { it.id }?.toSet() ?: emptySet()
        val shareInputs = mutableListOf<ShareInput>()
        shareInputs.add(ShareInput(personId = -1L, personName = "You", isSelected = true))
        candidatesFor(targetGroupId).forEach { person ->
            val isMember = memberIds.contains(person.id)
            shareInputs.add(ShareInput(personId = person.id, personName = person.name, isSelected = isMember, isGuest = isGuestIn(targetGroupId, person)))
        }
        _shares.value = shareInputs
        recalculateShares()
        _isConfirmSheetOpen.value = true
    }

    fun openEditTransaction(fullTx: FullTransaction) {
        val tx = fullTx.transaction
        lockedShares = null
        _splitLocked.value = false
        // Whoever added a group expense or paid for it can edit it (item 11).
        if (!fullTx.canEdit) {
            val owner = people.value.firstOrNull { it.profileId == tx.ownerProfileId }?.name ?: "the member who added it"
            val payer = fullTx.split?.paidByPersonId?.let { id -> people.value.firstOrNull { it.id == id }?.name }
            _statusMessage.value = if (payer != null && payer != owner) {
                "Only $owner, who added it, or $payer, who paid, can edit this expense."
            } else {
                "Only $owner can edit this expense."
            }
            return
        }
        rawGroups.value.firstOrNull { it.id == tx.groupId && it.isShared && it.isArchived }?.let { group ->
            _statusMessage.value = archivedMessage(ArchivedGroupException(group.name))
            return
        }
        _editingTransactionId.value = tx.id
        _type.value = tx.type
        _amount.value = if (tx.amount % 1.0 == 0.0) tx.amount.toLong().toString() else tx.amount.toString()
        _merchant.value = tx.merchant
        _category.value = tx.category
        _paymentMethod.value = tx.paymentMethod
        _note.value = tx.note
        _date.value = tx.date
        _groupId.value = tx.groupId
        _isSplit.value = tx.isSplit

        if (fullTx.split != null) {
            _paidByPersonId.value = fullTx.split.paidByPersonId
            _splitMode.value = when (fullTx.split.splitType) {
                "exact" -> SplitMode.EXACT
                "percentage" -> SplitMode.PERCENTAGE
                else -> SplitMode.EQUAL
            }

            // Guests I host come back as the friends they are; someone else's guests can't be
            // changed here, so their split is kept exactly as it is (see lockedShares).
            val currentPeople = candidatesFor(tx.groupId, includeGuests = tx.isOwnedByMe)
            val existingShareMap = fullTx.shares.filterNot { it.isGuest && it.guestPersonId == null }
                .associateBy { if (it.isGuest) it.guestPersonId!! else it.personId }
            lockedShares = fullTx.shares.takeIf { shares -> !tx.isOwnedByMe && shares.any { it.isGuest } }
            _splitLocked.value = lockedShares != null

            val shareInputs = mutableListOf<ShareInput>()
            val myShare = existingShareMap[-1L]
            shareInputs.add(
                ShareInput(
                    personId = -1L,
                    personName = "You",
                    amount = myShare?.shareAmount ?: 0.0,
                    percentage = myShare?.percentage ?: 0.0,
                    isSelected = myShare != null
                )
            )

            currentPeople.forEach { person ->
                val pShare = existingShareMap[person.id]
                shareInputs.add(
                    ShareInput(
                        personId = person.id,
                        personName = person.name,
                        amount = pShare?.shareAmount ?: 0.0,
                        percentage = pShare?.percentage ?: 0.0,
                        isSelected = pShare != null,
                        isGuest = isGuestIn(tx.groupId, person),
                    )
                )
            }
            _shares.value = shareInputs
        } else {
            _paidByPersonId.value = -1L
            _splitMode.value = SplitMode.EQUAL
            initSharesForCurrentPeople()
        }

        editBaseline = currentDraft()
        _isConfirmSheetOpen.value = true
    }

    // The expense as it was when its edit opened, to tell whether closing the sheet loses changes.
    private var editBaseline: EntryDraft? = null

    private fun currentDraft() = EntryDraft(
        type = _type.value,
        amount = _amount.value,
        merchant = _merchant.value,
        category = _category.value,
        paymentMethod = _paymentMethod.value,
        note = _note.value,
        date = _date.value,
        groupId = _groupId.value,
        isSplit = _isSplit.value,
        splitMode = _splitMode.value,
        paidByPersonId = _paidByPersonId.value,
        shares = _shares.value,
    )

    /** Whether closing the quick-add sheet now would lose what's in it. */
    fun hasUnsavedEntry(): Boolean =
        EntryDraft.isUnsaved(currentDraft(), original = editBaseline.takeIf { _editingTransactionId.value != null })

    fun parseInputText(rawText: String) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            _isParsing.value = true
            _parseNotice.value = null
            val friendNames = people.value.map { it.name }
            val outcome = geminiRepo.parseText(rawText, friendNames, parseChoices())
            applyParsedResult(outcome.result)
            // No method said: this account's own default, not "Other".
            if (outcome.result.paymentMethod.isBlank()) _paymentMethod.value = defaultPaymentMethod()
            // Why it's a guess, when it is: offline, out of quota and a refused key each say so.
            _parseNotice.value = outcome.failure?.quickAddNotice()
            _isParsing.value = false
            _isConfirmSheetOpen.value = true
        }
    }

    fun parseReceiptImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _isParsing.value = true
            _isConfirmSheetOpen.value = true
            when (val read = geminiRepo.readReceipt(bitmap, parseChoices())) {
                is ReceiptReadResult.Read -> {
                    applyParsedResult(read.entry)
                    _parseNotice.value = if (read.missingAmount) "Read the bill, but couldn't find the total. Fill in the amount." else null
                }
                is ReceiptReadResult.Failed -> {
                    applyParsedResult(ParsedTransactionResult(type = "expense", amount = 0.0, merchant = "", category = "Other", paymentMethod = "Other", note = "", readFailed = true))
                    // "Try a clearer photo" was the answer to being offline too.
                    _parseNotice.value = read.reason.receiptNotice()
                }
            }
            _isParsing.value = false
        }
    }

    fun handleSharedImageUri(context: Context, uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            if (bitmap != null) {
                parseReceiptImage(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _statusMessage.value = "Couldn't open that image."
        }
    }

    fun parseBankOrUpiStatement(pdfBytes: ByteArray, fileName: String = "statement.pdf") {
        viewModelScope.launch {
            _isParsingStatement.value = true
            _isImportStatementOpen.value = true
            val result = geminiRepo.readStatementPdf(pdfBytes, fileName, parseChoices())
            _isParsingStatement.value = false
            when (result) {
                is StatementReadResult.Success -> _statementParseResult.value = result.statement
                is StatementReadResult.Failed -> {
                    _isImportStatementOpen.value = false
                    _statusMessage.value = statementFailureMessage(result.reason)
                }
            }
        }
    }

    /**
     * One message per reason. The old single line ("check your connection") was wrong for most of
     * them and left the person with nothing to act on.
     */
    private fun statementFailureMessage(reason: StatementReadFailure): String = when (reason) {
        StatementReadFailure.NoKey -> "Statement reading isn't set up in this build."
        StatementReadFailure.NotAPdf -> "That file isn't a PDF. Export your statement as a PDF and try again."
        StatementReadFailure.PasswordProtected -> "That statement is password-protected. Open it, save an unlocked copy, and pick that one."
        StatementReadFailure.TooLarge -> "That statement is too big to read in one go. Export a shorter date range and try again."
        StatementReadFailure.Timeout -> "That statement took too long to read. A shorter date range usually goes through."
        StatementReadFailure.Offline -> "Couldn't reach the internet. Try again once you're back online."
        StatementReadFailure.Rejected -> "Couldn't read that statement right now. Try again in a minute."
        StatementReadFailure.RateLimited -> "Too many reads in a short while. Wait a minute, then try that statement again."
        StatementReadFailure.KeyRefused -> "Statement reading isn't available in this copy of the app right now. It's a setup problem on our side."
        StatementReadFailure.Blocked -> "Google declined to read that statement. Try exporting it again, or a shorter date range."
        StatementReadFailure.Truncated -> "That statement has more rows than fit in one read. Export a shorter date range and try again."
        StatementReadFailure.NothingFound -> "No transactions found in that PDF. Scanned or image-only statements can't be read."
        StatementReadFailure.Unreadable -> "Couldn't read that statement. Try again, or export it again from your bank or UPI app."
    }

    fun handleSharedPdfUri(context: Context, uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: ByteArray(0)
            inputStream?.close()
            if (bytes.isNotEmpty()) {
                parseBankOrUpiStatement(bytes, "statement.pdf")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _statusMessage.value = "Couldn't open that PDF."
        }
    }

    fun dismissStatementImportDialog() {
        _isImportStatementOpen.value = false
        _statementParseResult.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun confirmImportStatementTransactions(items: List<StatementTransactionItem>) {
        if (items.isEmpty()) {
            dismissStatementImportDialog()
            return
        }

        viewModelScope.launch {
            val entities = items.map { item ->
                TransactionEntity(
                    id = 0,
                    type = item.type,
                    amount = item.amount,
                    category = item.category,
                    merchant = item.merchant,
                    paymentMethod = item.paymentMethod,
                    date = item.date,
                    note = item.note,
                    isSplit = false
                )
            }

            ledgerRepo.batchInsertTransactions(entities)
            dismissStatementImportDialog()
            _statusMessage.value = if (entities.size == 1) "Added 1 entry from the statement." else "Added ${entities.size} entries from the statement."
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    private fun applyParsedResult(parsed: ParsedTransactionResult) {
        _editingTransactionId.value = null
        lockedShares = null
        _splitLocked.value = false
        _type.value = parsed.type
        // Money.input, not Double.toString, which writes ₹1,00,00,000 as "1.0E7".
        _amount.value = if (parsed.amount > 0) Money.input(parsed.amount) else ""
        _merchant.value = parsed.merchant
        // Whatever the parser said, as one of this account's choices (the offline parser and older
        // prompts don't know custom categories).
        val choices = parseChoices()
        _category.value = choices.categoryFor(parsed.type, parsed.category)
        _paymentMethod.value = choices.paymentFor(parsed.paymentMethod)
        _note.value = parsed.note
        _date.value = parsed.date
        _groupId.value = null

        val currentPeople = people.value
        val shareInputs = mutableListOf<ShareInput>()
        shareInputs.add(ShareInput(personId = -1L, personName = "You", isSelected = true))

        var hasSplits = false
        currentPeople.forEach { person ->
            val isMentioned = parsed.splitWithPersonNames.any { it.equals(person.name, ignoreCase = true) }
            if (isMentioned) hasSplits = true
            shareInputs.add(
                ShareInput(
                    personId = person.id,
                    personName = person.name,
                    isSelected = isMentioned
                )
            )
        }

        _isSplit.value = hasSplits
        _splitMode.value = SplitMode.EQUAL
        _paidByPersonId.value = -1L
        _shares.value = shareInputs
        recalculateShares()
    }

    private fun initSharesForCurrentPeople() {
        val currentPeople = people.value
        val shareInputs = mutableListOf<ShareInput>()
        shareInputs.add(ShareInput(personId = -1L, personName = "You", isSelected = true))
        currentPeople.forEach { person ->
            shareInputs.add(ShareInput(personId = person.id, personName = person.name, isSelected = false))
        }
        _shares.value = shareInputs
    }

    fun updateAmount(newAmount: String) {
        _amount.value = newAmount
        recalculateShares()
    }

    fun updateMerchant(newMerchant: String) {
        _merchant.value = newMerchant
    }

    fun updateCategory(newCat: String) {
        _category.value = newCat
    }

    fun updatePaymentMethod(newMethod: String) {
        _paymentMethod.value = newMethod
    }

    fun updateNote(newNote: String) {
        _note.value = newNote
    }

    fun updateType(newType: String) {
        _type.value = newType
        val prefs = categoryPrefs.value
        val isIncomeCategory = Categories.isIncomeCategory(_category.value, prefs)
        if (newType == "income" && !isIncomeCategory) {
            _category.value = Categories.incomeCategories(prefs).first()
        } else if (newType == "expense" && isIncomeCategory) {
            // Back to spending: the chosen default, not whatever happens to be first in the list.
            _category.value = defaultCategory("expense")
        }
    }

    fun updateDate(newDate: Long) {
        _date.value = newDate
    }

    fun updateGroupId(newGroupId: Long?) {
        val wasShared = isSharedGroup(_groupId.value)
        _groupId.value = newGroupId
        if (wasShared || isSharedGroup(newGroupId)) {
            // Participants of a shared group are its members, and only them.
            val selected = _shares.value.filter { it.isSelected }.map { it.personId }.toSet()
            _shares.value = listOf(ShareInput(personId = -1L, personName = "You", isSelected = -1L in selected)) +
                candidatesFor(newGroupId).map { ShareInput(personId = it.id, personName = it.name, isSelected = it.id in selected, isGuest = isGuestIn(newGroupId, it)) }
            if (_paidByPersonId.value != -1L && _shares.value.none { it.personId == _paidByPersonId.value }) {
                _paidByPersonId.value = -1L
            }
            recalculateShares()
        }
    }

    private fun isSharedGroup(groupId: Long?): Boolean =
        groupId != null && rawGroups.value.firstOrNull { it.id == groupId }?.isShared == true

    /**
     * Who can take part in a split: a shared group's members, since everyone in a synced split must
     * be a member (decision 1); otherwise everyone, as before.
     */
    private fun candidatesFor(groupId: Long?, includeGuests: Boolean = true): List<PersonEntity> {
        if (!isSharedGroup(groupId)) return people.value
        val memberIds = groups.value.firstOrNull { it.group.id == groupId }?.members?.map { it.id }?.toSet().orEmpty()
        // Friends who aren't on the app can come along as my guests (design item 9), after the members.
        val guests = if (includeGuests) people.value.filter { it.profileId == null && it.id !in memberIds } else emptyList()
        return people.value.filter { it.id in memberIds } + guests
    }

    /** A friend with no account, in a shared group's expense: my guest. */
    private fun isGuestIn(groupId: Long?, person: PersonEntity): Boolean = isSharedGroup(groupId) && person.profileId == null

    /** Someone else's expense with guests in it: its shares as they are, sent back unchanged. */
    private var lockedShares: List<SplitShareEntity>? = null
    private val _splitLocked = MutableStateFlow(false)
    /** True while editing a groupmate's expense that has their guests in it: only they change the split. */
    val splitLocked: StateFlow<Boolean> = _splitLocked.asStateFlow()

    fun setIsSplit(split: Boolean) {
        _isSplit.value = split
        if (split) {
            val current = _shares.value.toMutableList()
            if (current.none { it.personId != -1L && it.isSelected }) {
                val firstFriend = current.firstOrNull { it.personId != -1L }
                if (firstFriend != null) {
                    val idx = current.indexOf(firstFriend)
                    current[idx] = firstFriend.copy(isSelected = true)
                    _shares.value = current
                }
            }
            recalculateShares()
        }
    }

    fun setSplitMode(mode: SplitMode) {
        _splitMode.value = mode
        recalculateShares()
    }

    fun setPaidByPersonId(id: Long) {
        _paidByPersonId.value = id
        // In a shared group the leftover paisa follow the payer.
        if (isSharedGroup(_groupId.value)) recalculateShares()
    }

    fun togglePersonShare(personId: Long) {
        val current = _shares.value.toMutableList()
        val index = current.indexOfFirst { it.personId == personId }
        if (index != -1) {
            val item = current[index]
            current[index] = item.copy(isSelected = !item.isSelected)
            _shares.value = current
            recalculateShares()
        }
    }

    fun updateExactShareAmount(personId: Long, exactAmount: Double) {
        val current = _shares.value.toMutableList()
        val index = current.indexOfFirst { it.personId == personId }
        if (index != -1) {
            val item = current[index]
            current[index] = item.copy(amount = exactAmount)
            _shares.value = current
        }
    }

    fun updateSharePercentage(personId: Long, pct: Double) {
        val total = _amount.value.toDoubleOrNull() ?: 0.0
        val current = _shares.value.toMutableList()
        val index = current.indexOfFirst { it.personId == personId }
        if (index != -1) {
            val item = current[index]
            current[index] = item.copy(
                percentage = pct,
                amount = (total * (pct / 100.0) * 100).roundToInt() / 100.0
            )
            _shares.value = current
        }
    }

    private fun recalculateShares() {
        val total = _amount.value.toDoubleOrNull() ?: 0.0
        val current = _shares.value.toMutableList()
        val selected = current.filter { it.isSelected }

        if (selected.isEmpty()) return

        if (isSharedGroup(_groupId.value) && _splitMode.value == SplitMode.EQUAL && total > 0) {
            // Paisa-exact, leftover on the payer (decision 6), so the shares always sync.
            val rounded = SplitRounding.equal(total, selected.map { it.personId }, _paidByPersonId.value)
            _shares.value = current.map { item ->
                rounded[item.personId]?.let { item.copy(amount = it, percentage = 100.0 / selected.size) }
                    ?: item.copy(amount = 0.0, percentage = 0.0)
            }
            return
        }

        when (_splitMode.value) {
            SplitMode.EQUAL -> {
                val perPerson = if (selected.isNotEmpty()) (total / selected.size * 100).roundToInt() / 100.0 else 0.0
                _shares.value = current.map { item ->
                    if (item.isSelected) {
                        item.copy(amount = perPerson, percentage = (100.0 / selected.size))
                    } else {
                        item.copy(amount = 0.0, percentage = 0.0)
                    }
                }
            }
            SplitMode.EXACT -> {
                _shares.value = current.map { item ->
                    if (item.isSelected && item.amount == 0.0) {
                        item.copy(amount = (total / selected.size * 100).roundToInt() / 100.0)
                    } else item
                }
            }
            SplitMode.PERCENTAGE -> {
                val equalPct = (100.0 / selected.size * 10).roundToInt() / 10.0
                _shares.value = current.map { item ->
                    if (item.isSelected) {
                        val pct = if (item.percentage > 0) item.percentage else equalPct
                        item.copy(
                            percentage = pct,
                            amount = (total * (pct / 100.0) * 100).roundToInt() / 100.0
                        )
                    } else item.copy(amount = 0.0, percentage = 0.0)
                }
            }
        }
    }

    fun dismissConfirmSheet() {
        _isConfirmSheetOpen.value = false
        _parseNotice.value = null
    }

    private val _splitGroupPrompt = MutableStateFlow<SharedRecordGroups.Options?>(null)
    /** Non-null while asking where a split with groupmates should go (see [ShareSplitPrompt]). */
    val splitGroupPrompt: StateFlow<SharedRecordGroups.Options?> = _splitGroupPrompt.asStateFlow()

    /** Where a settlement, IOU or split with these people could sync. */
    fun groupOptionsFor(personIds: Collection<Long>): SharedRecordGroups.Options =
        SharedRecordGroups.optionsFor(personIds, people.value, groups.value)

    fun shareSplitInGroup(groupId: Long) {
        _splitGroupPrompt.value = null
        updateGroupId(groupId)
        saveCurrentTransaction(askForGroup = false)
    }

    fun saveSplitOnThisPhone() {
        _splitGroupPrompt.value = null
        saveCurrentTransaction(askForGroup = false)
    }

    fun dismissSplitGroupPrompt() {
        _splitGroupPrompt.value = null
    }

    fun createGroupFromSplitPrompt() {
        _splitGroupPrompt.value = null
        openCreateSharedGroup()
    }

    fun saveCurrentTransaction(askForGroup: Boolean = true) {
        val total = _amount.value.toDoubleOrNull() ?: 0.0
        if (total <= 0) return
        // The sheet disables Save and says why; this keeps any other caller from saving it anyway.
        if (_isSplit.value) splitProblem(_amount.value, _splitMode.value, _shares.value)?.let {
            _statusMessage.value = it
            return
        }

        // A split with groupmates outside a shared group never reaches them (decision 1): ask first.
        if (askForGroup && _isSplit.value && !isSharedGroup(_groupId.value)) {
            val participants = _shares.value.filter { it.isSelected }.map { it.personId } + _paidByPersonId.value
            // A split can bring friends who aren't on the app along as my guests (design item 9).
            val options = SharedRecordGroups.optionsFor(participants, people.value, groups.value, splitPayerId = _paidByPersonId.value)
            if (options.involvesGroupmates) {
                _splitGroupPrompt.value = options
                return
            }
        }

        val merchantName = _merchant.value.ifBlank {
            if (_type.value == "income") "Income" else _category.value
        }

        val tx = TransactionEntity(
            id = _editingTransactionId.value ?: 0,
            type = _type.value,
            amount = total,
            category = _category.value,
            merchant = merchantName,
            paymentMethod = _paymentMethod.value,
            date = _date.value,
            note = _note.value,
            groupId = _groupId.value,
            isSplit = _isSplit.value
        )

        val selectedShares = _shares.value.filter { it.isSelected && it.amount > 0 }
        // Percentages like 33.3 x 3 leave the amounts a few paisa short; in a shared group that would
        // never sync, so the amounts are taken proportionally to the percentages instead.
        val sharedPercentageAmounts = if (
            _isSplit.value && isSharedGroup(_groupId.value) && _splitMode.value == SplitMode.PERCENTAGE &&
            selectedShares.isNotEmpty() && selectedShares.sumOf { it.percentage } > 0
        ) {
            SplitRounding.byWeights(total, selectedShares.associate { it.personId to it.percentage }, _paidByPersonId.value)
        } else null
        val locked = lockedShares
        if (locked != null && abs(total - locked.sumOf { it.shareAmount }) >= 0.005) {
            _statusMessage.value = "Guests are in this split, so only the member who added it can change the amount."
            return
        }
        val splitShareEntities = when {
            // Someone else's guests: the server only accepts them back exactly as they were.
            locked != null -> locked.map { it.copy(id = 0, splitId = 0) }
            _isSplit.value -> selectedShares.map {
                val amount = sharedPercentageAmounts?.get(it.personId) ?: it.amount
                if (it.isGuest) {
                    // Stored under me, the host, so every group balance counts it as mine to cover.
                    SplitShareEntity(splitId = 0, personId = -1L, shareAmount = amount, percentage = it.percentage, guestName = it.personName, guestPersonId = it.personId)
                } else {
                    SplitShareEntity(splitId = 0, personId = it.personId, shareAmount = amount, percentage = it.percentage)
                }
            }
            else -> emptyList()
        }

        viewModelScope.launch {
            try {
                if (_editingTransactionId.value != null && _editingTransactionId.value != 0L) {
                    ledgerRepo.updateTransactionWithSplits(
                        tx = tx,
                        paidByPersonId = _paidByPersonId.value,
                        shares = splitShareEntities,
                        splitType = _splitMode.value.name.lowercase(Locale.getDefault())
                    )
                } else {
                    ledgerRepo.saveTransactionWithSplits(
                        tx = tx,
                        paidByPersonId = _paidByPersonId.value,
                        shares = splitShareEntities,
                        splitType = _splitMode.value.name.lowercase(Locale.getDefault())
                    )
                }
            } catch (e: ArchivedGroupException) {
                _statusMessage.value = archivedMessage(e)
                return@launch
            }
            _isConfirmSheetOpen.value = false
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    private fun archivedMessage(e: ArchivedGroupException) =
        "${e.groupName} is archived, so its expenses and settlements are read-only."

    /** Takes the row away now; the ledger delete waits for the Undo snackbar to go. */
    fun deleteTransaction(txId: Long) {
        val tx = rawTransactions.value.firstOrNull { it.transaction.id == txId }?.transaction
        pendingDeletes.hide(txId)
        _undoPrompt.value = UndoPrompt(
            txId = txId,
            message = tx?.let { "Deleted ${it.merchant.ifBlank { it.category }}" } ?: "Expense deleted",
        )
    }

    fun clearUndoPrompt() {
        _undoPrompt.value = null
    }

    fun undoDelete(txId: Long) = pendingDeletes.undo(txId)

    fun commitDelete(txId: Long) = pendingDeletes.commit(txId)

    private suspend fun commitTransactionDelete(txId: Long) {
        try {
            ledgerRepo.deleteTransaction(txId)
        } catch (e: ArchivedGroupException) {
            _statusMessage.value = archivedMessage(e)
            return
        }
        // Keep the row hidden until the feed has caught up, so it doesn't blink back in between.
        withTimeoutOrNull(2_000) { ledgerRepo.fullTransactions.first { list -> list.none { it.transaction.id == txId } } }
        SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
    }

    override fun onCleared() {
        pendingDeletes.commitAll()
        super.onCleared()
    }

    // Groups Management
    private val _createGroupShared = MutableStateFlow(false)
    /** Whether the create-group dialog opens with "Share with others" already on. */
    val createGroupShared: StateFlow<Boolean> = _createGroupShared.asStateFlow()

    fun openCreateGroup() {
        _createGroupShared.value = false
        _isCreateGroupOpen.value = true
    }

    /** From a settlement, IOU or split that needs a shared group with someone. */
    fun openCreateSharedGroup() {
        _isSettleUpOpen.value = false
        _isIouOpen.value = false
        _createGroupShared.value = true
        _isCreateGroupOpen.value = true
    }

    fun dismissCreateGroup() {
        _isCreateGroupOpen.value = false
    }

    fun createNewGroup(
        name: String,
        description: String,
        emoji: String,
        colorHex: String,
        memberPersonIds: List<Long>,
        shared: Boolean = false,
    ) {
        viewModelScope.launch {
            if (shared) {
                ledgerRepo.createSharedGroup(name, description, emoji, colorHex)
            } else {
                ledgerRepo.createGroup(name, description, emoji, colorHex, memberPersonIds)
            }
            dismissCreateGroup()
            _statusMessage.value = if (shared) {
                "Created $name. Open it to invite people."
            } else {
                "Created $name."
            }
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    fun selectGroupForDetail(group: GroupWithDetails) {
        _selectedGroupIdForDetail.value = group.group.id
    }

    fun dismissGroupDetail() {
        _selectedGroupIdForDetail.value = null
    }

    fun deleteGroup(groupId: Long) {
        if (isSharedGroup(groupId)) {
            _statusMessage.value = "Shared groups can't be deleted. Open the group to archive or leave it."
            return
        }
        viewModelScope.launch {
            ledgerRepo.deleteGroup(groupId)
            dismissGroupDetail()
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    // Person & Ledger operations
    fun openPersonLedger(person: PersonEntity) {
        _selectedPersonForLedger.value = person
    }

    fun closePersonLedger() {
        _selectedPersonForLedger.value = null
    }

    fun openSettleUp(person: PersonEntity) {
        _settleUpPerson.value = person
        _isSettleUpOpen.value = true
    }

    fun dismissSettleUp() {
        _isSettleUpOpen.value = false
        _settleUpPerson.value = null
    }

    /**
     * Opens the share sheet with [message] and, when you have a UPI ID, your payment code for [amount]
     * (ask-to-pay). The ask is logged only if an app is picked (ask/AskSentReceiver).
     */
    fun askToPay(context: Context, person: PersonEntity, amount: Double, message: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val qr = upiPayee.value?.let { payee ->
                withContext(Dispatchers.IO) {
                    AskShare.writeQr(app, person.id, UpiLink.build(payee.upiId, payee.name, amount, note = "Kharcha Paani hisaab"))
                }
            }
            val firstName = person.name.trim().substringBefore(' ').ifEmpty { person.name }
            val chooser = AskShare.chooser(app, AskShare.sendIntent(message, qr), person.id, amount, userId, "Ask $firstName to pay")
            context.startActivity(chooser)
        }
    }

    fun recordSettlementPayment(fromPersonId: Long, toPersonId: Long, amount: Double, note: String, groupId: Long? = null) {
        viewModelScope.launch {
            try {
                ledgerRepo.recordSettlement(
                    fromPersonId = fromPersonId,
                    toPersonId = toPersonId,
                    amount = amount,
                    note = note,
                    groupId = groupId,
                    isIou = false
                )
            } catch (e: ArchivedGroupException) {
                _statusMessage.value = archivedMessage(e)
                return@launch
            }
            dismissSettleUp()
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    fun openIouDialog() {
        _isIouOpen.value = true
    }

    fun dismissIouDialog() {
        _isIouOpen.value = false
    }

    fun recordIou(fromPersonId: Long, toPersonId: Long, amount: Double, note: String, groupId: Long? = null) {
        viewModelScope.launch {
            try {
                ledgerRepo.recordSettlement(
                    fromPersonId = fromPersonId,
                    toPersonId = toPersonId,
                    amount = amount,
                    note = note,
                    groupId = groupId,
                    isIou = true
                )
            } catch (e: ArchivedGroupException) {
                _statusMessage.value = archivedMessage(e)
                return@launch
            }
            dismissIouDialog()
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    fun openSimplifyDebts() {
        val currentBalances = personBalances.value.associate { it.person.id to it.netBalance }
        val groupMembers = groups.value.map { details -> details.members.map { it.id }.toSet() }
        val simplified = DebtSimplifier.simplify(people.value, currentBalances, groupMembers)
        _simplifiedDebts.value = simplified
        _isSimplifyDebtsOpen.value = true
    }

    fun dismissSimplifyDebts() {
        _isSimplifyDebtsOpen.value = false
    }

    fun openAddFriend() {
        _isAddFriendOpen.value = true
    }

    fun dismissAddFriend() {
        _isAddFriendOpen.value = false
    }

    fun addNewFriend(name: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = ledgerRepo.addPerson(name, colorHex)
            if (_isConfirmSheetOpen.value) {
                val current = _shares.value.toMutableList()
                current.add(ShareInput(personId = newId, personName = name.trim(), isSelected = true))
                _shares.value = current
                recalculateShares()
            }
            dismissAddFriend()
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    fun deleteFriend(personId: Long) {
        viewModelScope.launch {
            ledgerRepo.deletePerson(personId)
            if (_selectedPersonForLedger.value?.id == personId) {
                _selectedPersonForLedger.value = null
            }
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    /**
     * Each Insights question's own result, and which one is on screen.
     *
     * One shared answer and one shared `failed` flag used to stand in for all three, so a failure
     * in any of them read as a failure of all of them. See [AskBoard].
     */
    private val _askBoard = MutableStateFlow(AskBoard())
    val askBoard: StateFlow<AskBoard> = _askBoard.asStateFlow()

    /** How many answers are left today (backlog item 16, cap of [AI_DAILY_CAP]). */
    val aiAsksLeft: StateFlow<Int> = settings.aiUsage
        .map { it.remaining(LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AI_DAILY_CAP)

    /** Whether asking is possible at all: no key in the build means the section isn't shown. */
    val aiAvailable: Boolean = geminiRepo.hasApiKey()

    private var askJob: Job? = null

    /**
     * Shows one of Insights' questions, asking it only if it hasn't been answered yet.
     *
     * Tapping back to a chip that already has an answer just shows it again: instant, and it
     * doesn't spend another of the day's asks on a question that has already been answered for
     * this month. Tapping one that failed retries it. Starting a new request cancels one already
     * running — no queue, because a queued answer lands under a chip nobody is looking at.
     */
    fun askInsights(prompt: AiPrompt) {
        if (!_askBoard.value.needsAsking(prompt)) {
            _askBoard.value = _askBoard.value.select(prompt)
            return
        }
        askJob?.cancel()
        askJob = viewModelScope.launch {
            val month = _insightsMonth.value
            if (!settings.aiUsage.first().canAsk(LocalDate.now())) {
                _askBoard.value = _askBoard.value.select(prompt)
                return@launch
            }
            _askBoard.value = _askBoard.value.asking(prompt)

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            val analytics = insightsAnalytics.value
            val transactions = rawTransactions.value
            val data = when (prompt) {
                AiPrompt.MonthInReview -> AiContext.monthInReview(
                    analytics = analytics,
                    days = DailySpend.of(transactions, month, today, zone),
                    month = month,
                )
                AiPrompt.Overspending -> trailingComparison(month, transactions, today, zone)
                AiPrompt.WhatToChange -> AiContext.whatToChange(
                    comparison = trailingComparison(month, transactions, today, zone),
                    subscriptions = subscriptions.value,
                    transactions = transactions,
                    month = month,
                    zone = zone,
                )
                // Not offered on Insights; it needs a group (owner decision, 2026-09-19).
                AiPrompt.TripReview -> ""
            }

            val result = geminiRepo.askForAnswer(prompt, MonthNames.possessive(month, today), data)
            // A request that never reached Google doesn't spend one of the day's asks.
            if (result.spendsAnAsk()) settings.recordAiAsk(today)
            // Dropped if another month was picked while it was being written: that month's board
            // was already cleared, and this answer is about the previous one.
            if (month != _insightsMonth.value) return@launch
            _askBoard.value = when (result) {
                is AiAskResult.Answer -> _askBoard.value.answered(prompt, result.text)
                is AiAskResult.Failed -> _askBoard.value.failed(prompt, result.reason)
            }
        }
    }

    /** This month's categories against each of the previous three, for the two history prompts. */
    private suspend fun trailingComparison(
        month: YearMonth,
        transactions: List<FullTransaction>,
        today: LocalDate,
        zone: ZoneId,
    ): String {
        val earliest = earliestInsightsMonth.value
        val previous = AiContext.trailingMonths(month, earliest).associateWith { past ->
            ledgerRepo.monthlyAnalyticsFor(past).first().categorySummaries
        }
        return AiContext.trailingComparison(insightsAnalytics.value.categorySummaries, previous, month, today = today)
    }

    /** The trip read for the open group sheet. Its own state, not the Insights board's. */
    private val _tripReview = MutableStateFlow<AskState>(AskState.Idle)
    val tripReview: StateFlow<AskState> = _tripReview.asStateFlow()

    /**
     * Its own job, separate from [askJob]: a trip read and an Insights answer are about different
     * things on different screens, and opening a group shouldn't cancel an answer being written.
     */
    private var tripJob: Job? = null

    /** Clears a trip read so the next group opened doesn't show the last one's. */
    fun clearTripReview() {
        tripJob?.cancel()
        _tripReview.value = AskState.Idle
    }

    /** Reads one group, start to finish. Group sheet only — it is about a group. */
    fun askTripReview(groupId: Long) {
        tripJob?.cancel()
        tripJob = viewModelScope.launch {
            // Nothing to read yet, so nothing to say: leaving the state alone keeps the button
            // where it is rather than flashing an error at someone who tapped a live button.
            val details = groups.value.firstOrNull { it.group.id == groupId } ?: return@launch
            if (!settings.aiUsage.first().canAsk(LocalDate.now())) {
                _tripReview.value = AskState.Failed()
                return@launch
            }
            _tripReview.value = AskState.Asking

            val zone = ZoneId.systemDefault()
            val data = AiContext.tripReview(
                details = details,
                shares = GroupInsights.memberShares(details.transactions, details.members),
                debts = GroupInsights.owesWhom(details.transactions, details.settlements, details.members),
                categories = GroupInsights.categoryTotals(details.transactions),
                settlements = details.settlements,
                members = details.members,
                zone = zone,
            )
            val result = geminiRepo.askForAnswer(AiPrompt.TripReview, "this group's", data)
            if (result.spendsAnAsk()) settings.recordAiAsk(LocalDate.now())
            _tripReview.value = when (result) {
                is AiAskResult.Answer -> AskState.Answered(result.text)
                is AiAskResult.Failed -> AskState.Failed(result.reason)
            }
        }
    }

    private val _isExportOpen = MutableStateFlow(false)
    val isExportOpen: StateFlow<Boolean> = _isExportOpen.asStateFlow()

    fun openExport() {
        _isExportOpen.value = true
    }

    fun closeExport() {
        _isExportOpen.value = false
    }

    /**
     * Shares the range as a .csv file. It used to go as the share's text, which apps like WhatsApp and
     * Gmail cut off after a screenful, so a whole ledger arrived as its last 10-20 rows.
     */
    /** One group's whole history as a spreadsheet, from its own screen. */
    fun exportGroupCsv(context: Context, groupId: Long) {
        exportCsv(context, ExportRange.Everything, groupId)
    }

    fun exportCsv(context: Context, range: ExportRange, groupId: Long? = null) {
        viewModelScope.launch {
            val csv = ledgerRepo.exportCsv(range, groupId = groupId)
            if (csv.rows == 0) {
                _statusMessage.value = if (groupId != null) "Nothing logged in that group to export." else "Nothing logged in that range to export."
                return@launch
            }
            val uri = writeExport(csv)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, csv.fileName)
                clipData = ClipData.newRawUri(csv.fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            _isExportOpen.value = false
            context.startActivity(Intent.createChooser(send, if (csv.rows == 1) "Export 1 entry" else "Export ${csv.rows} entries"))
        }
    }

    private val backupRepo = LocalBackupRepository(db)

    /**
     * Writes the local-only half of the ledger to a JSON file and hands it to the share sheet.
     *
     * Insurance, not routine: a normal app update keeps all of this. It is uninstalling, a new
     * phone, or a change of signing key that loses it. See [LocalBackup].
     */
    fun exportLocalBackup(context: Context) {
        viewModelScope.launch {
            // Settings are read here and handed in: the repository owns the database, not
            // DataStore, and the first version of this simply forgot to pass them — which is
            // exactly why every export came back with empty category lists.
            val backup = backupRepo.collect(
                appVersion = BuildConfig.VERSION_NAME,
                settings = settings.categoryPrefs(userId).first()
                    .toBackup(askBeforeDiscarding = settings.askBeforeDiscarding.first()),
            )
            if (backup.isEmpty) {
                _statusMessage.value = "Nothing on this phone that isn't already backed up to your account."
                return@launch
            }
            val fileName = LocalBackup.fileName(backup.exportedAt)
            val uri = writeToCache(fileName, LocalBackup.encode(backup))
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                clipData = ClipData.newRawUri(fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Back up ${backup.summary()}"))
        }
    }

    /** Reads a backup file the person picked and puts back whatever is missing. */
    fun importLocalBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (text == null) {
                _statusMessage.value = "Couldn't open that file."
                return@launch
            }
            when (val result = LocalBackup.decode(text)) {
                is BackupReadResult.Unreadable ->
                    _statusMessage.value = "That doesn't look like a Kharcha Paani backup."
                is BackupReadResult.TooNew ->
                    _statusMessage.value = "That backup was written by a newer version of the app. Update first."
                is BackupReadResult.Ok -> {
                    val report = backupRepo.restore(result.backup)
                    restoreSettings(result.backup.settings)
                    _statusMessage.value = report.copy(settingsRestored = !result.backup.settings.isEmpty).message()
                }
            }
        }
    }

    /** Category and payment-method choices live in DataStore, so they restore separately. */
    private suspend fun restoreSettings(saved: BackupSettings) {
        if (saved.isEmpty) return
        settings.updateCategoryPrefs(userId) { current ->
            current.copy(
                customExpense = (current.customExpense + saved.customCategories).distinct(),
                customIncome = (current.customIncome + saved.customIncomeCategories).distinct(),
                hiddenCategories = current.hiddenCategories + saved.hiddenCategories,
                customPaymentMethods = (current.customPaymentMethods + saved.customPaymentMethods).distinct(),
                hiddenPaymentMethods = current.hiddenPaymentMethods + saved.hiddenPaymentMethods,
            )
        }
        saved.askBeforeDiscarding?.let { settings.setAskBeforeDiscarding(it) }
    }

    private suspend fun writeToCache(fileName: String, content: String): Uri = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(content, Charsets.UTF_8) }
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    private suspend fun writeExport(csv: CsvFile): Uri = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        // One export at a time in the cache: the last one is replaced, not piled up.
        val dir = File(app.cacheDir, "exports").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, csv.fileName).apply { writeText(csv.content, Charsets.UTF_8) }
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    fun getPersonLedgerFlow(personId: Long): kotlinx.coroutines.flow.Flow<List<PersonLedgerItem>> {
        return ledgerRepo.getLedgerForPerson(personId)
    }

    companion object {
        fun factory(application: Application, db: AppDatabase, userId: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { MainViewModel(application, db, userId) } }
    }
}

/** Offline and no-key failures never reached Google, so they don't count against the daily cap. */
private fun AiAskResult.spendsAnAsk(): Boolean =
    this !is AiAskResult.Failed || reason !in setOf(AiFailure.NoKey, AiFailure.Offline)
