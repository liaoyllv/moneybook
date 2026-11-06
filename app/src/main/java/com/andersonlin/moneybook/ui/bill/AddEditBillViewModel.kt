package com.andersonlin.moneybook.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Account
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.repository.AccountRepository
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.repository.LedgerRepository
import com.andersonlin.moneybook.util.formatCentsPlain
import com.andersonlin.moneybook.util.toCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import com.andersonlin.moneybook.ui.stats.tagsOf
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AddEditUiState(
    val isEdit: Boolean = false,
    val type: Int = Bill.TYPE_EXPENSE,
    val amountText: String = "",
    val note: String = "",
    val tags: List<String> = emptyList(),
    val tagSuggestions: List<String> = emptyList(),
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val categoriesLoaded: Boolean = false,
    val accountId: Long = Bill.DEFAULT_ACCOUNT_ID,
    val accounts: List<Account> = emptyList(),
    val amountError: Boolean = false,
    val attachmentUri: String? = null,
    val attachmentMime: String? = null
) {
    val hasAttachment: Boolean get() = !attachmentUri.isNullOrBlank()
    val attachmentIsImage: Boolean get() = attachmentMime?.startsWith("image/") == true
}

sealed interface AddEditEvent {
    data class ShowMessage(val message: String) : AddEditEvent
    data object Saved : AddEditEvent
    data object SavedContinue : AddEditEvent
    data object Deleted : AddEditEvent
}

/** 记一笔：新增 / 编辑 / 删除账单 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddEditBillViewModel(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddEditEvent>()
    val events = _events.asSharedFlow()

    private var bill: Bill? = null
    private var saving = false
    private var accountChosen = false
    private var categoryHeatMap: Map<Long, Int> =
        billRepository.getWarmCategoryHeat(ledgerRepository.cachedActiveLedgerId)
    private var tagSuggestionsMap: Map<Long, List<String>> =
        billRepository.getWarmTagSuggestions(ledgerRepository.cachedActiveLedgerId)
    private var flowsStarted = false

    private fun warmCategoriesForType(type: Int): List<Category> {
        val heat = categoryHeatMap
        return categoryRepository.cachedCategories
            .filter { it.type == type }
            .sortedWith(
                compareByDescending<Category> { heat[it.id] ?: 0 }
                    .thenBy { it.sortOrder }
                    .thenBy { it.id }
            )
    }

    /** 进入页面时调用：billId > 0 为编辑模式，否则为新增；initialDate > 0 时作为新增的预填日期 */
    fun init(billId: Long, defaultType: Int, initialDate: Long = 0L) {
        saving = false
        accountChosen = false
        val todayEpochDay = LocalDate.now().toEpochDay()
        val activeLedgerId = ledgerRepository.cachedActiveLedgerId
        categoryHeatMap = billRepository.getWarmCategoryHeat(activeLedgerId).ifEmpty { categoryHeatMap }
        tagSuggestionsMap = billRepository.getWarmTagSuggestions(activeLedgerId).ifEmpty { tagSuggestionsMap }

        if (!flowsStarted) {
            flowsStarted = true
            // 1. 轻量化标签与备注建议（优先命中内存缓存，未命中时后台计算）
            viewModelScope.launch(Dispatchers.Default) {
                ledgerRepository.activeLedgerId
                    .collect { ledgerId ->
                        val suggestions = billRepository.getCachedTagSuggestions(ledgerId)
                        tagSuggestionsMap = suggestions
                        val currentCatId = _uiState.value.categoryId
                        if (currentCatId != null) {
                            val curSuggestions = suggestions[currentCatId] ?: emptyList()
                            _uiState.update { it.copy(tagSuggestions = curSuggestions) }
                        }
                    }
            }

            // 2. 当前类型的分类列表 + 分类使用热度动态排序（按近期与全量综合加权）
            viewModelScope.launch {
                combine(
                    _uiState
                        .map { it.type }
                        .distinctUntilChanged()
                        .flatMapLatest { type ->
                            categoryRepository.getCategoriesByType(type).map { type to it }
                        },
                    ledgerRepository.activeLedgerId.flatMapLatest { billRepository.getCategoryHeat(it) }
                ) { (type, categories), heatList ->
                    val heat = heatList.associate { it.categoryId to it.heat }
                    categoryHeatMap = heat
                    type to categories.sortedWith(
                        compareByDescending<Category> { heat[it.id] ?: 0 }
                            .thenBy { it.sortOrder }
                            .thenBy { it.id }
                    )
                }
                    .flowOn(Dispatchers.Default)
                    .collect { (type, categories) ->
                        _uiState.update { state ->
                            if (state.type != type) {
                                state
                            } else {
                                val categoryId = if (state.categoryId != null &&
                                    categories.any { it.id == state.categoryId }
                                ) {
                                    state.categoryId
                                } else {
                                    categories.firstOrNull()?.id
                                }
                                val suggestions = categoryId?.let { tagSuggestionsMap[it] } ?: emptyList()
                                state.copy(
                                    categories = categories,
                                    categoriesLoaded = true,
                                    categoryId = categoryId,
                                    tagSuggestions = suggestions
                                )
                            }
                        }
                    }
            }

            // 3. 账户列表
            viewModelScope.launch {
                accountRepository.getAllAccounts().collect { accounts ->
                    _uiState.update { state ->
                        val accountId = if (!accountChosen && (bill == null || !state.isEdit)) {
                            accounts.firstOrNull { it.name == "支付宝" }?.id ?: accounts.firstOrNull()?.id ?: Bill.DEFAULT_ACCOUNT_ID
                        } else if (accounts.any { it.id == state.accountId }) {
                            state.accountId
                        } else {
                            accounts.firstOrNull()?.id ?: Bill.DEFAULT_ACCOUNT_ID
                        }
                        state.copy(accounts = accounts, accountId = accountId)
                    }
                }
            }
        }

        if (billId > 0) {
            viewModelScope.launch {
                billRepository.getById(billId)?.let { b ->
                    bill = b
                    _uiState.update {
                        it.copy(
                            isEdit = true,
                            type = b.type,
                            amountText = formatCentsPlain(b.amountCents),
                            note = b.note,
                            tags = tagsOf(b.tags).take(1),
                            dateEpochDay = b.dateEpochDay,
                            categoryId = b.categoryId,
                            accountId = b.accountId,
                            attachmentUri = b.attachmentUri,
                            attachmentMime = b.attachmentMime,
                            amountError = false,
                            tagSuggestions = tagSuggestionsMap[b.categoryId] ?: emptyList()
                        )
                    }
                }
            }
        } else {
            bill = null
            val targetDate = if (initialDate > 0) initialDate else todayEpochDay
            val warmCats = warmCategoriesForType(defaultType)
            val warmAccounts = accountRepository.cachedAccounts
            _uiState.update { state ->
                val cats = state.categories.ifEmpty { warmCats }
                val catId = state.categoryId?.takeIf { id -> cats.any { it.id == id } } ?: cats.firstOrNull()?.id
                val accs = state.accounts.ifEmpty { warmAccounts }
                val defaultAccId = accs.firstOrNull { it.name == "支付宝" }?.id
                    ?: accs.firstOrNull()?.id
                    ?: state.accountId
                state.copy(
                    isEdit = false,
                    type = defaultType,
                    amountText = "",
                    note = "",
                    tags = emptyList(),
                    dateEpochDay = targetDate,
                    categories = cats,
                    categoriesLoaded = cats.isNotEmpty() || state.categoriesLoaded,
                    categoryId = catId,
                    accounts = accs,
                    accountId = defaultAccId,
                    amountError = false,
                    attachmentUri = null,
                    attachmentMime = null,
                    tagSuggestions = catId?.let { tagSuggestionsMap[it] } ?: emptyList()
                )
            }
        }
    }

    fun setType(type: Int) = _uiState.update {
        if (it.type == type) it else {
            val warmCats = warmCategoriesForType(type)
            val catId = warmCats.firstOrNull()?.id
            it.copy(
                type = type,
                categories = warmCats,
                categoryId = catId,
                tags = emptyList(),
                tagSuggestions = catId?.let { id -> tagSuggestionsMap[id] } ?: emptyList()
            )
        }
    }

    fun setAccount(id: Long) { accountChosen=true;_uiState.update { it.copy(accountId=id) } }

    fun setAttachment(uri: String?, mime: String?) =
        _uiState.update { it.copy(attachmentUri = uri, attachmentMime = mime) }

    fun clearAttachment() =
        _uiState.update { it.copy(attachmentUri = null, attachmentMime = null) }

    fun setAmount(text: String) {
        // 只允许数字 + 最多一个小数点、两位小数
        if (text.isBlank() || text.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) {
            _uiState.update { it.copy(amountText = text, amountError = false) }
        }
    }

    fun setNote(text: String) = _uiState.update { it.copy(note = text.take(50)) }

    fun setDate(epochDay: Long) = _uiState.update { it.copy(dateEpochDay = epochDay) }

    fun setCategory(id: Long) {
        if (_uiState.value.categoryId == id) return
        val suggestions = tagSuggestionsMap[id] ?: emptyList()
        _uiState.update {
            it.copy(categoryId = id, tags = emptyList(), tagSuggestions = suggestions)
        }
    }

    fun addTag(value: String) {
        val tag = value.trim().replace("\n", " ").take(30)
        if (tag.isNotEmpty()) {
            _uiState.update { it.copy(tags = listOf(tag)) }
        }
    }

    fun toggleTag(value: String) {
        val tag = value.trim().replace("\n", " ").take(30)
        if (tag.isEmpty()) return
        _uiState.update {
            it.copy(tags = if (tag in it.tags) emptyList() else listOf(tag))
        }
    }

    fun reorderCategories(ids: List<Long>) { viewModelScope.launch {
        runCatching { categoryRepository.reorder(ids) }.onFailure { emit(AddEditEvent.ShowMessage("排序失败，请重试")) }
    } }

    /**
     * 保存账单。
     * @param andContinue true = 保存后留在本页并清空金额/备注（连续记账），
     *                    false = 保存后返回上一页。
     */
    fun save(andContinue: Boolean = false) {
        if(saving) return
        val state = _uiState.value
        val cents = state.amountText.toCents()
        if (cents == null) {
            _uiState.update { it.copy(amountError = true) }
            emit(AddEditEvent.ShowMessage("请输入有效金额（大于 0，最多两位小数）"))
            return
        }
        val categoryId = state.categoryId
        if (categoryId == null) {
            emit(AddEditEvent.ShowMessage("请先选择分类"))
            return
        }
        saving=true
        viewModelScope.launch {
            var finished=false
            try {
            val existing = bill
            val ledgerId = ledgerRepository.getActiveLedgerId()
            if (existing == null) {
                billRepository.insert(
                    Bill(
                        type = state.type,
                        amountCents = cents,
                        categoryId = categoryId,
                        accountId = state.accountId,
                        ledgerId = ledgerId,
                        note = state.note.trim(),
                        tags = state.tags.distinct().joinToString("\n"),
                        dateEpochDay = state.dateEpochDay,
                        attachmentUri = state.attachmentUri,
                        attachmentMime = state.attachmentMime
                    )
                )
            } else {
                billRepository.update(
                    existing.copy(
                        type = state.type,
                        amountCents = cents,
                        categoryId = categoryId,
                        accountId = state.accountId,
                        note = state.note.trim(),
                        tags = state.tags.distinct().joinToString("\n"),
                        dateEpochDay = state.dateEpochDay,
                        attachmentUri = state.attachmentUri,
                        attachmentMime = state.attachmentMime
                    )
                )
            }
            if (andContinue) {
                // 保留类型/分类/账户/日期，清空金额与备注与附件，方便连续记账
                _uiState.update {
                    it.copy(
                        amountText = "",
                        note = "",
                        tags = emptyList(),
                        amountError = false,
                        attachmentUri = null,
                        attachmentMime = null
                    )
                }
                bill = null
                _events.emit(AddEditEvent.SavedContinue)
            } else {
                finished=true
                _events.emit(AddEditEvent.Saved)
            }
            } catch (e: Exception) {
                if(e is kotlinx.coroutines.CancellationException) throw e
                _events.emit(AddEditEvent.ShowMessage("保存失败，请重试"))
            } finally { if(!finished) saving=false }
        }
    }

    fun delete() {
        val existing = bill ?: return
        viewModelScope.launch {
            billRepository.delete(existing)
            _events.emit(AddEditEvent.Deleted)
        }
    }

    private fun emit(event: AddEditEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}
