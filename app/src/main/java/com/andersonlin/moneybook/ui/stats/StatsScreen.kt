package com.andersonlin.moneybook.ui.stats

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.andersonlin.moneybook.ui.components.CompactCenterTopBar
import com.andersonlin.moneybook.ui.components.TopSnackbarHost
import com.andersonlin.moneybook.ui.components.EmptyState
import com.andersonlin.moneybook.ui.theme.ChartColors
import com.andersonlin.moneybook.util.formatCents
import com.andersonlin.moneybook.util.fullDateLabel
import com.andersonlin.moneybook.util.monthLabel
import com.andersonlin.moneybook.util.SoundFeedback
import com.andersonlin.moneybook.util.SoundType
import com.andersonlin.moneybook.util.tactileScale
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
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
    val view = LocalView.current
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

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactCenterTopBar(
                title = { Text("统计") },
                actions = {
                    IconButton(
                        onClick = { saveChartImage() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = "保存统计图为图片", modifier = Modifier.size(20.dp))
                    }
                },
                height = 40.dp
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isCalendar = state.chartType == StatsViewModel.CHART_CALENDAR
            val displayDateText = remember(state.scale, state.anchorDate, state.chartType, state.calendarMonth) {
                val a = state.anchorDate
                if (isCalendar) {
                    "${state.calendarMonth.year}年${state.calendarMonth.monthValue}月"
                } else {
                    when (state.scale) {
                        StatsViewModel.SCALE_DAY -> {
                            val weekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                            "${a.monthValue}月${a.dayOfMonth}日 ${weekNames[a.dayOfWeek.value - 1]}"
                        }
                        StatsViewModel.SCALE_WEEK -> {
                            val monday = a.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            val sunday = monday.plusDays(6)
                            "${monday.monthValue}.${monday.dayOfMonth} ~ ${sunday.monthValue}.${sunday.dayOfMonth}"
                        }
                        StatsViewModel.SCALE_MONTH -> {
                            "${a.year}年${a.monthValue}月"
                        }
                        StatsViewModel.SCALE_YEAR -> {
                            "${a.year}年"
                        }
                        else -> "${a.year}年${a.monthValue}月"
                    }
                }
            }

            // 1. 日期筛选与维度切换栏（紧凑型置顶）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var periodMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(
                        onClick = {
                            SoundFeedback.play(view, SoundType.LIGHT)
                            periodMenu = true
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            (if (isCalendar) "月" else listOf("日", "周", "月", "年")[state.scale]) + " ▾",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                        listOf("按日", "按周", "按月", "按年").forEachIndexed { i, v ->
                            DropdownMenuItem(
                                text = { Text(v) },
                                onClick = {
                                    SoundFeedback.play(view, SoundType.LIGHT)
                                    viewModel.setScale(i)
                                    if (isCalendar && i != StatsViewModel.SCALE_MONTH) viewModel.setChartType(0)
                                    periodMenu = false
                                }
                            )
                        }
                    }
                }
                IconButton(
                    onClick = {
                        SoundFeedback.play(view, SoundType.LIGHT)
                        viewModel.prev()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上一期", modifier = Modifier.size(18.dp))
                }
                Surface(
                    onClick = {
                        SoundFeedback.play(view, SoundType.LIGHT)
                        val initial = state.anchorDate
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                viewModel.setAnchor(LocalDate.of(year, month + 1, dayOfMonth))
                            },
                            initial.year,
                            initial.monthValue - 1,
                            initial.dayOfMonth
                        ).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = displayDateText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                IconButton(
                    onClick = {
                        SoundFeedback.play(view, SoundType.LIGHT)
                        viewModel.next()
                    },
                    enabled = state.canGoNext,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下一期", modifier = Modifier.size(18.dp))
                }
                val hasTypeMenu = state.chartType == 0 || state.chartType == 3
                if (hasTypeMenu) {
                    var typeMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = {
                                SoundFeedback.play(view, SoundType.LIGHT)
                                typeMenu = true
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(state.typeLabel + " ▾", style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            listOf(0 to "支出", 1 to "收入", -1 to "全部").forEach { (type, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        SoundFeedback.play(view, SoundType.LIGHT)
                                        viewModel.setType(type)
                                        typeMenu = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.width(46.dp))
                }
            }

            // 2. 统计维度 Tab 栏（紧凑型胶囊按钮）：占比、对比、趋势、日历
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("占比", "对比", "趋势", "日历").forEachIndexed { i, label ->
                    val isSelected = state.chartType == i
                    val interactionSource = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            SoundFeedback.play(view, SoundType.LIGHT)
                            viewModel.setChartType(i)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .tactileScale(interactionSource, scaleDown = 0.92f),
                        interactionSource = interactionSource,
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
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
    TopSnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PieContent(state: StatsUiState) {
    var detail by remember { mutableStateOf<Long?>(null) }
    detail?.let { categoryId ->
        CategoryInsightDialog(state, categoryId) { detail = null }
    }
    var showAnalysis by remember { mutableStateOf(false) }
    var showDates by remember { mutableStateOf(false) }
    var expandCategories by remember { mutableStateOf(false) }
    var expandTags by remember { mutableStateOf(false) }
    val view = LocalView.current
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
                Spacer(Modifier.height(48.dp))
            }
        }
    }
    if (showDates) {
        AlertDialog(
            onDismissRequest = { showDates = false },
            title = { Text("比较区间") },
            text = { Text(state.comparisonLabel) },
            confirmButton = { TextButton(onClick = { showDates = false }) { Text("关闭") } }
        )
    }

    val allTagRankings = remember(state.currentRecords) {
        state.currentRecords.flatMap { rec ->
            tagsOf(rec.tags).map { tag -> tag to rec.amount }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, amounts) ->
            amounts.size to amounts.sum()
        }.entries.sortedByDescending { it.value.second }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 1. 顶部总览卡片：左侧环形占比图，右侧关键金额与统计指标
        item(key = "total_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.slices.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DonutChart(slices = state.slices, colors = ChartColors, modifier = Modifier.size(70.dp))
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "本期${state.typeLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "¥ " + formatCents(state.total),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.currentRecords.size} 笔  ·  单笔均值 ¥${formatCents(if (state.currentRecords.isEmpty()) 0 else state.total / state.currentRecords.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val ratioText = state.prevTotal?.takeIf { it > 0 }?.let {
                            "环比 %+.1f%%".format(Locale.ROOT, (state.total - it) * 100.0 / it)
                        } ?: "上期暂无记录"
                        Surface(
                            onClick = { showDates = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = ratioText,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            onClick = { showAnalysis = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "支出分析 ↗",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 2. 分类明细标题
        item(key = "legend_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "分类明细",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (state.slices.size > 3) {
                    TextButton(
                        onClick = {
                            SoundFeedback.play(view, SoundType.LIGHT)
                            expandCategories = !expandCategories
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (expandCategories) "收起 ▴" else "展开全部 (${state.slices.size}) ▾",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // 3. 各分类占比条目（默认前 3 条，超出可展开）
        val visibleSlices = if (expandCategories) state.slices else state.slices.take(3)
        items(visibleSlices, key = { it.category.id }) { slice ->
            StatLegendRow(
                slice = slice,
                color = ChartColors[state.slices.indexOf(slice) % ChartColors.size],
                total = state.total,
                onClick = { detail = slice.category.id }
            )
        }

        // 4. 本期高频标签偏好（自然充实下方空间，条目少时也不显空旷）
        if (allTagRankings.isNotEmpty()) {
            item(key = "tags_preference_card") {
                val visibleTags = if (expandTags) allTagRankings else allTagRankings.take(3)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "本期标签偏好",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (allTagRankings.size > 3) {
                                TextButton(
                                    onClick = {
                                        SoundFeedback.play(view, SoundType.LIGHT)
                                        expandTags = !expandTags
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = if (expandTags) "收起 ▴" else "展开全部 (${allTagRankings.size}) ▾",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            } else {
                                Text(
                                    text = "共 ${allTagRankings.size} 个标签",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        visibleTags.forEach { (tag, info) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = "# $tag",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${info.first} 笔",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "¥" + formatCents(info.second),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. 支出结构特征摘要卡片
        item(key = "spending_summary_card") {
            val maxRecord = state.currentRecords.maxByOrNull { it.amount }
            val topCategory = state.slices.firstOrNull()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "支出结构摘要",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("首要支出分类", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = topCategory?.let { "${it.category.icon} ${it.category.name} (${String.format(Locale.ROOT, "%.1f%%", if (state.total > 0) it.total * 100.0 / state.total else 0.0)})" } ?: "无",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (maxRecord != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("最大单笔支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = (state.categoryNames[maxRecord.categoryId] ?: "支出") + " ¥" + formatCents(maxRecord.amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "bottom_space") { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun LineContent(state: StatsUiState, expenseColor: Color, incomeColor: Color) {
    if (!state.chartHasData) {
        EmptyBox("该时间段暂无收支记录")
        return
    }
    val totalExpense = state.chartPoints.sumOf { it.expense }
    val avgExpense = if (state.chartPoints.isNotEmpty()) totalExpense / state.chartPoints.size else 0L
    val peakPoint = state.chartPoints.maxByOrNull { it.expense }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. 三个核心指标 KPI 卡片（铺满顶部）
        item(key = "kpi_row") {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    title = "总支出",
                    value = "¥" + formatCents(totalExpense),
                    subtitle = "${state.currentRecords.size}笔账单",
                    color = expenseColor
                )
                MetricCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    title = "周期均值",
                    value = "¥" + formatCents(avgExpense),
                    subtitle = "按日折算",
                    color = MaterialTheme.colorScheme.onSurface
                )
                MetricCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    title = "单日峰值",
                    value = if (peakPoint != null && peakPoint.expense > 0) "¥" + formatCents(peakPoint.expense) else "¥0.00",
                    subtitle = peakPoint?.label?.takeIf { peakPoint.expense > 0 } ?: "平稳",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // 2. 趋势折线图卡片（优雅圆角卡片，含渐变投影）
        item(key = "chart_card") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "支出走势曲线",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.chartWindowLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ExpenseTrendChart(
                        points = state.chartPoints,
                        expenseColor = expenseColor,
                        incomeColor = incomeColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        // 3. 重点支出节点明细
        item(key = "nodes_header") {
            Text(
                text = "各节点支出明细",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        val activePoints = state.chartPoints.filter { it.expense > 0 }.reversed()
        if (activePoints.isEmpty()) {
            item(key = "no_active_points") {
                Text(
                    text = "所选周期内暂无大额波动记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(activePoints.take(2), key = { "trend_pt_" + it.label }) { pt ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (pt == peakPoint) expenseColor else MaterialTheme.colorScheme.outline)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = pt.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (pt == peakPoint) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (pt == peakPoint) {
                            Text(
                                text = "峰值 ",
                                style = MaterialTheme.typography.labelSmall,
                                color = expenseColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "-¥" + formatCents(pt.expense),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = expenseColor
                        )
                    }
                }
            }
        }

        // 4. 当活跃节点少于 3 个时，补充展示走势特征摘要卡片，铺满整个屏幕
        if (activePoints.size <= 3) {
            item(key = "trend_insight_card") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("波动特征解读", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("周期记录总笔数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${state.currentRecords.size} 笔账单", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("峰值支出占比", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val peakPct = if (totalExpense > 0 && peakPoint != null) (peakPoint.expense * 100.0 / totalExpense) else 0.0
                                Text(String.format(Locale.ROOT, "%.1f%%", peakPct), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = expenseColor)
                            }
                        }
                    }
                }
            }
        }
        item(key = "bottom_space") { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            }
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
            }
        }
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
    val view = LocalView.current
    val today = LocalDate.now()
    val isCurrentMonth = today.year == state.calendarMonth.year && today.monthValue == state.calendarMonth.monthValue

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

    // 默认选中：若当前月则选今天；否则选金额最大的一天或最后一天
    var selectedDay by remember(state.calendarMonth) {
        val initial = if (isCurrentMonth) {
            state.calendarDays.firstOrNull { it.date == today }
        } else {
            state.calendarDays.maxByOrNull { dayValue(it) } ?: state.calendarDays.lastOrNull()
        }
        mutableStateOf(initial)
    }

    val totalMonthExpense = remember(state.calendarDays) { state.calendarDays.sumOf { it.expense } }
    val daysRecorded = remember(state.calendarDays) { state.calendarDays.count { it.expense > 0 } }
    val maxDayExpense = remember(state.calendarDays) { state.calendarDays.maxByOrNull { it.expense } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 1. 星期头（周一为首）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
        ) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. 日历网格（紧凑单元格高度 36dp，确保底部明细卡片完整呈现在一屏内）
        val firstOffset = state.calendarDays.firstOrNull()
            ?.date?.dayOfWeek?.value?.minus(1) ?: 0
        val cells: List<CalendarDay?> =
            List(firstOffset) { null } + state.calendarDays

        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { index ->
                    val day = week.getOrNull(index)
                    val isSelected = day != null && selectedDay?.date == day.date
                    val isToday = day != null && day.date == today
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clickable(enabled = day != null) {
                                day?.let {
                                    SoundFeedback.play(view, SoundType.LIGHT)
                                    selectedDay = it
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val value = dayValue(day)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .then(
                                            when {
                                                isSelected -> Modifier.background(MaterialTheme.colorScheme.primary)
                                                isToday -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                else -> Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.date.dayOfMonth.toString(),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            isToday -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                if (value > 0) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(if (value > maxValue * 0.4f) 4.5.dp else 3.5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else circleColor.copy(alpha = 0.9f)
                                            )
                                    )
                                } else {
                                    Spacer(Modifier.height(2.dp + 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. 月度开销概览卡片（置于日历下方，文案优化）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "月度开销概览",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "圆点标识支出日",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = when (state.type) {
                                StatsViewModel.TYPE_INCOME -> "本月收入"
                                StatsViewModel.TYPE_ALL -> "本月总流水"
                                else -> "本月累计支出"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "¥ " + formatCents(
                                when (state.type) {
                                    StatsViewModel.TYPE_INCOME -> state.calendarDays.sumOf { it.income }
                                    StatsViewModel.TYPE_ALL -> state.calendarDays.sumOf { it.expense + it.income }
                                    else -> totalMonthExpense
                                }
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (state.type) {
                                StatsViewModel.TYPE_INCOME -> incomeColor
                                StatsViewModel.TYPE_ALL -> MaterialTheme.colorScheme.tertiary
                                else -> expenseColor
                            }
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "已记账 $daysRecorded 天" + (if (daysRecorded > 0) " · 日均 ¥${formatCents(totalMonthExpense / daysRecorded)}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (maxDayExpense != null && maxDayExpense.expense > 0) {
                            Text(
                                text = "单日最高 ¥${formatCents(maxDayExpense.expense)} (${maxDayExpense.date.dayOfMonth}日)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. 当天账单明细卡片（内嵌展示，充实页面底部，替代打扰弹窗，完整呈现在当前屏）
        selectedDay?.let { day ->
            val dayEpoch = day.date.toEpochDay()
            val dayRecords = remember(dayEpoch, state.currentRecords) {
                state.currentRecords.filter { it.day == dayEpoch }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val weekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                        val dayOfWeekName = weekNames[day.date.dayOfWeek.value - 1]
                        Column {
                            Text(
                                text = "${day.date.monthValue}月${day.date.dayOfMonth}日 $dayOfWeekName",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "支出 -¥${formatCents(day.expense)}" + if (day.income > 0) " · 收入 +¥${formatCents(day.income)}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (day.expense > 0) expenseColor else MaterialTheme.colorScheme.outline
                            )
                        }
                        Surface(
                            onClick = {
                                SoundFeedback.play(view, SoundType.CRISP)
                                onAddBillForDate(dayEpoch)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "+ 当天记一笔",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (dayRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "当天暂无账单记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            dayRecords.take(3).forEach { record ->
                                val cat = state.categoryMap[record.categoryId]
                                val catIcon = cat?.icon ?: "💰"
                                val catName = cat?.name ?: (state.categoryNames[record.categoryId] ?: "其他")
                                val noteAndTags = formatNoteAndTags(record.note, record.tags)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$catIcon $catName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = noteAndTags,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = (if (record.type == 1) "+¥" else "-¥") + formatCents(record.amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (record.type == 1) incomeColor else expenseColor
                                    )
                                }
                            }
                            if (dayRecords.size > 3) {
                                Text(
                                    text = "… 等共 ${dayRecords.size} 笔账单",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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
    val percent = if (total > 0) (slice.total.toFloat() / total).coerceIn(0f, 1f) else 0f
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${slice.category.icon} ${slice.category.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(3.dp))
                // 细比例条
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percent)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(color)
                    )
                }
            }
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
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
