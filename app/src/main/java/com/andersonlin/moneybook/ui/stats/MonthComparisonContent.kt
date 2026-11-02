package com.andersonlin.moneybook.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val comparison = state.periodComparison
    val rows = comparison.items
    var selectedIndex by remember(comparison) {
        val todayIdx = rows.indexOfLast { (it.current != null && it.current > 0) || it.index == state.anchorDate.dayOfMonth }
        mutableStateOf(if (todayIdx >= 0) todayIdx else 0)
    }
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("收支对比", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comparison.currentTitle, color = currentColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(comparison.previousTitle, color = previousColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("两侧共用刻度 · 最高 ¥${formatCents(maxValue)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = "支出对比，上方为所选期，下方为对比期。" }
                .pointerInput(rows) {
                    detectTapGestures { p ->
                        if (rows.isNotEmpty()) {
                            selectedIndex = (p.x / size.width * rows.size).toInt().coerceIn(0, rows.lastIndex)
                        }
                    }
                }
        ) {
            if (rows.isEmpty()) return@Canvas
            val center = size.height / 2
            val bandHalf = 8.dp.toPx()
            val half = center - bandHalf - 2.dp.toPx()
            val step = size.width / rows.size

            // 绘制中央刻度带与上下边界线
            drawRect(
                color = bandColor,
                topLeft = Offset(0f, center - bandHalf),
                size = Size(size.width, bandHalf * 2)
            )
            drawLine(axisColor, Offset(0f, center - bandHalf), Offset(size.width, center - bandHalf), 1.dp.toPx())
            drawLine(axisColor, Offset(0f, center + bandHalf), Offset(size.width, center + bandHalf), 1.dp.toPx())

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor.toArgb()
                textSize = (if (rows.size <= 7) 11.sp else 8.5.sp).toPx()
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val fontMetrics = paint.fontMetrics
            val textBaseY = center - (fontMetrics.ascent + fontMetrics.descent) / 2

            rows.forEachIndexed { index, row ->
                val x = step * index
                val barWidth = (step * (if (rows.size <= 7) 0.5f else 0.65f)).coerceAtLeast(2.dp.toPx())

                // 上方：所选期柱状
                row.current?.takeIf { it > 0 }?.let { value ->
                    val h = (half * value / maxValue).coerceAtLeast(2.dp.toPx())
                    drawRect(currentColor, Offset(x + (step - barWidth) / 2, center - bandHalf - 1.dp.toPx() - h), Size(barWidth, h))
                }
                // 下方：对比期柱状
                row.previous?.takeIf { it > 0 }?.let { value ->
                    val h = (half * value / maxValue).coerceAtLeast(2.dp.toPx())
                    drawRect(previousColor, Offset(x + (step - barWidth) / 2, center + bandHalf + 1.dp.toPx()), Size(barWidth, h))
                }

                // 选中指示列
                if (index == selectedIndex) {
                    drawRect(
                        color = textColor.copy(alpha = 0.12f),
                        topLeft = Offset(x, 0f),
                        size = Size(step, size.height)
                    )
                }

                // 刻度文字：<=12 项全画，多项抽稀
                val isDrawLabel = when {
                    rows.size <= 12 -> true
                    row.index == 1 || (row.index % 5 == 0 && (rows.size - row.index) >= 2) || row.index == rows.size -> true
                    else -> false
                }
                if (isDrawLabel) {
                    drawContext.canvas.nativeCanvas.drawText(row.label, x + step / 2, textBaseY, paint)
                }
            }
        }

        rows.getOrNull(selectedIndex)?.let { item ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { if (selectedIndex > 0) selectedIndex-- },
                            enabled = selectedIndex > 0,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("前一个", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(item.subLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = { if (selectedIndex < rows.lastIndex) selectedIndex++ },
                            enabled = selectedIndex < rows.lastIndex,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("后一个", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("本期（${item.subLabel}）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                item.current?.let { "¥" + formatCents(it) } ?: "未到或无数据",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = currentColor
                            )
                            if (item.current != null) Text("${item.currentCount}笔记录", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("上期（${item.prevSubLabel}）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                item.previous?.let { "¥" + formatCents(it) } ?: "无数据",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = previousColor
                            )
                            if (item.previous != null) Text("${item.previousCount}笔记录", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (item.current != null && item.previous != null) {
                        val delta = item.current - item.previous
                        val deltaColor = if (delta > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Text(
                            text = "较上期 ${if (delta >= 0) "+" else "−"}¥${formatCents(kotlin.math.abs(delta))}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = deltaColor
                        )
                    }
                }
            }
        }

        // 全周期累计对比卡片：充实下方空间，一屏完整呈现
        val currentTotal = rows.mapNotNull { it.current }.sum()
        val previousTotal = rows.mapNotNull { it.previous }.sum()
        val periodDelta = currentTotal - previousTotal
        val periodDeltaPercent = if (previousTotal > 0) (periodDelta * 100.0 / previousTotal) else 0.0
        val deltaColor = if (periodDelta > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

        val validCurrentDays = rows.count { it.current != null }
        val currentDailyAvg = if (validCurrentDays > 0) currentTotal / validCurrentDays else 0L
        val validPrevDays = rows.count { it.previous != null }
        val prevDailyAvg = if (validPrevDays > 0) previousTotal / validPrevDays else 0L

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("全周期累计对比", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("本期累计支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(1.dp))
                        Text("¥" + formatCents(currentTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = currentColor)
                        Text("日均 ¥${formatCents(currentDailyAvg)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("上期累计支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(1.dp))
                        Text("¥" + formatCents(previousTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = previousColor)
                        Text("日均 ¥${formatCents(prevDailyAvg)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("两期差额", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${if (periodDelta >= 0) "较上期 +" else "较上期 −"}¥${formatCents(kotlin.math.abs(periodDelta))} (${if (periodDelta >= 0) "+" else ""}${"%.1f".format(periodDeltaPercent)}%)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = deltaColor
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
