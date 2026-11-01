package com.andersonlin.moneybook.ui.stats

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.MainActivity
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.ui.components.EmptyState
import com.andersonlin.moneybook.ui.theme.ChartColors
import com.andersonlin.moneybook.util.formatCents
import com.andersonlin.moneybook.util.fullDateLabel
import com.andersonlin.moneybook.util.monthLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onAddBillForDate: (Long) -> Unit,
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expenseColor = MaterialTheme.colorScheme.error
    val incomeColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val activity = remember(context) {
        var current: Context? = context
        var result: MainActivity? = null
        while (current != null && result == null) {
            when (current) {
                is MainActivity -> result = current
                is ContextWrapper -> current = current.baseContext
                else -> current = null
            }
        }
        result
    }
    val scope = rememberCoroutineScope()
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun saveChartImage() {
        val fileName = "moneybook_stats_" +
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".png"
        activity?.createDocument("image/png", fileName) { uri ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    val ok = runCatching {
                        val bitmap = ChartBitmapRenderer.render(state, 1080, 1280)
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } != null
                    }.getOrDefault(false)
                    saveMessage = if (ok) "统计图已保存" else "保存失败，请重试"
                }
            }
        }
    }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            saveMessage = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("统计") },
                actions = {
                    IconButton(onClick = { saveChartImage() }) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = "保存统计图为图片")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("占比","对比","趋势","日历").forEachIndexed { i,label ->
                    TextButton(onClick={viewModel.setChartType(i)},modifier=Modifier.weight(1f),
                        colors=ButtonDefaults.textButtonColors(containerColor=if(state.chartType==i) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) {
                        Text(label,maxLines=1,style=MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isMonthOnly = state.chartType == StatsViewModel.CHART_CALENDAR || state.chartType == StatsViewModel.CHART_BAR
                var periodMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { periodMenu = true }) {
                        Text(
                            (if (isMonthOnly) "月" else listOf("日", "周", "月", "年")[state.scale]) + " ▾",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                        listOf("按日", "按周", "按月", "按年").forEachIndexed { i, v ->
                            DropdownMenuItem(
                                text = { Text(v) },
                                onClick = {
                                    viewModel.setScale(i)
                                    if (isMonthOnly) viewModel.setChartType(0)
                                    periodMenu = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = viewModel::prev) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上一期")
                }
                Text(
                    text = if (isMonthOnly || state.scale == 2) monthLabel(state.calendarMonth) else state.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = viewModel::next, enabled = state.canGoNext) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下一期")
                }
                val hasTypeMenu = state.chartType == 0 || state.chartType == 3
                if (hasTypeMenu) {
                    var typeMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { typeMenu = true }) {
                            Text(state.typeLabel + " ▾", style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            listOf(0 to "支出", 1 to "收入", -1 to "全部").forEach { (type, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setType(type)
                                        typeMenu = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.width(56.dp))
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (state.chartType) {
                    StatsViewModel.CHART_PIE -> PieContent(state)
                    StatsViewModel.CHART_BAR -> MonthComparisonContent(state)
                    StatsViewModel.CHART_CALENDAR -> CalendarContent(
                        state,
                        viewModel,
                        expenseColor,
                        incomeColor,
                        onAddBillForDate
                    )
                    else -> LineContent(state, expenseColor, incomeColor)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PieContent(state: StatsUiState) {
    var detail by remember { mutableStateOf<Long?>(null) }
    detail?.let { categoryId ->
        CategoryInsightDialog(state,categoryId) { detail=null }
    }
    var showChart by remember {mutableStateOf(false)}
    var showAnalysis by remember {mutableStateOf(false)}
    var showDates by remember {mutableStateOf(false)}
    if (showAnalysis) {
        ModalBottomSheet(
            onDismissRequest = { showAnalysis = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = BottomSheetDefaults.ScrimColor
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.typeLabel}分析",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showAnalysis = false }) {
                        Text("关闭", style = MaterialTheme.typography.labelLarge)
                    }
                }
                SpendingInsightSummary(state, true)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    if(showDates) AlertDialog(onDismissRequest={showDates=false},title={Text("比较区间")},text={Text(state.comparisonLabel)},confirmButton={TextButton(onClick={showDates=false}){Text("关闭")}})
    LazyColumn(Modifier.fillMaxSize()) {
        item(key="total") {
            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("本期${state.typeLabel}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥ "+formatCents(state.total),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold)
                Text("${state.currentRecords.size} 笔  ·  单笔均值 ¥${formatCents(if(state.currentRecords.isEmpty()) 0 else state.total/state.currentRecords.size)}",style=MaterialTheme.typography.bodySmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SuggestionChip(
                        onClick = { showDates = true },
                        label = {
                            Text(
                                state.prevTotal?.takeIf { it > 0 }?.let {
                                    "环比 %+.1f%%".format(Locale.ROOT, (state.total - it) * 100.0 / it)
                                } ?: "上期暂无记录",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                    SuggestionChip(
                        onClick = { showAnalysis = true },
                        label = { Text("支出分析 ↗", style = MaterialTheme.typography.labelMedium) }
                    )
                    SuggestionChip(
                        onClick = { showChart = !showChart },
                        label = { Text(if (showChart) "收起图表" else "占比图 ▾", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
        if(showChart && state.slices.isNotEmpty()) item(key="chart") {
            Box(Modifier.fillMaxWidth().padding(8.dp),contentAlignment=Alignment.Center) {
                DonutChart(slices=state.slices,colors=ChartColors,modifier=Modifier.size(160.dp))
            }
        }
        item(key = "legend_header") {
            Text(
                text = "分类明细",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        items(state.slices, key = { it.category.id }) { slice ->
            StatLegendRow(
                slice = slice,
                color = ChartColors[state.slices.indexOf(slice) % ChartColors.size],
                total = state.total,
                onClick={ detail=slice.category.id }
            )
        }
        item(key = "bottom_space") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun BarContent(state: StatsUiState, expenseColor: Color, incomeColor: Color) {
    if (!state.chartHasData) {
        EmptyBox("该时间段暂无收支记录")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "title") {
            Text(
                text = state.chartWindowLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item(key = "chart") {
            Column {
                BarChart(
                    points = state.chartPoints,
                    expenseColor = expenseColor,
                    incomeColor = incomeColor,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                ChartLegend(expenseColor, incomeColor)
            }
        }
        items(state.chartPoints.reversed(), key = { "detail_" + it.label }) { point ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "-" + formatCents(point.expense),
                    style = MaterialTheme.typography.bodyMedium,
                    color = expenseColor
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "+" + formatCents(point.income),
                    style = MaterialTheme.typography.bodyMedium,
                    color = incomeColor
                )
            }
        }
    }
}

@Composable
private fun LineContent(state: StatsUiState, expenseColor: Color, incomeColor: Color) {
    if (!state.chartHasData) {
        EmptyBox("该时间段暂无收支记录")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "title") {
            Text(
                text = state.chartWindowLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item(key = "chart") {
            Column {
                ExpenseTrendChart(
                    points = state.chartPoints,
                    expenseColor = expenseColor,
                    incomeColor = incomeColor,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                Text("每日支出（元）",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(horizontal=16.dp))
            }
        }
        item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CalendarContent(
    state: StatsUiState,
    viewModel: StatsViewModel,
    expenseColor: Color,
    incomeColor: Color,
    onAddBillForDate: (Long) -> Unit
) {
    var selectedDay by remember { mutableStateOf<CalendarDay?>(null) }
    val circleColor = when (state.type) {
        StatsViewModel.TYPE_INCOME -> incomeColor
        StatsViewModel.TYPE_ALL -> MaterialTheme.colorScheme.tertiary
        else -> expenseColor
    }
    val dayValue: (CalendarDay) -> Long = { day ->
        when (state.type) {
            StatsViewModel.TYPE_INCOME -> day.income
            StatsViewModel.TYPE_ALL -> day.expense + day.income
            else -> day.expense
        }
    }
    val maxValue = state.calendarDays.maxOfOrNull(dayValue)?.coerceAtLeast(1L) ?: 1L
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = when (state.type) {
                StatsViewModel.TYPE_INCOME -> "本月收入 ¥" + formatCents(state.calendarDays.sumOf { it.income })
                StatsViewModel.TYPE_ALL -> "本月总流水 ¥" + formatCents(state.calendarDays.sumOf { it.expense + it.income })
                else -> "本月支出 ¥" + formatCents(state.calendarDays.sumOf { it.expense })
            },
            style = MaterialTheme.typography.titleMedium
        )
        // 图例说明
        Text(
            text = "圆圈大小表示当天金额",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        // 星期头（周一为首）
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // 日历网格
        val firstOffset = state.calendarDays.firstOrNull()
            ?.date?.dayOfWeek?.value?.minus(1) ?: 0
        val cells: List<CalendarDay?> =
            List(firstOffset) { null } + state.calendarDays
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { index ->
                    val day = week.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable(enabled = day != null) {
                                day?.let { selectedDay = it }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val value = dayValue(day)
                            val radius = if (value > 0) {
                                (5f + 15f * kotlin.math.sqrt(value.toFloat() / maxValue)).dp
                            } else {
                                0.dp
                            }
                            if (radius > 0.dp) {
                                Box(
                                    modifier = Modifier
                                        .size(radius * 2)
                                        .clip(CircleShape)
                                        .background(circleColor.copy(alpha = 0.35f))
                                )
                            }
                            Text(
                                text = day.date.dayOfMonth.toString(),
                                fontSize = 12.sp,
                                fontWeight = if (value > 0) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    day.date == today -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    selectedDay?.let { day ->
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            title = { Text(fullDateLabel(day.date.toEpochDay())) },
            text = {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("支出", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            text = "-" + formatCents(day.expense),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = expenseColor
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("收入", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            text = "+" + formatCents(day.income),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = incomeColor
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        selectedDay = null
                        onAddBillForDate(day.date.toEpochDay())
                    }) { Text("当天记一笔") }
                    TextButton(onClick = { selectedDay = null }) { Text("关闭") }
                }
            }
        )
    }
}

@Composable
private fun ChartLegend(expenseColor: Color, incomeColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(expenseColor)
        )
        Spacer(Modifier.width(6.dp))
        Text("支出", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(20.dp))
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(incomeColor)
        )
        Spacer(Modifier.width(6.dp))
        Text("收入", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyBox(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(icon = Icons.Filled.PieChart, title = title)
    }
}

@Composable
private fun StatLegendRow(slice: StatSlice, color: Color, total: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick=onClick)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${slice.category.icon} ${slice.category.name}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (slice.category.type == Bill.TYPE_INCOME) "+" else "-") +
                    formatCents(slice.total),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (slice.category.type == Bill.TYPE_INCOME) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Text(
                text = if (total > 0) {
                    String.format(Locale.US, "%.1f%%", slice.total * 100.0 / total)
                } else {
                    "0%"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
