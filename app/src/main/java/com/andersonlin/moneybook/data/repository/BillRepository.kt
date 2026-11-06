package com.andersonlin.moneybook.data.repository

import com.andersonlin.moneybook.data.db.BillDao
import com.andersonlin.moneybook.data.model.Bill
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/** 账单仓库：对 DAO 的薄封装，ViewModel 只依赖 Repository。查询均按账本隔离。 */
class BillRepository(private val dao: BillDao) {

    fun getAllBills(ledgerId: Long) = dao.getAllBills(ledgerId)

    fun getRecentBills(ledgerId: Long, limit: Int) = dao.getRecentBills(ledgerId, limit)

    suspend fun getById(id: Long): Bill? = dao.getById(id)

    fun getMonthSummary(ledgerId: Long, startDay: Long, endDay: Long) =
        dao.getMonthSummary(ledgerId, startDay, endDay)

    fun getCategoryStats(ledgerId: Long, type: Int, startDay: Long, endDay: Long) =
        dao.getCategoryStats(ledgerId, type, startDay, endDay)

    fun getMonthlyTotals(ledgerId: Long, startDay: Long, endDay: Long) =
        dao.getMonthlyTotals(ledgerId, startDay, endDay)

    fun getDailyTotals(ledgerId: Long, startDay: Long, endDay: Long) =
        dao.getDailyTotals(ledgerId, startDay, endDay)

    fun getDaySummary(ledgerId: Long, day: Long) = dao.getDaySummary(ledgerId, day)

    fun searchBills(
        ledgerId: Long,
        typeFilter: Int,
        keyword: String,
        minCents: Long? = null,
        maxCents: Long? = null
    ) = dao.searchBills(ledgerId, typeFilter, keyword, minCents, maxCents)

    @Volatile
    private var cachedTagSuggestionsLedgerId: Long = -1L
    @Volatile
    private var cachedTagSuggestions: Map<Long, List<String>>? = null
    @Volatile
    private var cachedCategoryCountsLedgerId: Long = -1L
    @Volatile
    private var cachedCategoryCounts: Map<Long, Int> = emptyMap()

    fun invalidateTagCache() {
        cachedTagSuggestions = null
    }

    @Volatile
    private var cachedCategoryHeatLedgerId: Long = -1L
    @Volatile
    private var cachedCategoryHeat: Map<Long, Int> = emptyMap()

    fun getWarmCategoryHeat(ledgerId: Long): Map<Long, Int> =
        if (cachedCategoryHeatLedgerId == ledgerId) cachedCategoryHeat else emptyMap()

    fun updateWarmCategoryHeat(ledgerId: Long, heat: Map<Long, Int>) {
        cachedCategoryHeatLedgerId = ledgerId
        cachedCategoryHeat = heat
    }

    suspend fun refreshWarmCategoryHeat(ledgerId: Long): Map<Long, Int> {
        val today = java.time.LocalDate.now().toEpochDay()
        val list = dao.getCategoryHeatSnapshot(ledgerId, today - 30, today - 90)
        val heatMap = list.associate { it.categoryId to it.heat }
        updateWarmCategoryHeat(ledgerId, heatMap)
        return heatMap
    }

    fun getCategoryHeat(ledgerId: Long): Flow<List<BillDao.CategoryHeat>> {
        val today = java.time.LocalDate.now().toEpochDay()
        return dao.getCategoryHeat(ledgerId, today - 30, today - 90).onEach { list ->
            updateWarmCategoryHeat(ledgerId, list.associate { it.categoryId to it.heat })
        }
    }

    fun getWarmTagSuggestions(ledgerId: Long): Map<Long, List<String>> =
        if (cachedTagSuggestionsLedgerId == ledgerId) cachedTagSuggestions ?: emptyMap() else emptyMap()

    fun getWarmCategoryCounts(ledgerId: Long): Map<Long, Int> =
        if (cachedCategoryCountsLedgerId == ledgerId) cachedCategoryCounts else emptyMap()

    fun updateWarmCategoryCounts(ledgerId: Long, counts: Map<Long, Int>) {
        cachedCategoryCountsLedgerId = ledgerId
        cachedCategoryCounts = counts
    }

    suspend fun getCachedTagSuggestions(ledgerId: Long): Map<Long, List<String>> {
        val cached = cachedTagSuggestions
        if (cachedTagSuggestionsLedgerId == ledgerId && cached != null) {
            return cached
        }
        val notes = dao.getTagNotesSnapshot(ledgerId)
        val suggestions = notes
            .groupBy { it.categoryId }
            .mapValues { (_, bList) ->
                bList.flatMap { b ->
                    (b.tags.split('\n', ',').map { it.trim() }.filter { it.isNotBlank() } +
                        listOf(b.note.trim()).filter { it.isNotBlank() && it.length <= 30 }).distinct()
                }.groupingBy { it }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .map { it.key }
            }
        cachedTagSuggestionsLedgerId = ledgerId
        cachedTagSuggestions = suggestions
        return suggestions
    }

    suspend fun insert(bill: Bill): Long {
        invalidateTagCache()
        return dao.insert(bill)
    }

    suspend fun update(bill: Bill) {
        invalidateTagCache()
        dao.update(bill)
    }

    suspend fun delete(bill: Bill) {
        invalidateTagCache()
        dao.delete(bill)
    }

    fun getCategoryCounts(ledgerId: Long) = dao.getCategoryCounts(ledgerId).onEach { list ->
        updateWarmCategoryCounts(ledgerId, list.associate { it.categoryId to it.count })
    }

    fun getTagNotes(ledgerId: Long) = dao.getTagNotes(ledgerId)

    suspend fun countByCategory(categoryId: Long): Int = dao.countByCategory(categoryId)

    suspend fun deleteByCategory(categoryId: Long) {
        invalidateTagCache()
        dao.deleteByCategory(categoryId)
    }

    suspend fun countByLedger(ledgerId: Long): Int = dao.countByLedger(ledgerId)

    suspend fun deleteByLedger(ledgerId: Long) {
        invalidateTagCache()
        dao.deleteByLedger(ledgerId)
    }

    suspend fun getAllSnapshot() = dao.getAllSnapshot()

    suspend fun getSnapshotForLedger(ledgerId: Long) = dao.getSnapshotForLedger(ledgerId)

    suspend fun insertAll(bills: List<Bill>) {
        invalidateTagCache()
        dao.insertAll(bills)
    }

    suspend fun deleteAll() {
        invalidateTagCache()
        dao.deleteAll()
    }
}
