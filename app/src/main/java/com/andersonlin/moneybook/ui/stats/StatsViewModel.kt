package com.andersonlin.moneybook.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.repository.LedgerRepository
import com.andersonlin.moneybook.util.endEpochDay
import com.andersonlin.moneybook.util.fullDateLabel
import com.andersonlin.moneybook.util.monthLabel
import com.andersonlin.moneybook.util.startEpochDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 饼图切片 */
data class StatSlice(
    val category: Category,
    val total: Long
)

/** 统计时间范围 */
data class StatsRange(
    val label: String,
    val startDay: Long,
    val endDay: Long
)

/** 柱状/折线共用的一组数据点 */
data class ChartPoint(
    val label: String,
    val expense: Long,
    val income: Long
)

/** 日历视图的一天 */
data class CalendarDay(
    val date: LocalDate,
    val expense: Long,
    val income: Long
)

data class StatsUiState(
    val scale: Int = StatsViewModel.SCALE_MONTH,
    val label: String = monthLabel(YearMonth.now()),
    val type: Int = StatsViewModel.TYPE_EXPENSE,
    val chartType: Int = StatsViewModel.CHART_PIE,
    val anchorDate: LocalDate = LocalDate.now(),
    val slices: List<StatSlice> = emptyList(),
    val total: Long = 0L,
    val canGoNext: Boolean = false,
    val chartPoints: List<ChartPoint> = emptyList(),
    val chartWindowLabel: String = "",
    val chartHasData: Boolean = false,
    val calendarMonth: YearMonth = YearMonth.now(),
    val calendarDays: List<CalendarDay> = emptyList(),
    val prevTotal: Long? = null,
    val comparisonLabel: String = "",
    val currentRecords: List<InsightRecord> = emptyList(),
    val previousRecords: List<InsightRecord> = emptyList(),
    val categoryNames: Map<Long,String> = emptyMap(),
    val categoryMap: Map<Long, Category> = emptyMap(),
    val categoryMetrics: List<ExpenseMetric> = emptyList(),
    val anomalies: List<ExpenseAnomaly> = emptyList(),
    val monthComparison: List<DayComparison> = emptyList(),
    val periodComparison: PeriodComparison = PeriodComparison("", "", emptyList())
) {
    val typeLabel: String
        get() = when (type) {
            StatsViewModel.TYPE_INCOME -> "收入"
            StatsViewModel.TYPE_ALL -> "流水"
            else -> "支出"
        }
}

/**
 * 统计页：分类占比饼图（支出/收入/全部三态）+ 收支对比图。
 * 日/周/月/年维度始终生效：
 * - 饼图：区间内分类占比
 * - 柱状/趋势：同一窗口数据 —— 年 = 该年 12 个月，月 = 该月每日，周 = 该周 7 日，日 = 近 7 天
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    companion object {
        const val SCALE_DAY = 0
        const val SCALE_WEEK = 1
        const val SCALE_MONTH = 2
        const val SCALE_YEAR = 3

        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1
        const val TYPE_ALL = -1

        const val CHART_PIE = 0
        const val CHART_BAR = 1
        const val CHART_LINE = 2
        const val CHART_CALENDAR = 3
    }

    private val scale = MutableStateFlow(SCALE_MONTH)
    private val anchor = MutableStateFlow(LocalDate.now())
    private val type = MutableStateFlow(TYPE_EXPENSE)
    private val chartType = MutableStateFlow(CHART_PIE)

    private val recordsFlow = ledgerRepository.activeLedgerId
        .flatMapLatest { billRepository.getAllBills(it) }
        .map { bills ->
            bills.map {
                InsightRecord(it.id, it.categoryId, it.amountCents, it.dateEpochDay, it.type, it.note, it.tags)
            }
        }
        .flowOn(Dispatchers.Default)

    private val baseStateFlow = combine(
        combine(scale, anchor, type, ledgerRepository.activeLedgerId) { s, a, t, l ->
            StatsQuery(s, a, t, CHART_PIE, l)
        }
            .flatMapLatest { q ->
                val window = insightWindow(q.scale, q.anchor, false)
                val range = StatsRange("${LocalDate.ofEpochDay(window.start)} ~ ${LocalDate.ofEpochDay(window.end)}", window.start, window.end)
                // 图表窗口（柱状/趋势共用）：年维度用逐月聚合，其余用逐日聚合
                val year = q.anchor.year
                val ym = YearMonth.from(q.anchor)
                val queryStart = minOf(dailyStart(q), ym.startEpochDay())
                val queryEnd = maxOf(dailyEnd(q), ym.endEpochDay())
                combine(
                    billRepository.getMonthlyTotals(
                        q.ledgerId,
                        LocalDate.of(year, 1, 1).toEpochDay(),
                        LocalDate.of(year, 12, 31).toEpochDay()
                    ),
                    billRepository.getDailyTotals(q.ledgerId, queryStart, queryEnd)
                ) { monthly, daily ->
                    Pair(range to window, Pair(monthly, daily) to q)
                }
            },
        recordsFlow,
        categoryRepository.getAllCategories()
    ) { (meta, payload), records, categories ->
        val (range, window) = meta
        val (totals, q) = payload
        val (monthly, daily) = totals
        val result = QueryResult(range, q, monthly, daily, window, records)
        val categoryMap = categories.associateBy { it.id }
        val current=result.records.filter { it.day in result.window.start..result.window.end && (result.query.type==-1 || it.type==result.query.type) }
        val previous=result.records.filter { it.day in result.window.previousStart..result.window.previousEnd && (result.query.type==-1 || it.type==result.query.type) }
        val slices = current.groupBy { it.categoryId }.mapNotNull { (id, records) ->
            categoryMap[id]?.let { StatSlice(it,records.sumOf { r -> r.amount }) }
        }.sortedByDescending { it.total }

        val points = buildChartPoints(result)

        val today = LocalDate.now()
        val canGoNext = when (result.query.scale) {
            SCALE_DAY -> result.query.anchor < today
            SCALE_WEEK -> result.query.anchor
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusDays(6) < today
            SCALE_MONTH -> YearMonth.from(result.query.anchor) < YearMonth.now()
            SCALE_YEAR -> result.query.anchor.year < today.year
            else -> false
        }

        val prevTotal = previous.takeIf { it.isNotEmpty() }?.sumOf { it.amount }

        StatsUiState(
            scale = result.query.scale,
            label = result.range.label,
            type = result.query.type,
            chartType = result.query.chartType,
            anchorDate = result.query.anchor,
            slices = slices,
            total = current.sumOf { it.amount },
            canGoNext = canGoNext,
            chartPoints = points,
            chartWindowLabel = chartWindowLabel(result.query),
            chartHasData = points.any { it.expense > 0 || it.income > 0 },
            calendarMonth = YearMonth.from(result.query.anchor),
            calendarDays = buildCalendarDays(result),
            prevTotal = prevTotal,
            comparisonLabel=result.window.comparisonLabel,
            currentRecords=current, previousRecords=previous,
            categoryNames=categoryMap.mapValues { it.value.name },
            categoryMap=categoryMap,
            categoryMetrics=expenseMetrics(current,previous) { (categoryMap[it.categoryId]?.name ?: "未知分类") + if(result.query.type==-1 && it.type==1) "（收入）" else "" },
            anomalies=expenseAnomalies(current,result.records),
            monthComparison=compareExpenseMonths(result.records,YearMonth.from(result.query.anchor)),
            periodComparison=compareExpensePeriod(result.records, result.query.scale, result.query.anchor)
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<StatsUiState> = combine(baseStateFlow, chartType) { base, c ->
        val canGoNext = if (c == CHART_CALENDAR) {
            base.calendarMonth < YearMonth.now()
        } else {
            base.canGoNext
        }
        base.copy(chartType = c, canGoNext = canGoNext)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StatsUiState()
    )

    fun setScale(scale: Int) {
        this.scale.value = scale
    }

    fun setChartType(chartType: Int) {
        if(chartType==CHART_BAR || chartType==CHART_LINE) type.value=TYPE_EXPENSE
        this.chartType.value = chartType
    }

    fun prev() = move(-1)

    fun next() {
        if (uiState.value.canGoNext) move(1)
    }

    fun setType(type: Int) {
        this.type.value = type
    }

    fun setAnchor(date: LocalDate) {
        anchor.value = date
    }

    private fun move(delta: Int) {
        val a = anchor.value
        anchor.value = if(chartType.value==CHART_CALENDAR) a.plusMonths(delta.toLong()) else when (scale.value) {
            SCALE_DAY -> a.plusDays(delta.toLong())
            SCALE_WEEK -> a.plusWeeks(delta.toLong())
            SCALE_MONTH -> a.plusMonths(delta.toLong())
            SCALE_YEAR -> a.plusYears(delta.toLong())
            else -> a
        }
    }

    // ---- 图表窗口 ----

    private fun dailyStart(q: StatsQuery): Long = when {
        q.chartType == CHART_CALENDAR -> YearMonth.from(q.anchor).startEpochDay()
        q.scale == SCALE_DAY -> q.anchor.minusDays(6).toEpochDay()
        q.scale == SCALE_WEEK -> q.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        q.scale == SCALE_MONTH -> YearMonth.from(q.anchor).startEpochDay()
        else -> q.anchor.toEpochDay() // 年维度不使用 daily 数据
    }

    private fun dailyEnd(q: StatsQuery): Long = when {
        q.chartType == CHART_CALENDAR -> YearMonth.from(q.anchor).endEpochDay()
        q.scale == SCALE_DAY -> q.anchor.toEpochDay()
        q.scale == SCALE_WEEK -> q.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusDays(6).toEpochDay()
        q.scale == SCALE_MONTH -> YearMonth.from(q.anchor).endEpochDay()
        else -> q.anchor.toEpochDay()
    }

    /** 日历视图数据：锚点所在月的每日收支 */
    private fun buildCalendarDays(result: QueryResult): List<CalendarDay> {
        val ym = YearMonth.from(result.query.anchor)
        val dailyMap = result.daily.groupBy { it.day }
        return (1..ym.lengthOfMonth()).map { d ->
            val rows = dailyMap[dayKey(ym.year, ym.monthValue, d)] ?: emptyList()
            CalendarDay(
                date = LocalDate.of(ym.year, ym.monthValue, d),
                expense = rows.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L,
                income = rows.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L
            )
        }
    }

    /** 生成柱状/趋势共用的数据点（缺日/缺月补 0） */
    private fun buildChartPoints(result: QueryResult): List<ChartPoint> {
        val q = result.query
        return when (q.scale) {
            SCALE_YEAR -> {
                val monthlyMap = result.monthly.groupBy { it.ym }
                (1..12).map { m ->
                    val rows = monthlyMap[ymKey(q.anchor.year, m)] ?: emptyList()
                    ChartPoint(
                        label = "${m}月",
                        expense = rows.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L,
                        income = rows.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L
                    )
                }
            }
            SCALE_MONTH -> {
                val dailyMap = result.daily.groupBy { it.day }
                val ym = YearMonth.from(q.anchor)
                (1..ym.lengthOfMonth()).map { d ->
                    val rows = dailyMap[dayKey(ym.year, ym.monthValue, d)] ?: emptyList()
                    ChartPoint(
                        label = "${d}日",
                        expense = rows.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L,
                        income = rows.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L
                    )
                }
            }
            else -> {
                val dailyMap = result.daily.groupBy { it.day }
                val start = LocalDate.ofEpochDay(dailyStart(q))
                val days = (dailyEnd(q) - dailyStart(q)).toInt()
                (0..days).map { offset ->
                    val date = start.plusDays(offset.toLong())
                    val rows = dailyMap[dayKey(date.year, date.monthValue, date.dayOfMonth)] ?: emptyList()
                    ChartPoint(
                        label = "${date.monthValue}/${date.dayOfMonth}",
                        expense = rows.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L,
                        income = rows.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L
                    )
                }
            }
        }
    }

    private fun chartWindowLabel(q: StatsQuery): String = when (q.scale) {
        SCALE_YEAR -> "${q.anchor.year}年 · 月度对比"
        SCALE_MONTH -> monthLabel(YearMonth.from(q.anchor)) + " · 每日对比"
        SCALE_WEEK -> {
            val monday = q.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            "${monday.monthValue}/${monday.dayOfMonth}-${sunday.monthValue}/${sunday.dayOfMonth} · 每日对比"
        }
        else -> "近 7 天 · 每日对比"
    }
}

private data class StatsQuery(
    val scale: Int,
    val anchor: LocalDate,
    val type: Int,
    val chartType: Int,
    val ledgerId: Long
)

private data class QueryResult(
    val range: StatsRange,
    val query: StatsQuery,
    val monthly: List<com.andersonlin.moneybook.data.db.BillDao.MonthlyTotal>,
    val daily: List<com.andersonlin.moneybook.data.db.BillDao.DailyTotal>,
    val window: InsightWindow,
    val records: List<InsightRecord>
)

private fun ymKey(year: Int, month: Int): String = "%04d-%02d".format(year, month)

private fun dayKey(year: Int, month: Int, day: Int): String =
    "%04d-%02d-%02d".format(year, month, day)
