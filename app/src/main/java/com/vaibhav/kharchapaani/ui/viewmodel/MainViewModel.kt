package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.data.sync.SyncScheduler
import com.vaibhav.kharchapaani.data.sync.TransactionSyncEngine
import com.vaibhav.kharchapaani.domain.ActivityFeed
import com.vaibhav.kharchapaani.domain.DebtSimplifier
import com.vaibhav.kharchapaani.domain.LocalStatementParser
import com.vaibhav.kharchapaani.domain.SharedRecordGroups
import com.vaibhav.kharchapaani.domain.SplitRounding
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.util.NotificationHelper
import com.vaibhav.kharchapaani.widget.SplitFlowWidgetProvider
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val geminiRepo = GeminiRepository()

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

    /** Recent Activity: transactions plus settlements and IOUs, filtered and searched together. */
    val activityFeed: StateFlow<List<ActivityItem>> = combine(
        rawTransactions,
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
            }
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

    private val _editingTransactionId = MutableStateFlow<Long?>(null)
    val editingTransactionId: StateFlow<Long?> = _editingTransactionId.asStateFlow()

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

    // AI Financial Summary
    private val _aiSummary = MutableStateFlow<String?>(null)
    val aiSummary: StateFlow<String?> = _aiSummary.asStateFlow()

    private val _isGeneratingSummary = MutableStateFlow(false)
    val isGeneratingSummary: StateFlow<Boolean> = _isGeneratingSummary.asStateFlow()

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

    fun openManualAdd(defaultType: String = "expense") {
        _editingTransactionId.value = null
        _type.value = defaultType
        _amount.value = ""
        _merchant.value = ""
        _category.value = if (defaultType == "income") "Income (Client)" else "Food"
        _paymentMethod.value = "PopUPI"
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
        _editingTransactionId.value = null
        _type.value = "expense"
        _amount.value = ""
        _merchant.value = ""
        _category.value = "Food"
        _paymentMethod.value = "PopUPI"
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
            shareInputs.add(ShareInput(personId = person.id, personName = person.name, isSelected = isMember))
        }
        _shares.value = shareInputs
        recalculateShares()
        _isConfirmSheetOpen.value = true
    }

    fun openEditTransaction(fullTx: FullTransaction) {
        val tx = fullTx.transaction
        // Decision 2: only the person who added a group expense can change it.
        if (!tx.isOwnedByMe) {
            val owner = people.value.firstOrNull { it.profileId == tx.ownerProfileId }?.name ?: "the member who added it"
            _statusMessage.value = "Only $owner can edit this expense."
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

            val currentPeople = candidatesFor(tx.groupId)
            val existingShareMap = fullTx.shares.associateBy { it.personId }

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
                        isSelected = pShare != null
                    )
                )
            }
            _shares.value = shareInputs
        } else {
            _paidByPersonId.value = -1L
            _splitMode.value = SplitMode.EQUAL
            initSharesForCurrentPeople()
        }

        _isConfirmSheetOpen.value = true
    }

    fun parseInputText(rawText: String) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            _isParsing.value = true
            val friendNames = people.value.map { it.name }
            val parsed = geminiRepo.parseMessyText(rawText, friendNames)
            applyParsedResult(parsed)
            _isParsing.value = false
            _isConfirmSheetOpen.value = true
        }
    }

    fun parseReceiptImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _isParsing.value = true
            _isConfirmSheetOpen.value = true
            val friendNames = people.value.map { it.name }
            val parsed = geminiRepo.parseReceiptOrScreenshot(bitmap, friendNames)
            applyParsedResult(parsed)
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
        }
    }

    fun parseBankOrUpiStatement(pdfBytes: ByteArray, fileName: String = "statement.pdf") {
        viewModelScope.launch {
            _isParsingStatement.value = true
            _isImportStatementOpen.value = true
            val result = geminiRepo.parseBankOrUpiStatementPdf(pdfBytes, fileName)
            _statementParseResult.value = result
            _isParsingStatement.value = false
        }
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
            _statusMessage.value = "Failed to open PDF statement: ${e.message}"
        }
    }

    fun loadSampleStatementForDemo() {
        _statementParseResult.value = LocalStatementParser.generateSampleParsedStatement()
        _isImportStatementOpen.value = true
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
            _statusMessage.value = "Successfully added ${entities.size} transactions across respective months!"
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    private fun applyParsedResult(parsed: ParsedTransactionResult) {
        _editingTransactionId.value = null
        _type.value = parsed.type
        _amount.value = if (parsed.amount > 0) {
            if (parsed.amount % 1.0 == 0.0) parsed.amount.toLong().toString() else parsed.amount.toString()
        } else ""
        _merchant.value = parsed.merchant
        _category.value = parsed.category
        _paymentMethod.value = parsed.paymentMethod
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
        if (newType == "income" && _category.value == "Food") {
            _category.value = "Income (Client)"
        } else if (newType == "expense" && _category.value.startsWith("Income")) {
            _category.value = "Food"
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
                candidatesFor(newGroupId).map { ShareInput(personId = it.id, personName = it.name, isSelected = it.id in selected) }
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
    private fun candidatesFor(groupId: Long?): List<PersonEntity> {
        if (!isSharedGroup(groupId)) return people.value
        val memberIds = groups.value.firstOrNull { it.group.id == groupId }?.members?.map { it.id }?.toSet().orEmpty()
        return people.value.filter { it.id in memberIds }
    }

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

        // A split with groupmates outside a shared group never reaches them (decision 1): ask first.
        if (askForGroup && _isSplit.value && !isSharedGroup(_groupId.value)) {
            val participants = _shares.value.filter { it.isSelected }.map { it.personId } + _paidByPersonId.value
            val options = groupOptionsFor(participants)
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
        val splitShareEntities = if (_isSplit.value) {
            selectedShares.map {
                SplitShareEntity(
                    splitId = 0,
                    personId = it.personId,
                    shareAmount = sharedPercentageAmounts?.get(it.personId) ?: it.amount,
                    percentage = it.percentage
                )
            }
        } else emptyList()

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

    fun deleteTransaction(txId: Long) {
        viewModelScope.launch {
            try {
                ledgerRepo.deleteTransaction(txId)
            } catch (e: ArchivedGroupException) {
                _statusMessage.value = archivedMessage(e)
                return@launch
            }
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
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
                "Group '$name' created. Open it to invite people."
            } else {
                "Group '$name' created successfully!"
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
        val simplified = DebtSimplifier.simplify(people.value, currentBalances)
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

    fun generateAiFinancialSummary() {
        viewModelScope.launch {
            _isGeneratingSummary.value = true
            val summary = geminiRepo.generateFinancialSummary(monthlyAnalytics.value)
            _aiSummary.value = summary
            _isGeneratingSummary.value = false
        }
    }

    fun triggerMonthlyNotification(context: Context) {
        val analytics = monthlyAnalytics.value
        NotificationHelper.showMonthlySpendingNotification(
            context = context,
            totalExpense = analytics.totalExpense,
            topCategory = analytics.topCategory,
            topCategoryAmount = analytics.topCategoryAmount
        )
        _statusMessage.value = "Monthly insight notification pushed to system tray!"
    }

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            val csvContent = ledgerRepo.exportCsv()
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, csvContent)
                putExtra(Intent.EXTRA_TITLE, "KharchaPaani_Statement.csv")
                type = "text/csv"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Export Kharcha Paani Financial Statement")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        }
    }

    fun getPersonLedgerFlow(personId: Long): kotlinx.coroutines.flow.Flow<List<PersonLedgerItem>> {
        return ledgerRepo.getLedgerForPerson(personId)
    }

    companion object {
        fun factory(application: Application, db: AppDatabase, userId: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { MainViewModel(application, db, userId) } }
    }
}
