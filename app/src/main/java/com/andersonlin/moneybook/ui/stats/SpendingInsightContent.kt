package com.andersonlin.moneybook.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andersonlin.moneybook.util.formatCents
import java.time.LocalDate

@Composable
internal fun SpendingInsightSummary(state: StatsUiState, initiallyExpanded: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("金额变化最大的分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        state.categoryMetrics.sortedByDescending { kotlin.math.abs(it.total - it.previous) }.take(5).forEach { m ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val delta = m.total - m.previous
                    val deltaColor = if (delta > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(m.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(
                            (if (delta >= 0) "+" else "−") + "¥" + formatCents(kotlin.math.abs(delta)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = deltaColor
                        )
                    }
                    Text("${m.previousCount} → ${m.count} 笔 · 本期 ¥${formatCents(m.total)}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "单笔均值 ¥${formatCents(if (m.previousCount == 0) 0 else m.previous / m.previousCount)} → ¥${formatCents(m.average)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("异常支出候选", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (state.anomalies.isEmpty()) {
            Text("暂无符合规则的候选，或历史记录不足。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        state.anomalies.take(5).forEach { a ->
            val v = a.record
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            state.categoryNames[v.categoryId] ?: "其他",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "¥" + formatCents(v.amount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        "${LocalDate.ofEpochDay(v.day)} · ${formatNoteAndTags(v.note, v.tags)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "通常 ¥${formatCents(a.median)} · 基于该分类 ${a.sampleCount} 笔历史中位数",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        Text("异常仅供核对，不代表不合理支出；缺记也会造成统计变化。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
internal fun CategoryInsightDialog(state: StatsUiState, categoryId: Long, dismiss: () -> Unit) {
    val current = state.currentRecords.filter { it.categoryId == categoryId }
    val previous = state.previousRecords.filter { it.categoryId == categoryId }
    val total = current.sumOf { it.amount }
    val tagRows = expenseMetrics(
        current.flatMap { r -> tagsOf(r.tags).ifEmpty { listOf("未标记") }.map { r.copy(note = it) } },
        previous.flatMap { r -> tagsOf(r.tags).ifEmpty { listOf("未标记") }.map { r.copy(note = it) } }
    ) { it.note }
    val noteRows = expenseMetrics(current, previous) { it.note.trim().ifBlank { "无备注" } }
    var showNotes by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(state.categoryNames[categoryId] ?: "分类明细") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${current.size} 笔 · ¥${formatCents(total)} · 单笔均值 ¥${formatCents(if (current.isEmpty()) 0 else total / current.size)}")
                Text("标签明细", style = MaterialTheme.typography.titleSmall)
                Text("每笔账单可属于多个标签，占比以本分类总额为分母。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                tagRows.forEach { MetricLine(it, total) }
                TextButton(onClick = { showNotes = !showNotes }) {
                    Text(if (showNotes) "收起历史备注" else "查看历史备注分组")
                }
                if (showNotes) noteRows.forEach { MetricLine(it, total) }
                Text("大额账单（最多20笔）", style = MaterialTheme.typography.titleSmall)
                current.sortedByDescending { it.amount }.take(20).forEach { r ->
                    Text("${LocalDate.ofEpochDay(r.day)} · ¥${formatCents(r.amount)} · ${formatNoteAndTags(r.note, r.tags)}")
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("关闭") } }
    )
}

@Composable
private fun MetricLine(m: ExpenseMetric, total: Long) {
    Column {
        Text("${m.label} · ¥${formatCents(m.total)} · ${m.count}笔", fontWeight = FontWeight.Medium)
        Text(
            "均值 ¥${formatCents(m.average)}；上期 ¥${formatCents(m.previous)}（${m.previousCount}笔）" +
                if (total > 0) "；占比 ${"%.1f".format(java.util.Locale.ROOT, m.total * 100.0 / total)}%" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
