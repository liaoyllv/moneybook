package com.andersonlin.moneybook.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
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
import com.andersonlin.moneybook.util.SoundFeedback
import com.andersonlin.moneybook.util.SoundType
import com.andersonlin.moneybook.util.tactileClick
import com.andersonlin.moneybook.util.tactileScale
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
    val view = LocalView.current
    val fabInteraction = remember { MutableInteractionSource() }

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
                        val pillScale by animateFloatAsState(
                            targetValue = if (selected) 1f else 0.82f,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessHigh),
                            label = "tabPillScale"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .tactileClick(
                                    soundType = SoundType.LIGHT,
                                    scaleDown = 0.88f,
                                    onClick = { selectedTab = index }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(width = 44.dp, height = 36.dp)
                                    .graphicsLayer {
                                        scaleX = pillScale
                                        scaleY = pillScale
                                    }
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
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        SoundFeedback.play(view, SoundType.CRISP)
                        onNavigateToAddEdit(null, Bill.TYPE_EXPENSE, 0L)
                    },
                    modifier = Modifier.tactileScale(fabInteraction, scaleDown = 0.90f),
                    interactionSource = fabInteraction,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "记一笔")
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(110, easing = LinearOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(80, easing = FastOutLinearInEasing))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "mainTabContent"
        ) { tab ->
            when (tab) {
                0 -> HomeScreen(
                    onAddBill = { onNavigateToAddEdit(null, Bill.TYPE_EXPENSE, 0L) },
                    onBillClick = { id -> onNavigateToAddEdit(id, Bill.TYPE_EXPENSE, 0L) },
                    onSetBudget = onNavigateToBudget,
                    onManageLedgers = onNavigateToLedgers,
                    onOpenGoals = onNavigateToGoals,
                    homeViewModel = homeViewModel,
                    billListViewModel = billListViewModel
                )
                1 -> StatsScreen(
                    onAddBillForDate = { date -> onNavigateToAddEdit(null, Bill.TYPE_EXPENSE, date) },
                    viewModel = statsViewModel
                )
                2 -> SettingsScreen(
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
        composable(
            Routes.MAIN,
            exitTransition = {
                if (targetState.destination.route?.startsWith("add_edit") == true) {
                    ExitTransition.None
                } else {
                    fadeOut(animationSpec = tween(90))
                }
            },
            popEnterTransition = {
                if (initialState.destination.route?.startsWith("add_edit") == true) {
                    EnterTransition.None
                } else {
                    fadeIn(animationSpec = tween(100))
                }
            }
        ) {
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
                    initialOffsetY = { (it * 0.18f).toInt() },
                    animationSpec = tween(160, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))
                ) + fadeIn(animationSpec = tween(130))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(100))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(100))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(150, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(110))
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
            val subpageEnter = slideInHorizontally(
                initialOffsetX = { (it * 0.22f).toInt() },
                animationSpec = tween(160, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))
            ) + fadeIn(tween(130))
            val subpageExit = fadeOut(tween(90))
            val subpagePopEnter = fadeIn(tween(100))
            val subpagePopExit = slideOutHorizontally(
                targetOffsetX = { (it * 0.22f).toInt() },
                animationSpec = tween(130, easing = FastOutLinearInEasing)
            ) + fadeOut(tween(90))

            composable(Routes.CATEGORIES, enterTransition = { subpageEnter }, exitTransition = { subpageExit }, popEnterTransition = { subpagePopEnter }, popExitTransition = { subpagePopExit }) {
                CategoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ACCOUNTS, enterTransition = { subpageEnter }, exitTransition = { subpageExit }, popEnterTransition = { subpagePopEnter }, popExitTransition = { subpagePopExit }) {
                AccountScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BUDGET, enterTransition = { subpageEnter }, exitTransition = { subpageExit }, popEnterTransition = { subpagePopEnter }, popExitTransition = { subpagePopExit }) {
                BudgetScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CATEGORY_BUDGET, enterTransition = { subpageEnter }, exitTransition = { subpageExit }, popEnterTransition = { subpagePopEnter }, popExitTransition = { subpagePopExit }) {
                CategoryBudgetScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.RECURRING, enterTransition = { subpageEnter }, exitTransition = { subpageExit }, popEnterTransition = { subpagePopEnter }, popExitTransition = { subpagePopExit }) {
                RecurringScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LOCK, enterTransition = { subpageEnter }, exitTransition = { subpageExit }, popEnterTransition = { subpagePopEnter }, popExitTransition = { subpagePopExit }) {
                LockSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LEDGERS, enterTransition = { subpageEnter }, exitTransition = { subpageExit }, popEnterTransition = { subpagePopEnter }, popExitTransition = { subpagePopExit }) {
                LedgerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GOALS, enterTransition = { subpageEnter }, exitTransition = { subpageExit }, popEnterTransition = { subpagePopEnter }, popExitTransition = { subpagePopExit }) {
                GoalScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.REMINDER, enterTransition = { subpageEnter }, exitTransition = { subpageExit }, popEnterTransition = { subpagePopEnter }, popExitTransition = { subpagePopExit }) {
                ReminderScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ONBOARDING, enterTransition = { subpageEnter }, exitTransition = { subpageExit }, popEnterTransition = { subpagePopEnter }, popExitTransition = { subpagePopExit }) {
                OnboardingScreen(onFinished = { navController.popBackStack() })
            }
            composable(Routes.ABOUT, enterTransition = { subpageEnter }, exitTransition = { subpageExit }, popEnterTransition = { subpagePopEnter }, popExitTransition = { subpagePopExit }) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
    }
}
