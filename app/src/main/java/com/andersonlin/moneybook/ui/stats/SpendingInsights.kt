package com.andersonlin.moneybook.ui.stats

import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

data class InsightRecord(val id: Long, val categoryId: Long, val amount: Long,
    val day: Long, val type: Int, val note: String, val tags: String)
data class ExpenseMetric(val label: String, val count: Int, val total: Long,
    val previous: Long = 0, val previousCount: Int = 0) {
    val average: Long get() = if (count == 0) 0 else total / count
}
data class ExpenseAnomaly(val record: InsightRecord, val median: Long, val sampleCount: Int)
data class InsightWindow(val start: Long, val end: Long, val previousStart: Long, val previousEnd: Long) {
    val comparisonLabel: String get() = "${LocalDate.ofEpochDay(start)} ~ ${LocalDate.ofEpochDay(end)}；对比 ${LocalDate.ofEpochDay(previousStart)} ~ ${LocalDate.ofEpochDay(previousEnd)}"
}

fun tagsOf(value: String): List<String> = value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

fun formatNoteAndTags(note: String, tags: String): String {
    val tagList = tagsOf(tags)
    val trimmedNote = note.trim()
    val parts = buildList {
        if (tagList.isNotEmpty()) add(tagList.joinToString("、"))
        if (trimmedNote.isNotEmpty() && trimmedNote !in tagList) add(trimmedNote)
    }
    return if (parts.isEmpty()) "无备注" else parts.joinToString(" · ")
}

/** 当前未结束周期采用相同进度比较，短月份在月底截断。日历始终按月。 */
fun insightWindow(scale: Int, anchor: LocalDate, calendar: Boolean, today: LocalDate = LocalDate.now()): InsightWindow {
    val s = if (calendar) 2 else scale
    fun period(a: LocalDate): Pair<LocalDate, LocalDate> = when(s) {
        0 -> a to a
        1 -> a.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
        2 -> YearMonth.from(a).let { it.atDay(1) to it.atEndOfMonth() }
        else -> LocalDate.of(a.year,1,1) to LocalDate.of(a.year,12,31)
    }
    val (start, fullEnd) = period(anchor)
    val end = minOf(fullEnd,today).coerceAtLeast(start)
    val prevAnchor = when(s) { 0 -> anchor.minusDays(1); 1 -> anchor.minusWeeks(1); 2 -> anchor.minusMonths(1); else -> anchor.minusYears(1) }
    val (ps, pe) = period(prevAnchor)
    val previousEnd = if(end == fullEnd) pe else when(s) {
        2 -> YearMonth.from(ps).atDay(minOf(end.dayOfMonth,YearMonth.from(ps).lengthOfMonth()))
        3 -> end.minusYears(1)
        else -> minOf(ps.plusDays(end.toEpochDay()-start.toEpochDay()),pe)
    }
    return InsightWindow(start.toEpochDay(),end.toEpochDay(),ps.toEpochDay(),previousEnd.toEpochDay())
}

fun expenseMetrics(current: List<InsightRecord>, previous: List<InsightRecord>, key: (InsightRecord) -> String): List<ExpenseMetric> {
    val c=current.groupBy(key); val p=previous.groupBy(key)
    return (c.keys+p.keys).map { k -> ExpenseMetric(k,c[k]?.size ?: 0,c[k]?.sumOf { it.amount } ?: 0,
        p[k]?.sumOf { it.amount } ?: 0,p[k]?.size ?: 0) }.sortedByDescending { it.total }
}

/** 同分类过去180天至少8笔样本，超过3倍中位数及IQR上界才列为候选。 */
fun expenseAnomalies(current: List<InsightRecord>, history: List<InsightRecord>): List<ExpenseAnomaly> {
    val currentExpenses = current.filter { it.type == 0 }
    if (currentExpenses.isEmpty()) return emptyList()
    val minDay = currentExpenses.minOf { it.day } - 180
    val maxDay = currentExpenses.maxOf { it.day }
    val historyByCategory = history
        .asSequence()
        .filter { it.type == 0 && it.day in minDay until maxDay }
        .groupBy { it.categoryId }
    return currentExpenses.mapNotNull { r ->
        val categoryHistory = historyByCategory[r.categoryId] ?: return@mapNotNull null
        val amounts = categoryHistory
            .filter { it.day < r.day && it.day >= r.day - 180 }
            .map { it.amount }
            .sorted()
        if (amounts.size < 8) null else {
            val median = amounts[amounts.size / 2]
            val q1 = amounts[amounts.size / 4]
            val q3 = amounts[amounts.size * 3 / 4]
            if (r.amount.toDouble() > maxOf(median * 3.0, q3 + 1.5 * (q3 - q1))) {
                ExpenseAnomaly(r, median, amounts.size)
            } else null
        }
    }.sortedByDescending { it.record.amount }.take(10)
}


data class DayComparison(val day: Int, val current: Long?, val previous: Long?, val currentCount: Int, val previousCount: Int)

/** 同日支出对比：无该日期/尚未到来为null；存在该日但未记录为0。 */
fun compareExpenseMonths(records: List<InsightRecord>, month: YearMonth, today: LocalDate=LocalDate.now()): List<DayComparison> {
    val previous=month.minusMonths(1)
    val minEpoch = previous.atDay(1).toEpochDay()
    val maxEpoch = month.atEndOfMonth().toEpochDay()
    val expense = records
        .asSequence()
        .filter { it.type == 0 && it.day in minEpoch..maxEpoch }
        .groupBy { it.day }
    return (1..maxOf(month.lengthOfMonth(),previous.lengthOfMonth())).map { day ->
        fun data(m: YearMonth): Pair<Long?,Int> {
            if(day>m.lengthOfMonth() || m.atDay(day)>today) return null to 0
            val rows=expense[m.atDay(day).toEpochDay()] ?: emptyList()
            return rows.sumOf {it.amount} to rows.size
        }
        val c=data(month);val p=data(previous)
        DayComparison(day,c.first,p.first,c.second,p.second)
    }
}
