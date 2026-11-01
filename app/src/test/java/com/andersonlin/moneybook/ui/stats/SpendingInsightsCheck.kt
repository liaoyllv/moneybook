package com.andersonlin.moneybook.ui.stats

import java.time.LocalDate

/** 不依赖Android运行时的统计边界回归，可通过 checkInsights 执行。 */
fun main() {
    fun day(value:String)=LocalDate.parse(value)
    val w=insightWindow(2,day("2026-10-30"),false,day("2026-10-30"))
    check(LocalDate.ofEpochDay(w.previousEnd)==day("2026-09-30"))
    val partial=insightWindow(2,day("2026-10-12"),false,day("2026-10-12"))
    check(LocalDate.ofEpochDay(partial.previousEnd)==day("2026-09-12"))
    val leap=insightWindow(2,day("2024-03-31"),false,day("2026-10-30"))
    check(LocalDate.ofEpochDay(leap.previousEnd)==day("2024-02-29"))
    val calendar=insightWindow(3,day("2026-09-16"),true,day("2026-10-30"))
    check(LocalDate.ofEpochDay(calendar.start)==day("2026-09-01"))
    check(LocalDate.ofEpochDay(calendar.previousEnd)==day("2026-08-31"))
    val year=insightWindow(3,day("2024-02-29"),false,day("2024-02-29"))
    check(LocalDate.ofEpochDay(year.previousEnd)==day("2023-02-28"))
    val a=InsightRecord(1,1,100,1000,0,"早餐","早餐\n外食\n早餐")
    check(tagsOf(a.tags)==listOf("早餐","外食"))
    val amounts=expenseMetrics(listOf(a),emptyList()) { "餐饮" }
    check(amounts.single().total==100L && amounts.single().count==1)
    val disappearing=expenseMetrics(emptyList(),listOf(a)) { "餐饮" }
    check(disappearing.single().total==0L && disappearing.single().previous==100L)
    val sample=(1..8).map { a.copy(id=it.toLong(),day=990+it.toLong()) }
    check(expenseAnomalies(listOf(a.copy(amount=10000)),sample).single().sampleCount==8)
    check(expenseAnomalies(listOf(a.copy(amount=10000)),sample.take(7)).isEmpty())
    check(expenseAnomalies(listOf(a.copy(amount=10000)),sample.map { it.copy(day=1001) }).isEmpty())
    check(expenseAnomalies(listOf(a.copy(type=1,amount=10000)),sample).isEmpty())
    val march=java.time.YearMonth.of(2024,3)
    val record=a.copy(day=day("2024-03-01").toEpochDay(),amount=250)
    val prior=a.copy(day=day("2024-02-01").toEpochDay(),amount=150)
    val comparison=compareExpenseMonths(listOf(record,prior,prior.copy(type=1,amount=9999)),march,day("2024-03-12"))
    check(comparison.first().current==250L && comparison.first().previous==150L)
    check(comparison[11].current==0L && comparison[12].current==null)
    check(comparison[28].previous==0L && comparison[29].previous==null)
    val jan=compareExpenseMonths(listOf(record.copy(day=day("2025-12-01").toEpochDay())),java.time.YearMonth.of(2026,1),day("2026-02-01"))
    check(jan.first().previous==250L)
    println("统计回归通过：等进度区间、日历、闰年、多标签去重、消失分类、异常样本与未来数据隔离")
}
