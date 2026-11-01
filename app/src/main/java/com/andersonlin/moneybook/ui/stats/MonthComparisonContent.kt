package com.andersonlin.moneybook.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andersonlin.moneybook.util.formatCents

@Composable
internal fun MonthComparisonContent(state: StatsUiState) {
    val rows = state.monthComparison
    var selected by remember(state.calendarMonth) { mutableStateOf(1) }
    val currentColor = MaterialTheme.colorScheme.primary
    val previousColor = MaterialTheme.colorScheme.tertiary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val bandColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val maxValue = rows.maxOfOrNull { maxOf(it.current ?: 0, it.previous ?: 0) }?.coerceAtLeast(1) ?: 1

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("同日支出对比", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("↑ 所选月（${state.calendarMonth.monthValue}月）", color = currentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text("↓ 上个月（${state.calendarMonth.minusMonths(1).monthValue}月）", color = previousColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        Text("两侧共用刻度 · 单日最高 ¥${formatCents(maxValue)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .semantics { contentDescription = "同日支出对比，上方为所选月，下方为上月。可使用下方前一天、后一天查看数值。" }
                .pointerInput(rows) {
                    detectTapGestures { p ->
                        if (rows.isNotEmpty()) {
                            selected = (p.x / size.width * rows.size).toInt().coerceIn(0, rows.lastIndex) + 1
                        }
                    }
                }
        ) {
            if (rows.isEmpty()) return@Canvas
            val center = size.height / 2
            val bandHalf = 11.dp.toPx()
            val half = center - bandHalf - 12.dp.toPx()
            val step = size.width / rows.size

            // 绘制中央日期刻度带与上下边界线
            drawRect(
                color = bandColor,
                topLeft = Offset(0f, center - bandHalf),
                size = Size(size.width, bandHalf * 2)
            )
            drawLine(axisColor, Offset(0f, center - bandHalf), Offset(size.width, center - bandHalf), 1.dp.toPx())
            drawLine(axisColor, Offset(0f, center + bandHalf), Offset(size.width, center + bandHalf), 1.dp.toPx())

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor.toArgb()
                textSize = 9.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val fontMetrics = paint.fontMetrics
            val textBaseY = center - (fontMetrics.ascent + fontMetrics.descent) / 2

            rows.forEachIndexed { index, row ->
                val x = step * index
                val barWidth = step * 0.65f

                // 上方：所选月份支出柱状
                row.current?.takeIf { it > 0 }?.let { value ->
                    val h = (half * value / maxValue).coerceAtLeast(2.dp.toPx())
                    drawRect(currentColor, Offset(x + step * 0.175f, center - bandHalf - 2.dp.toPx() - h), Size(barWidth, h))
                }
                // 下方：上月份支出柱状
                row.previous?.takeIf { it > 0 }?.let { value ->
                    val h = (half * value / maxValue).coerceAtLeast(2.dp.toPx())
                    drawRect(previousColor, Offset(x + step * 0.175f, center + bandHalf + 2.dp.toPx()), Size(barWidth, h))
                }

                // 选中日期指示列
                if (row.day == selected) {
                    drawRect(
                        color = textColor.copy(alpha = 0.12f),
                        topLeft = Offset(x, 0f),
                        size = Size(step, size.height)
                    )
                }

                // 日期文字：防重叠抽稀规则（1号、逢5号且离月末>=2天、以及月末最后一天）
                val isDrawDay = row.day == 1 || (row.day % 5 == 0 && (rows.size - row.day) >= 2) || row.day == rows.size
                if (isDrawDay) {
                    drawContext.canvas.nativeCanvas.drawText(row.day.toString(), x + step / 2, textBaseY, paint)
                }
            }
        }

        rows.getOrNull(selected - 1)?.let { day ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { selected-- }, enabled = selected > 1) { Text("前一天") }
                        Text("${day.day}号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { selected++ }, enabled = selected < rows.size) { Text("后一天") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${state.calendarMonth.monthValue}月${day.day}日", style = MaterialTheme.typography.labelMedium)
                            Text(
                                day.current?.let { "¥" + formatCents(it) } ?: "未到或无此日期",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = currentColor
                            )
                            if (day.current != null) Text("${day.currentCount}笔记录", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${state.calendarMonth.minusMonths(1).monthValue}月${day.day}日", style = MaterialTheme.typography.labelMedium)
                            Text(
                                day.previous?.let { "¥" + formatCents(it) } ?: "无此日期",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = previousColor
                            )
                            if (day.previous != null) Text("${day.previousCount}笔记录", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (day.current != null && day.previous != null) {
                        val delta = day.current - day.previous
                        val deltaColor = if (delta > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Text(
                            text = "记录金额较上月同日 ${if (delta >= 0) "+" else "−"}¥${formatCents(kotlin.math.abs(delta))}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = deltaColor
                        )
                    }
                }
            }
        }
        Text("横向按日期对齐；点柱子或按按钮查看同日金额。未到来的日期与不存在的日期留空。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
    }
}
