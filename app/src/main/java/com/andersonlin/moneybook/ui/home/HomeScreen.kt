package com.andersonlin.moneybook.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.ui.components.BillRow
import com.andersonlin.moneybook.ui.components.EmptyState
import com.andersonlin.moneybook.ui.goal.toProgress
import com.andersonlin.moneybook.util.formatCents
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddBill: () -> Unit,
    onViewAll: () -> Unit,
    onBillClick: (Long) -> Unit,
    onSetBudget: () -> Unit,
    onManageLedgers: () -> Unit,
    onOpenGoals: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLedgerDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        item(key = "summary", contentType = "summary") {
            MonthSummaryCard(
                month = state.month,
                income = state.income,
                expense = state.expense,
                todayExpense = state.todayExpense,
                ledgerName = state.ledgerName,
                onSwitchLedger = { showLedgerDialog = true },
                budgetCents = state.budgetCents,
                categoryAlerts = state.categoryAlerts,
                onSetBudget = onSetBudget
            )
        }

        if (state.isLoaded && state.recentBills.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    icon = Icons.Filled.AccountBalanceWallet,
                    title = "还没有账单",
                    subtitle = "点击右下角 + 记一笔吧"
                )
            }
        } else if (state.recentBills.isNotEmpty()) {
            item(key = "recent_header", contentType = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最近账单",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onViewAll) { Text("全部") }
                }
            }
            items(
                items = state.recentBills,
                key = { it.id },
                contentType = { "bill" }
            ) { bill ->
                BillRow(
                    bill = bill,
                    category = state.categories[bill.categoryId],
                    account = state.accounts[bill.accountId],
                    onClick = { onBillClick(bill.id) }
                )
            }
        }
    }

    // 账本切换
    if (showLedgerDialog) {
        AlertDialog(
            onDismissRequest = { showLedgerDialog = false },
            title = { Text("切换账本") },
            text = {
                Column {
                    state.ledgers.forEach { ledger ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setActiveLedger(ledger.id)
                                    showLedgerDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${ledger.icon} ${ledger.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (ledger.id == state.ledgerId) {
                                Text(
                                    text = "使用中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onManageLedgers) { Text("管理账本") }
            },
            dismissButton = {
                TextButton(onClick = { showLedgerDialog = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun MonthSummaryCard(
    month: YearMonth,
    income: Long,
    expense: Long,
    todayExpense: Long,
    ledgerName: String,
    onSwitchLedger: () -> Unit,
    budgetCents: Long?,
    categoryAlerts: List<CategoryAlert>,
    onSetBudget: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // 顶行：年月 + 弱化收敛的账本切换胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${month.year}年${month.monthValue}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Surface(
                    onClick = onSwitchLedger,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = ledgerName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "切换账本",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 主数值行：月支出 + 今日支出并列
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "支出 ¥" + formatCents(expense),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "今日支出",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    Text(
                        text = "¥" + formatCents(todayExpense),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            // 预算进度 / 超支提醒
            val budget = budgetCents
            if (budget != null && budget > 0) {
                Spacer(Modifier.height(10.dp))
                val ratio = (expense.toDouble() / budget).coerceIn(0.0, 1.0)
                LinearProgressIndicator(
                    progress = { ratio.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expense > budget) {
                            "已超支 ${formatCents(expense - budget)}"
                        } else {
                            "剩余 ${formatCents(budget - expense)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (expense > budget) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "预算 ${formatCents(budget)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onSetBudget,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "+ 设置本月预算",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // 分类预算提醒（接近或超支）
            categoryAlerts.forEach { alert ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (alert.over) {
                        "${alert.icon} ${alert.name} 超支 ${formatCents(alert.usedCents - alert.budgetCents)}"
                    } else {
                        "${alert.icon} ${alert.name} 接近预算（${formatCents(alert.usedCents)}/${formatCents(alert.budgetCents)}）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (alert.over) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    }
                )
            }
        }
    }
}
