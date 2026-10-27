package com.vaibhav.kharchapaani.ui.viewmodel

import kotlinx.coroutines.CancellationException
import com.vaibhav.kharchapaani.diagnostics.CrashReporting
import com.vaibhav.kharchapaani.domain.receiptNotice
import com.vaibhav.kharchapaani.domain.quickAddNotice
import com.vaibhav.kharchapaani.domain.isSavableAmount
import com.vaibhav.kharchapaani.domain.NotificationAccess
import com.vaibhav.kharchapaani.domain.NotificationMoment
import com.vaibhav.kharchapaani.domain.NotificationOffer
import com.vaibhav.kharchapaani.domain.NotificationPrefs
import com.vaibhav.kharchapaani.domain.QuietHours
import com.vaibhav.kharchapaani.domain.LocalHeuristicParser
import com.vaibhav.kharchapaani.domain.AiFailure
import com.vaibhav.kharchapaani.domain.CalendarDay
import com.vaibhav.kharchapaani.domain.AiAskResult
import com.vaibhav.kharchapaani.data.repository.ReceiptReadResult
import com.vaibhav.kharchapaani.data.repository.ReceiptRepository
import com.vaibhav.kharchapaani.data.receipts.BillPhoto
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import android.app.Application
import android.content.ClipData
import android.content.Context
import com.vaibhav.kharchapaani.ask.AskShare
import com.vaibhav.kharchapaani.domain.HisaabItemiser
import com.vaibhav.kharchapaani.domain.UpiLink
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
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
import com.vaibhav.kharchapaani.data.repository.DuplicateFriendException
import com.vaibhav.kharchapaani.ui.components.splitProblem
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
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import com.vaibhav.kharchapaani.domain.StatementPdf
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
import com.vaibhav.kharchapaani.domain.SplitTargets
import com.vaibhav.kharchapaani.domain.recentSplitGroup
import com.vaibhav.kharchapaani.ui.components.NewSubscription
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.reminder.ReminderRule
import com.vaibhav.kharchapaani.reminder.ReminderScheduler
import com.vaibhav.kharchapaani.reminder.ReminderSettings
import com.vaibhav.kharchapaani.util.NotificationHelper
import com.vaibhav.kharchapaani.util.SharedFiles
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
    // Gemini goes through the gemini-proxy Edge Function with this account's session token.
    // Tests pass one whose client answers without a network.
    private val geminiRepo: GeminiRepository = GeminiRepository(
        accessToken = { SupabaseClientProvider.get(application).auth.currentSessionOrNull()?.accessToken },
    ),
) : AndroidViewModel(application) {

    private val _moneyConfirmations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val moneyConfirmations = _moneyConfirmations.asSharedFlow()

    private val _reviewMoments = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reviewMoments = _reviewMoments.asSharedFlow()

    private val ledgerRepo = LedgerRepository(db, onLocalTransactionChange = ::requestSync, selfProfileId = userId)

    private val submissions = Submissions(viewModelScope, onFailure = { _statusMessage.value = it })

    /**
     * A user action that writes or sends something, with the safety net (plan T1.8): anything it
     * didn't handle (a full disk, a ledger closed by signing out, a bug) is told to the person and
     * reported instead of taking the app down. Cancellation passes through.
     */
    private fun launchAction(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CrashReporting.recordProblem("A user action failed", e)
            _statusMessage.value = Submissions.failureMessage(e)
        }
    }
    /** Actions still writing, so their sheet can show its button as busy (see [Submissions]). */
    val submitting: StateFlow<Set<Submit>> = submissions.inFlight

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
        launchAction {
            db.withTransaction {
                db.syncDao().requeueFailedTransactions()
                db.syncDao().requeueFailedGroups()
                db.syncDao().requeueFailedSettlements()
            }
            requestSync()
        }
    }

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

    /** Times Android's notification dialog was asked for. Until read, as if asked out (never ask early). */
    val notificationPermissionAsks: StateFlow<Int> = settings.notificationPermissionAsks
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotificationAccess.MAX_SYSTEM_ASKS)

    /** Times the app offered by itself; until read, as if used up, so nothing is offered early. */
    val notificationOffersShown: StateFlow<Int> = settings.notificationOffersShown
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotificationOffer.MAX_OFFERS)

    val notificationPrefs: StateFlow<NotificationPrefs> = settings.notificationPrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotificationPrefs())

    private val _notificationMoment = MutableStateFlow<NotificationMoment?>(null)
    /**
     * Set right after something a notification would follow up on (a group joined or created, a
     * payment recorded that waits for the other person, the first own entry). The shell offers to
     * turn notifications on there, if Android would still ask; see [NotificationOffer].
     */
    val notificationMoment: StateFlow<NotificationMoment?> = _notificationMoment.asStateFlow()

    fun reachNotificationMoment(moment: NotificationMoment) {
        if (_notificationMoment.value == null) _notificationMoment.value = moment
    }

    /** The offer was shown (whatever the answer): it counts against the two. */
    fun notificationOfferShown() {
        _notificationMoment.value = null
        viewModelScope.launch { settings.recordNotificationOffer() }
    }

    fun clearNotificationMoment() {
        _notificationMoment.value = null
    }

    fun setPaymentsToConfirm(on: Boolean) {
        viewModelScope.launch { settings.setPaymentsToConfirm(on) }
    }

    fun setQuietHours(quietHours: QuietHours) {
        viewModelScope.launch { settings.setQuietHours(quietHours) }
    }

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

    fun recordNotificationPermissionAsk() {
        viewModelScope.launch { settings.recordNotificationPermissionAsk() }
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
        submissions.launch(Submit.AddSubscription) {
            ledgerRepo.saveSubscription(
                SubscriptionEntity(
                    name = new.name,
                    amount = new.amount,
                    category = new.category,
                    paymentMethod = new.paymentMethod,
                    cycle = new.cycle.name,
                    startDate = CalendarDay.toMillis(new.start),
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
        launchAction {
            ledgerRepo.cancelSubscription(id)
            _statusMessage.value = "Cancelled. Charges already logged stay."
        }
    }

    fun deleteSubscription(id: Long) {
        launchAction { ledgerRepo.deleteSubscription(id) }
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
        launchAction {
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
        launchAction {
            try {
                ledgerRepo.deleteSettlement(settlementId)
            } catch (e: ArchivedGroupException) {
                _statusMessage.value = archivedMessage(e)
                return@launchAction
            } catch (e: NotYoursToWithdrawException) {
                // Theirs to withdraw; mine to answer (design item 10).
                openSettlementAnswer(settlementId)
                return@launchAction
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
                recordedByName = settlement.recordedByPersonId?.let { id -> if (id == -1L) "You" else names[id] },
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
        launchAction {
            val before = db.groupSyncDao().settlementById(id)
            try {
                ledgerRepo.respondToSettlement(id, confirm, note)
            } catch (e: ArchivedGroupException) {
                _statusMessage.value = archivedMessage(e)
                return@launchAction
            }
            NotificationHelper.clearSettlementReminder(getApplication(), id)
            _statusMessage.value = if (confirm) "Confirmed. It counts for both of you now." else "Marked not right. It won't count."
            if (confirm && before?.status == SettlementStatus.PENDING) reviewAfterConfirmedSettlement(id)
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

    private var groupChoiceIsExplicit = false
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
        .flatMapLatest { person -> person?.let { p -> ledgerRepo.getLedgerForPerson(p.id, getApplication<Application>()).map { HisaabItemiser.itemise(it) } } ?: flowOf(null) }
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

    // Bill photos (docs/designs/bill-photos.md): kept on this phone, in this account's folder.
    private val receipts = ReceiptRepository(db, ReceiptStore.forAccount(application, userId))

    private val _billPhoto = MutableStateFlow<BillPhoto>(BillPhoto.None)
    /** The add sheet's bill photo; the ledger only changes when the expense saves. */
    val billPhoto: StateFlow<BillPhoto> = _billPhoto.asStateFlow()

    /** Whether the expense being edited had a photo when its sheet opened, so Save knows to remove it. */
    private var hadBillPhotoOnOpen = false
    private var billPhotoJob: Job? = null

    /** Expenses with a bill photo, for the feed's icon. */
    val billPhotoIds: StateFlow<Set<Long>> = receipts.transactionIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        // Photos whose expense went by a route that doesn't know about files (a sync pull, a
        // groupmate's delete), and photos picked but never saved.
        viewModelScope.launch {
            runCatching { receipts.sweepOrphans() }.onFailure { Log.w("MainViewModel", "Bill photo sweep failed", it) }
        }
    }

    /** A picture from the camera or the gallery for the bill photo row; [onRead] runs once it's read. */
    fun attachBillPhoto(context: Context, uri: Uri, onRead: () -> Unit = {}) {
        keepBillPhoto {
            SharedFiles.decodeImage(context.applicationContext, uri, ReceiptStore.MAX_SIDE).also { onRead() }
        }
    }

    fun removeBillPhoto() {
        billPhotoJob?.cancel()
        (_billPhoto.value as? BillPhoto.Kept)?.staged?.let(receipts.store::discard)
        _billPhoto.value = BillPhoto.None
    }

    /** Decodes and writes a new photo; until it's written the row shows it's working. */
    private fun keepBillPhoto(decode: suspend () -> Bitmap?) {
        billPhotoJob?.cancel()
        val previous = _billPhoto.value.takeIf { it != BillPhoto.Processing } ?: BillPhoto.None
        _billPhoto.value = BillPhoto.Processing
        billPhotoJob = viewModelScope.launch {
            val staged = decode()?.let { receipts.store.stage(it) }
            if (staged == null) {
                // The photo that was there stays; this one couldn't be read or written.
                _billPhoto.value = previous
                _statusMessage.value = "Couldn't use that photo."
                return@launch
            }
            (previous as? BillPhoto.Kept)?.staged?.let(receipts.store::discard)
            _billPhoto.value = BillPhoto.Kept(staged.file, staged)
        }
    }

    /** A fresh sheet: drop a photo picked and never saved. */
    private fun resetBillPhoto() {
        billPhotoJob?.cancel()
        billPhotoJob = null
        (_billPhoto.value as? BillPhoto.Kept)?.staged?.let(receipts.store::discard)
        _billPhoto.value = BillPhoto.None
        hadBillPhotoOnOpen = false
    }

    private fun loadBillPhoto(transactionId: Long) {
        // Not billPhotoJob: picking a new photo mustn't cancel learning that there was an old one.
        viewModelScope.launch {
            val (_, file) = receipts.photoOf(transactionId) ?: return@launch
            if (_editingTransactionId.value != transactionId || !_isConfirmSheetOpen.value) return@launch
            hadBillPhotoOnOpen = true
            if (_billPhoto.value == BillPhoto.None) {
                _billPhoto.value = if (file.exists()) BillPhoto.Kept(file, staged = null) else BillPhoto.Missing
            }
        }
    }

    /** After the expense saved: keep, replace or remove its photo to match the sheet. */
    private suspend fun settleBillPhoto(transactionId: Long) {
        when (val photo = _billPhoto.value) {
            is BillPhoto.Kept -> photo.staged?.let { staged ->
                if (!receipts.attach(transactionId, staged)) _statusMessage.value = "Saved. The bill photo couldn't be kept."
            }
            BillPhoto.None -> if (hadBillPhotoOnOpen) receipts.remove(transactionId)
            BillPhoto.Missing, BillPhoto.Processing -> Unit
        }
        _billPhoto.value = BillPhoto.None
        hadBillPhotoOnOpen = false
    }

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
        resetBillPhoto()
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
        groupChoiceIsExplicit = false
        _groupId.value = null
        _isSplit.value = false
        _splitMode.value = SplitMode.EQUAL
        _paidByPersonId.value = -1L
        initSharesForCurrentPeople()
        _isConfirmSheetOpen.value = true
    }

    fun openManualAddForGroup(targetGroupId: Long) {
        resetBillPhoto()
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
        groupChoiceIsExplicit = true
        _groupId.value = targetGroupId
        _isSplit.value = true
        _splitMode.value = SplitMode.EQUAL
        _paidByPersonId.value = -1L

        _shares.value = sharesForGroup(targetGroupId)
        recalculateShares()
        _isConfirmSheetOpen.value = true
    }

    /**
     * A group's split: you, then everyone who can take part. [only] narrows it to the people a quick
     * add named ("petrol 500 Goa trip with Rohan"); otherwise the group's members are ticked.
     */
    private fun sharesForGroup(groupId: Long, only: Set<Long> = emptySet()): List<ShareInput> {
        val memberIds = groups.value.find { it.group.id == groupId }?.members?.map { it.id }?.toSet().orEmpty()
        val ticked = only.ifEmpty { memberIds }
        return listOf(ShareInput(personId = -1L, personName = "You", isSelected = true)) +
            candidatesFor(groupId).map { person ->
                ShareInput(personId = person.id, personName = person.name, isSelected = person.id in ticked, isGuest = isGuestIn(groupId, person))
            }
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
        resetBillPhoto()
        _type.value = tx.type
        _amount.value = if (tx.amount % 1.0 == 0.0) tx.amount.toLong().toString() else tx.amount.toString()
        _merchant.value = tx.merchant
        _category.value = tx.category
        _paymentMethod.value = tx.paymentMethod
        _note.value = tx.note
        _date.value = tx.date
        groupChoiceIsExplicit = true
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
        // After the sheet is open: the load gives up on a sheet that has closed, and started any
        // earlier it could see this one as not open yet.
        loadBillPhoto(tx.id)
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
        EntryDraft.isUnsaved(currentDraft(), original = editBaseline.takeIf { _editingTransactionId.value != null }) ||
            billPhotoChanged()

    /** A new photo waiting, or the expense's photo removed: closing the sheet would lose it. */
    private fun billPhotoChanged(): Boolean = when (val photo = _billPhoto.value) {
        is BillPhoto.Kept -> photo.staged != null
        BillPhoto.Processing -> true
        BillPhoto.None -> hadBillPhotoOnOpen
        BillPhoto.Missing -> false
    }

    fun parseInputText(rawText: String) {
        if (rawText.isBlank()) return
        withAiNotice(insteadOfAi = { fillInOnPhone(rawText) }) { readTextWithAi(rawText) }
    }

    /** "Type it myself" on a quick add: the phone's own guess, and the sheet to check it in. */
    private fun fillInOnPhone(rawText: String) {
        resetBillPhoto()
        val splitNotice = applyParsedResult(
            LocalHeuristicParser.parse(rawText, people.value.map { it.name }).copy(date = System.currentTimeMillis()),
            rawText,
        )
        if (_paymentMethod.value.isBlank()) _paymentMethod.value = defaultPaymentMethod()
        _parseNotice.value = listOfNotNull("Filled in on your phone. Check the amount and details.", splitNotice).joinToString(" ")
        _isConfirmSheetOpen.value = true
    }

    private fun readTextWithAi(rawText: String) {
        viewModelScope.launch {
            _isParsing.value = true
            _parseNotice.value = null
            resetBillPhoto()
            try {
                val friendNames = people.value.map { it.name }
                val outcome = geminiRepo.parseText(rawText, friendNames, parseChoices())
                val splitNotice = applyParsedResult(outcome.result, rawText)
                // No method said: this account's own default, not "Other".
                if (outcome.result.paymentMethod.isBlank()) _paymentMethod.value = defaultPaymentMethod()
                // Why it's a guess, when it is: offline, out of quota and a refused key each say so.
                // Then any name in the line that didn't find its friend.
                _parseNotice.value = listOfNotNull(outcome.failure?.quickAddNotice(), splitNotice)
                    .joinToString(" ").ifEmpty { null }
            } finally {
                // Whatever happened, the sheet never stays stuck on "Reading…".
                _isParsing.value = false
            }
            _isConfirmSheetOpen.value = true
        }
    }

    fun parseReceiptImage(bitmap: Bitmap) {
        withAiNotice(insteadOfAi = {
            // Typed by hand, with the bill still kept beside it.
            openManualAdd()
            keepBillPhoto { bitmap }
        }) { readReceiptWithAi(bitmap) }
    }

    private fun readReceiptWithAi(bitmap: Bitmap) {
        // The picture that was read is kept with the expense by default; one tap on the row drops it.
        resetBillPhoto()
        keepBillPhoto { bitmap }
        viewModelScope.launch {
            _isParsing.value = true
            _isConfirmSheetOpen.value = true
            try {
                when (val read = geminiRepo.readReceipt(bitmap, parseChoices())) {
                    is ReceiptReadResult.Read -> {
                        applyParsedResult(read.entry)
                        _parseNotice.value = when {
                            read.missingAmount -> "Read the bill, but couldn't find the total. Fill in the amount."
                            _billPhoto.value != BillPhoto.None -> "Read from your bill. The photo is kept below."
                            else -> null
                        }
                    }
                    is ReceiptReadResult.Failed -> {
                        applyParsedResult(ParsedTransactionResult(type = "expense", amount = 0.0, merchant = "", category = "Other", paymentMethod = "Other", note = "", readFailed = true))
                        // "Try a clearer photo" was the answer to being offline too.
                        _parseNotice.value = read.reason.receiptNotice()
                    }
                }
            } finally {
                _isParsing.value = false
            }
        }
    }

    /** A picture shared into the app: read off the main thread and at a size the phone can hold. */
    fun handleSharedImageUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bitmap = SharedFiles.decodeImage(context.applicationContext, uri)
            if (bitmap != null) parseReceiptImage(bitmap) else _statusMessage.value = "Couldn't open that image."
        }
    }

    /** The statement being read, so Cancel (or Back on the reading dialog) can stop it. */
    private var statementJob: Job? = null

    fun parseBankOrUpiStatement(pdfBytes: ByteArray, fileName: String = "statement.pdf") {
        withAiNotice(insteadOfAi = {
            _statusMessage.value = "Statements are read by Gemini, so this one wasn't. You can still add entries yourself."
        }) { readStatementWithAi(pdfBytes, fileName) }
    }

    private fun readStatementWithAi(pdfBytes: ByteArray, fileName: String) {
        statementJob?.cancel()
        statementJob = viewModelScope.launch {
            _isParsingStatement.value = true
            _isImportStatementOpen.value = true
            val result = try {
                geminiRepo.readStatementPdf(pdfBytes, fileName, parseChoices())
            } finally {
                _isParsingStatement.value = false
            }
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
        StatementReadFailure.DailyLimit -> "That's today's statement reads used up. They're back tomorrow; you can still add entries by hand."
        StatementReadFailure.KeyRefused -> "Statement reading isn't available in this copy of the app right now. It's a setup problem on our side."
        StatementReadFailure.Blocked -> "Google declined to read that statement. Try exporting it again, or a shorter date range."
        StatementReadFailure.Truncated -> "That statement has more rows than fit in one read. Export a shorter date range and try again."
        StatementReadFailure.NothingFound -> "No transactions found in that PDF. Scanned or image-only statements can't be read."
        StatementReadFailure.Unreadable -> "Couldn't read that statement. Try again, or export it again from your bank or UPI app."
    }

    /** A PDF shared into the app, read only up to the size a statement can be. */
    fun handleSharedPdfUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            when (val read = SharedFiles.readBytes(context.applicationContext, uri, StatementPdf.MAX_BYTES)) {
                is SharedFiles.Read.Bytes -> parseBankOrUpiStatement(read.bytes, "statement.pdf")
                SharedFiles.Read.TooLarge -> _statusMessage.value = statementFailureMessage(StatementReadFailure.TooLarge)
                SharedFiles.Read.Unreadable -> _statusMessage.value = "Couldn't open that PDF."
            }
        }
    }

    /**
     * Closes the statement import, and stops a reading still under way: its answer is dropped
     * rather than opening the list later. Back on the reading dialog used to do nothing for the
     * minutes a long statement takes.
     */
    fun dismissStatementImportDialog() {
        statementJob?.cancel()
        statementJob = null
        _isParsingStatement.value = false
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

        submissions.launch(Submit.ImportStatement) {
            // A row the statement reader got wrong (no amount, or more than the server stores) is
            // left out rather than refusing the whole import.
            val entities = items.filter { isSavableAmount(it.amount) }.map { item ->
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
            if (entities.isNotEmpty()) _moneyConfirmations.tryEmit(Unit)
            dismissStatementImportDialog()
            _statusMessage.value = if (entities.size == 1) "Added 1 entry from the statement." else "Added ${entities.size} entries from the statement."
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    /**
     * Fills the sheet from a parsed line. [rawText] is what the person typed or said, where a group
     * name is looked for ("petrol 500 Goa trip"). Returns what to tell them about the split when a
     * name in the line didn't find its friend, or null.
     */
    private fun applyParsedResult(parsed: ParsedTransactionResult, rawText: String? = null): String? {
        _editingTransactionId.value = null
        lockedShares = null
        _splitLocked.value = false
        _type.value = parsed.type
        // Money.input, not Double.toString, which writes ₹1,00,00,000 as "1.0E7".
        _amount.value = if (isSavableAmount(parsed.amount)) Money.input(parsed.amount) else ""
        _merchant.value = parsed.merchant
        // Whatever the parser said, as one of this account's choices (the offline parser and older
        // prompts don't know custom categories).
        val choices = parseChoices()
        _category.value = choices.categoryFor(parsed.type, parsed.category)
        _paymentMethod.value = choices.paymentFor(parsed.paymentMethod)
        _note.value = parsed.note
        _date.value = parsed.date

        val target = SplitTargets.resolve(rawText.orEmpty(), parsed.splitWithPersonNames, people.value, rawGroups.value)
        val groupId = target.groupId
        groupChoiceIsExplicit = groupId != null
        _groupId.value = groupId
        _shares.value = if (groupId != null) {
            sharesForGroup(groupId, only = target.personIds)
        } else {
            listOf(ShareInput(personId = -1L, personName = "You", isSelected = true)) +
                people.value.map { ShareInput(personId = it.id, personName = it.name, isSelected = it.id in target.personIds) }
        }
        _isSplit.value = _shares.value.any { it.personId != -1L && it.isSelected }
        _splitMode.value = SplitMode.EQUAL
        _paidByPersonId.value = -1L
        recalculateShares()

        // Named, found, but not someone who can be in this group's split (a member elsewhere).
        val leftOut = target.personIds.filter { id -> _shares.value.none { it.personId == id } }
            .mapNotNull { id -> people.value.firstOrNull { it.id == id }?.name }
        val groupName = groupId?.let { id -> rawGroups.value.firstOrNull { it.id == id }?.name }
        return SplitTargets.notice(target, leftOut, groupName)
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
        groupChoiceIsExplicit = true
        changeGroupId(newGroupId)
    }

    private fun changeGroupId(newGroupId: Long?) {
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
            suggestRecentGroup()
            recalculateShares()
        }
    }

    private fun suggestRecentGroup() {
        if (groupChoiceIsExplicit || _editingTransactionId.value != null ||
            !_isSplit.value || _type.value != "expense") return
        val selected = _shares.value.filter { it.isSelected }
        val friend = selected.filter { it.personId != -1L }.singleOrNull()
            ?.let { share -> people.value.firstOrNull { it.id == share.personId } }
        val suggested = if (selected.any { it.personId == -1L } && friend != null) {
            recentSplitGroup(groups.value, friend)
        } else null
        if (suggested != _groupId.value) changeGroupId(suggested)
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
        val total = Money.parseInput(_amount.value) ?: 0.0
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
        val total = Money.parseInput(_amount.value) ?: 0.0
        val current = _shares.value.toMutableList()
        val selected = current.filter { it.isSelected }

        if (selected.isEmpty()) return

        if (_splitMode.value == SplitMode.EQUAL && total > 0) {
            // Paisa-exact, leftover on the payer (decision 6): a shared group's shares always sync, and
            // anyone else's add up too (₹500 over three is 166.66, 166.66 and the payer's 166.68).
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
        resetBillPhoto()
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
        val total = Money.parseInput(_amount.value) ?: 0.0
        if (total <= 0) return
        // Income has no split: the sheet hides the editor, but the toggle stayed on, and the hidden
        // shares were saved as friends owing me (correctness audit, 2026-10-24).
        val splitting = _isSplit.value && _type.value != "income"
        // The sheet disables Save and says why; this keeps any other caller from saving it anyway.
        if (splitting) splitProblem(getApplication<Application>(), _amount.value, _splitMode.value, _shares.value)?.let {
            _statusMessage.value = it
            return
        }

        // A split with groupmates outside a shared group never reaches them (decision 1): ask first.
        if (askForGroup && splitting && !isSharedGroup(_groupId.value)) {
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
            isSplit = splitting
        )

        val selectedShares = _shares.value.filter { it.isSelected && it.amount > 0 }
        // Percentages like 33.3 x 3 leave the amounts short (10 paisa on ₹100, ₹1 on ₹1000), so the
        // amounts are taken proportionally to the percentages instead. Every split, not only a shared
        // group's: since 1.3 a split with friends who aren't on the app syncs too, and the server
        // refuses shares that don't add up, so that one stayed FAILED and never left the phone
        // (correctness audit, 2026-10-24). Owed by nobody, the gap was wrong locally as well.
        val sharedPercentageAmounts = if (
            splitting && _splitMode.value == SplitMode.PERCENTAGE &&
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
            splitting -> selectedShares.map {
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

        submissions.launch(Submit.SaveEntry) {
            // A photo still being written goes with the expense rather than being dropped.
            billPhotoJob?.join()
            val savedId = try {
                val editingId = _editingTransactionId.value
                if (editingId != null && editingId != 0L) {
                    ledgerRepo.updateTransactionWithSplits(
                        tx = tx,
                        paidByPersonId = _paidByPersonId.value,
                        shares = splitShareEntities,
                        splitType = _splitMode.value.name.lowercase(Locale.getDefault())
                    )
                    editingId
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
            settleBillPhoto(savedId)
            _isConfirmSheetOpen.value = false
            _moneyConfirmations.tryEmit(Unit)
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
        val photos = try {
            // Read first: the rows go with the expense (cascade), and with them the file names.
            receipts.fileNamesOf(txId).also { ledgerRepo.deleteTransaction(txId) }
        } catch (e: ArchivedGroupException) {
            _statusMessage.value = archivedMessage(e)
            return
        } catch (e: IllegalStateException) {
            // Signing out while the Undo bar was up: onCleared commits this after the account's
            // database is closed, and the uncaught error took the app down (correctness audit,
            // 2026-10-24). The expense stays; it can be deleted again after signing back in.
            Log.w("MainViewModel", "Delete not committed: the ledger was closed", e)
            return
        }
        receipts.deleteFiles(photos)
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
        submissions.launch(Submit.CreateGroup) {
            if (shared) {
                ledgerRepo.createSharedGroup(name, description, emoji, colorHex)
            } else {
                ledgerRepo.createGroup(name, description, emoji, colorHex, memberPersonIds)
            }
            dismissCreateGroup()
            if (shared) reachNotificationMoment(NotificationMoment.CreatedGroup)
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
        launchAction {
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
        launchAction {
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
        submissions.launch(Submit.RecordSettlement) {
            val settlementId = try {
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
            _moneyConfirmations.tryEmit(Unit)
            // In a shared group it waits for the other person's answer, and theirs for mine.
            if (isSharedGroup(groupId)) reachNotificationMoment(NotificationMoment.RecordedPayment)
            reviewAfterConfirmedSettlement(settlementId)
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    private suspend fun reviewAfterConfirmedSettlement(id: Long) {
        val saved = db.groupSyncDao().settlementById(id) ?: return
        if (saved.isConfirmed && !saved.isIou && (saved.fromPersonId == -1L || saved.toPersonId == -1L)) {
            _reviewMoments.tryEmit(Unit)
        }
    }

    fun openIouDialog() {
        _isIouOpen.value = true
    }

    fun dismissIouDialog() {
        _isIouOpen.value = false
    }

    fun recordIou(fromPersonId: Long, toPersonId: Long, amount: Double, note: String, groupId: Long? = null) {
        submissions.launch(Submit.RecordIou) {
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
        submissions.launch(Submit.AddFriend) {
            val newId = try {
                ledgerRepo.addPerson(name, colorHex)
            } catch (e: DuplicateFriendException) {
                // The sheet stays open so the name can be told apart ("Rahul K").
                _statusMessage.value = "You already have a friend called ${e.existingName}. Add something to tell them apart, like an initial."
                return@launch
            }
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
        launchAction {
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

    // The one-time notice before anything goes to Gemini (plan T1.10, decision D2).
    private val aiNoticeAccepted: StateFlow<Boolean> = settings.aiNoticeAccepted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _isAiNoticeOpen = MutableStateFlow(false)
    val isAiNoticeOpen: StateFlow<Boolean> = _isAiNoticeOpen.asStateFlow()
    private var afterAiNotice: (() -> Unit)? = null
    private var insteadOfAi: (() -> Unit)? = null

    /**
     * Runs [withAi] if what it sends to Gemini is covered: the notice accepted, or no key in this
     * build (then nothing leaves the phone). Otherwise shows the notice first; "Type it myself" runs
     * [insteadOfAi], which never sends anything.
     */
    private fun withAiNotice(insteadOfAi: () -> Unit, withAi: () -> Unit) {
        if (!aiAvailable || aiNoticeAccepted.value) return withAi()
        afterAiNotice = withAi
        this.insteadOfAi = insteadOfAi
        _isAiNoticeOpen.value = true
    }

    fun acceptAiNotice() {
        val next = afterAiNotice
        closeAiNotice()
        viewModelScope.launch { settings.setAiNoticeAccepted(true) }
        next?.invoke()
    }

    fun declineAiNotice() {
        val instead = insteadOfAi
        closeAiNotice()
        instead?.invoke()
    }

    private fun closeAiNotice() {
        _isAiNoticeOpen.value = false
        afterAiNotice = null
        insteadOfAi = null
    }

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
        // Picking an answer already in hand sends nothing, so it needs no notice.
        if (!_askBoard.value.needsAsking(prompt)) return askInsightsNow(prompt)
        withAiNotice(insteadOfAi = {}) { askInsightsNow(prompt) }
    }

    private fun askInsightsNow(prompt: AiPrompt) {
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
        withAiNotice(insteadOfAi = {}) { askTripReviewNow(groupId) }
    }

    private fun askTripReviewNow(groupId: Long) {
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
        launchAction {
            val csv = ledgerRepo.exportCsv(range, groupId = groupId)
            if (csv.rows == 0) {
                _statusMessage.value = if (groupId != null) "Nothing logged in that group to export." else "Nothing logged in that range to export."
                return@launchAction
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
        launchAction {
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
                return@launchAction
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

    private val _restorePreview = MutableStateFlow<LocalBackup?>(null)
    /** A backup that read and checked out, waiting for the person to confirm the restore. */
    val restorePreview: StateFlow<LocalBackup?> = _restorePreview.asStateFlow()

    /**
     * Reads a backup file the person picked and, if it checks out, asks before restoring it.
     *
     * Read off the main thread and no further than [BACKUP_MAX_BYTES]: the file is whatever someone
     * picked, and readText() on a huge one froze or crashed the app. It used to be applied the moment
     * it was picked, with no way to see what it held (security audit, 2026-10-24).
     */
    fun importLocalBackup(context: Context, uri: Uri) {
        launchAction {
            val text = when (val read = SharedFiles.readBytes(context.applicationContext, uri, BACKUP_MAX_BYTES)) {
                is SharedFiles.Read.Bytes -> read.bytes.toString(Charsets.UTF_8)
                SharedFiles.Read.TooLarge -> {
                    _statusMessage.value = "That file is too big to be a Kharcha Paani backup."
                    return@launchAction
                }
                SharedFiles.Read.Unreadable -> {
                    _statusMessage.value = "Couldn't open that file."
                    return@launchAction
                }
            }
            when (val result = withContext(Dispatchers.Default) { LocalBackup.decode(text) }) {
                is BackupReadResult.Unreadable ->
                    _statusMessage.value = "That doesn't look like a Kharcha Paani backup."
                is BackupReadResult.TooNew ->
                    _statusMessage.value = "That backup was written by a newer version of the app. Update first."
                is BackupReadResult.Invalid ->
                    _statusMessage.value = "That backup can't be restored: ${result.problem}. Nothing was changed."
                is BackupReadResult.Ok -> _restorePreview.value = result.backup
            }
        }
    }

    /** Puts back whatever in the previewed backup is missing here. */
    fun confirmRestore() {
        val backup = _restorePreview.value ?: return
        _restorePreview.value = null
        launchAction {
            val report = backupRepo.restore(backup)
            restoreSettings(backup.settings)
            _statusMessage.value = report.copy(settingsRestored = !backup.settings.isEmpty).message()
        }
    }

    fun dismissRestore() {
        _restorePreview.value = null
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
        return ledgerRepo.getLedgerForPerson(personId, getApplication<Application>())
    }

    companion object {
        /** A year of a busy ledger is well under 1 MB of JSON; this is generous and still bounded. */
        const val BACKUP_MAX_BYTES = 16 * 1024 * 1024

        fun factory(application: Application, db: AppDatabase, userId: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { MainViewModel(application, db, userId) } }
    }
}

/** Offline and no-key failures never reached Google, so they don't count against the daily cap. */
private fun AiAskResult.spendsAnAsk(): Boolean =
    this !is AiAskResult.Failed || reason !in setOf(AiFailure.NoKey, AiFailure.Offline)
