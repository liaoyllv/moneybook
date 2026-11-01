package com.andersonlin.moneybook.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.ui.AppViewModelProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.ui.account.AccountScreen
import com.andersonlin.moneybook.ui.bill.AddEditBillScreen
import com.andersonlin.moneybook.ui.bill.BillListScreen
import com.andersonlin.moneybook.ui.bill.BillListViewModel
import com.andersonlin.moneybook.ui.budget.BudgetScreen
import com.andersonlin.moneybook.ui.category.CategoryScreen
import com.andersonlin.moneybook.ui.categorybudget.CategoryBudgetScreen
import com.andersonlin.moneybook.ui.goal.GoalScreen
import com.andersonlin.moneybook.ui.home.HomeScreen
import com.andersonlin.moneybook.ui.home.HomeViewModel
import com.andersonlin.moneybook.ui.ledger.LedgerScreen
import com.andersonlin.moneybook.ui.lock.LockSettingsScreen
import com.andersonlin.moneybook.ui.onboarding.OnboardingScreen
import com.andersonlin.moneybook.ui.recurring.RecurringScreen
import com.andersonlin.moneybook.ui.reminder.ReminderScreen
import com.andersonlin.moneybook.ui.settings.AboutScreen
import com.andersonlin.moneybook.ui.settings.SettingsScreen
import com.andersonlin.moneybook.ui.settings.SettingsViewModel
import com.andersonlin.moneybook.ui.stats.StatsScreen
import com.andersonlin.moneybook.ui.stats.StatsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 路由定义 */
object Routes {
    const val MAIN = "main"
    const val HOME = "home"
    const val BILLS = "bills"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val ACCOUNTS = "accounts"
    const val BUDGET = "budget"
    const val RECURRING = "recurring"
    const val LOCK = "lock"
    const val LEDGERS = "ledgers"
    const val GOALS = "goals"
    const val REMINDER = "reminder"
    const val CATEGORY_BUDGET = "category_budget"
    const val ONBOARDING = "onboarding"
    const val ABOUT = "about"
    const val ADD_EDIT = "add_edit?billId={billId}&type={type}&date={date}"

    /** billId 为 null 或 -1 表示新增；type 为新账单默认类型；date 为预填日期（0=今天） */
    fun addEdit(billId: Long? = null, type: Int = Bill.TYPE_EXPENSE, date: Long = 0L): String =
        "add_edit?billId=${billId ?: -1L}&type=$type&date=$date"
}

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val topDestinations = listOf(
    TopDestination(Routes.HOME, "首页", Icons.Filled.Home),
    TopDestination(Routes.BILLS, "账单", Icons.AutoMirrored.Filled.ReceiptLong),
    TopDestination(Routes.STATS, "统计", Icons.Filled.PieChart),
    TopDestination(Routes.SETTINGS, "设置", Icons.Filled.Settings)
)

/** 主界面容器：常驻底部栏与悬浮记账按钮，页面切换与弹入弹出不闪烁、不位移 */
@Composable
fun MainScreen(
    initialTab: Int = 0,
    onNavigateToAddEdit: (billId: Long?, type: Int, date: Long) -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToCategoryBudget: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    onNavigateToLedgers: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToReminder: () -> Unit,
    onNavigateToLock: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    // 预挂载四个主 Tab 的 ViewModel 并保持常驻，使「首页 / 账单 / 统计 / 设置」任意切换达到 0ms 瞬开
    val homeViewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val billListViewModel: BillListViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val statsViewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topDestinations.forEachIndexed { index, dest ->
                        val selected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { selectedTab = index }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(width = 44.dp, height = 36.dp)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                        RoundedCornerShape(18.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(dest.icon, contentDescription = dest.label, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                FloatingActionButton(
                    onClick = { onNavigateToAddEdit(null, Bill.TYPE_EXPENSE, 0L) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "记一笔")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    onAddBill = { onNavigateToAddEdit(null, Bill.TYPE_EXPENSE, 0L) },
                    onViewAll = { selectedTab = 1 },
                    onBillClick = { id -> onNavigateToAddEdit(id, Bill.TYPE_EXPENSE, 0L) },
                    onSetBudget = onNavigateToBudget,
                    onManageLedgers = onNavigateToLedgers,
                    onOpenGoals = onNavigateToGoals,
                    viewModel = homeViewModel
                )
                1 -> BillListScreen(
                    onAddBill = { onNavigateToAddEdit(null, Bill.TYPE_EXPENSE, 0L) },
                    onBillClick = { id -> onNavigateToAddEdit(id, Bill.TYPE_EXPENSE, 0L) },
                    viewModel = billListViewModel
                )
                2 -> StatsScreen(
                    onAddBillForDate = { date -> onNavigateToAddEdit(null, Bill.TYPE_EXPENSE, date) },
                    viewModel = statsViewModel
                )
                3 -> SettingsScreen(
                    onManageCategories = onNavigateToCategories,
                    onManageAccounts = onNavigateToAccounts,
                    onManageBudget = onNavigateToBudget,
                    onManageCategoryBudget = onNavigateToCategoryBudget,
                    onManageRecurring = onNavigateToRecurring,
                    onManageLedgers = onNavigateToLedgers,
                    onManageGoals = onNavigateToGoals,
                    onManageReminder = onNavigateToReminder,
                    onLockSettings = onNavigateToLock,
                    onOnboarding = onNavigateToOnboarding,
                    onAbout = onNavigateToAbout,
                    viewModel = settingsViewModel
                )
            }
        }
    }
}

@Composable
fun AppRoot(addEvents: Flow<Unit> = emptyFlow()) {
    val navController = rememberNavController()
    var lastNavTime by remember { mutableStateOf(0L) }

    fun safeNavigateToAddEdit(billId: Long? = null, type: Int = Bill.TYPE_EXPENSE, date: Long = 0L) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNavTime < 350L) return
        lastNavTime = now
        navController.navigate(Routes.addEdit(billId, type, date)) {
            launchSingleTop = true
        }
    }

    // 小组件「记一笔」等外部请求 → 跳转记一笔页面
    LaunchedEffect(Unit) {
        addEvents.collect {
            safeNavigateToAddEdit()
        }
    }

        NavHost(
            navController = navController,
        startDestination = Routes.MAIN,
            modifier = Modifier
                .fillMaxSize()
        ) {
        composable(Routes.MAIN) {
            MainScreen(
                onNavigateToAddEdit = { billId, type, date -> safeNavigateToAddEdit(billId, type, date) },
                onNavigateToCategories = { navController.navigate(Routes.CATEGORIES) { launchSingleTop = true } },
                onNavigateToAccounts = { navController.navigate(Routes.ACCOUNTS) { launchSingleTop = true } },
                onNavigateToBudget = { navController.navigate(Routes.BUDGET) { launchSingleTop = true } },
                onNavigateToCategoryBudget = { navController.navigate(Routes.CATEGORY_BUDGET) { launchSingleTop = true } },
                onNavigateToRecurring = { navController.navigate(Routes.RECURRING) { launchSingleTop = true } },
                onNavigateToLedgers = { navController.navigate(Routes.LEDGERS) { launchSingleTop = true } },
                onNavigateToGoals = { navController.navigate(Routes.GOALS) { launchSingleTop = true } },
                onNavigateToReminder = { navController.navigate(Routes.REMINDER) { launchSingleTop = true } },
                onNavigateToLock = { navController.navigate(Routes.LOCK) { launchSingleTop = true } },
                onNavigateToOnboarding = { navController.navigate(Routes.ONBOARDING) { launchSingleTop = true } },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) { launchSingleTop = true } }
            )
        }
            composable(
                route = Routes.ADD_EDIT,
                arguments = listOf(
                    navArgument("billId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("type") {
                        type = NavType.IntType
                        defaultValue = Bill.TYPE_EXPENSE
                    },
                    navArgument("date") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
            ),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(160))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(160))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(160))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(160))
            }
            ) { entry ->
                val billId = entry.arguments?.getLong("billId") ?: -1L
                val type = entry.arguments?.getInt("type") ?: Bill.TYPE_EXPENSE
                val date = entry.arguments?.getLong("date") ?: 0L
                key(entry.id) {
                    AddEditBillScreen(
                        billId = billId,
                        defaultType = type,
                        initialDate = date,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel(
                            viewModelStoreOwner = entry,
                            key = "add_edit_${entry.id}",
                            factory = AppViewModelProvider.Factory
                        )
                    )
                }
            }
            composable(Routes.CATEGORIES) {
                CategoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ACCOUNTS) {
                AccountScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BUDGET) {
                BudgetScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CATEGORY_BUDGET) {
                CategoryBudgetScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.RECURRING) {
                RecurringScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LOCK) {
                LockSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LEDGERS) {
                LedgerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GOALS) {
                GoalScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.REMINDER) {
                ReminderScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
    }
}
