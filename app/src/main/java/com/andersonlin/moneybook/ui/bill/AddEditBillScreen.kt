package com.andersonlin.moneybook.ui.bill

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.TextField
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.MainActivity
import com.andersonlin.moneybook.MoneyBookApp
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.util.fullDateLabel

/** 从任意 Context 向上找 Activity */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditBillScreen(
    billId: Long,
    defaultType: Int,
    initialDate: Long = 0L,
    onBack: () -> Unit,
    viewModel: AddEditBillViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val amountFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = remember(context) { context.findActivity() as? MainActivity }

    LaunchedEffect(billId, initialDate) { viewModel.init(billId, defaultType, initialDate) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddEditEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                AddEditEvent.Saved -> {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    (appContext as? MoneyBookApp)?.requestWidgetUpdate()
                    onBack()
                }
                AddEditEvent.SavedContinue -> {
                    (appContext as? MoneyBookApp)?.requestWidgetUpdate()
                    snackbarHostState.showSnackbar("已保存，继续记下一笔")
                    amountFocusRequester.requestFocus()
                }
                AddEditEvent.Deleted -> {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    (appContext as? MoneyBookApp)?.requestWidgetUpdate()
                    onBack()
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    var accountMenuExpanded by remember { mutableStateOf(false) }
                    val currentAccount = state.accounts.firstOrNull { it.id == state.accountId }
                        ?: state.accounts.firstOrNull()

                    // 金额输入与支付方式单行并排
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (state.amountError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "¥",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        amountFocusRequester.requestFocus()
                                        keyboardController?.show()
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = state.amountText,
                                    onValueChange = viewModel::setAmount,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(amountFocusRequester),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        if (state.amountText.isEmpty()) {
                                            Text(
                                                "0.00",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                            // 分隔竖线
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .width(1.dp)
                                    .height(22.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            // 支付账户下拉
                            Box {
                                Surface(
                                    onClick = { accountMenuExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = (currentAccount?.name ?: "选择账户") + " ▾",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = accountMenuExpanded,
                                    onDismissRequest = { accountMenuExpanded = false }
                                ) {
                                    state.accounts.forEach { account ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(account.icon)
                                                    Text(account.name, style = MaterialTheme.typography.bodyMedium)
                                                    if (account.id == state.accountId) {
                                                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                viewModel.setAccount(account.id)
                                                accountMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.isEdit) {
                        Button(
                            onClick = { viewModel.save(andContinue = false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            Text("保存", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.save(andContinue = true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(25.dp)
                            ) {
                                Text("继续记", style = MaterialTheme.typography.titleMedium)
                            }
                            Button(
                                onClick = { viewModel.save(andContinue = false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(25.dp)
                            ) {
                                Text("记完了", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isEdit) "编辑账单" else "记一笔",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.setType(Bill.TYPE_EXPENSE) },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            text = if (state.type == Bill.TYPE_EXPENSE) "✓ 支出" else "支出",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    TextButton(
                        onClick = { viewModel.setType(Bill.TYPE_INCOME) },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            text = if (state.type == Bill.TYPE_INCOME) "✓ 收入" else "收入",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (state.isEdit) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 分类
            if (state.categoriesLoaded && state.categories.isEmpty()) {
                Text(
                    text = "暂无分类，请先到「设置-分类管理」添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else if (state.categories.isNotEmpty()) {
                com.andersonlin.moneybook.ui.components.CategoryPicker(
                    state.categories, state.categoryId, viewModel::setCategory, viewModel::reorderCategories
                )
            }

            Spacer(Modifier.height(6.dp))

            key(state.categoryId) {
                com.andersonlin.moneybook.ui.components.CompactTags(
                    state.tagSuggestions,state.tags,viewModel::toggleTag,viewModel::addTag)
            }

            Spacer(Modifier.height(4.dp))

            // 日期与附件快捷条（单行收拢，右侧附件图标/缩略胶囊）
            var attachmentMenuExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = "选择日期",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = fullDateLabel(state.dateEpochDay) + " ▾",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 附件区域：已选时展示微缩胶囊预览；未选时展示添加小按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.hasAttachment) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 6.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (state.attachmentIsImage) {
                                    AsyncImage(
                                        model = Uri.parse(state.attachmentUri),
                                        contentDescription = "查看大图",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { showImageViewer = true }
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.InsertDriveFile,
                                        contentDescription = "查看文件",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                state.attachmentUri?.let {
                                                    activity?.openAttachmentViewer(it, state.attachmentMime)
                                                }
                                            },
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = if (state.attachmentIsImage) "图片" else "附件",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clickable {
                                        if (state.attachmentIsImage) showImageViewer = true
                                        else state.attachmentUri?.let {
                                            activity?.openAttachmentViewer(it, state.attachmentMime)
                                        }
                                    }
                                )
                                IconButton(
                                    onClick = viewModel::clearAttachment,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "移除附件",
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box {
                        Surface(
                            onClick = { attachmentMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    contentDescription = "添加附件",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (state.hasAttachment) "更换" else "附件",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = attachmentMenuExpanded,
                            onDismissRequest = { attachmentMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("拍照 / 相册", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    attachmentMenuExpanded = false
                                    activity?.pickImage { uri ->
                                        if (uri != null) {
                                            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "image/*"
                                            viewModel.setAttachment(uri.toString(), mime)
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("选取文件", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    attachmentMenuExpanded = false
                                    activity?.pickAttachment { uri ->
                                        if (uri != null) {
                                            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "application/octet-stream"
                                            viewModel.setAttachment(uri.toString(), mime)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // 单行轻量备注输入框（不占高度、不遮挡、随输随记）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (state.note.isEmpty()) {
                                Text(
                                    "写点备注（可选）...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (state.note.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setNote("") },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清空备注",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if(showDatePicker) {
        var date by remember { mutableStateOf(java.time.LocalDate.ofEpochDay(state.dateEpochDay)) }
        AlertDialog(onDismissRequest={showDatePicker=false},title={Text("选择日期")},text={
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf("年" to date.year,"月" to date.monthValue,"日" to date.dayOfMonth).forEachIndexed { index, pair ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton(onClick={date=when(index) {0->date.minusYears(1);1->date.minusMonths(1);else->date.minusDays(1)}}) { Text("－") }
                        Text("${pair.second} ${pair.first}",style=MaterialTheme.typography.titleMedium)
                        TextButton(onClick={date=when(index) {0->date.plusYears(1);1->date.plusMonths(1);else->date.plusDays(1)}}) { Text("＋") }
                    }
                }
                TextButton(onClick={date=java.time.LocalDate.now()}) { Text("回到今天") }
            }
        },confirmButton={TextButton(onClick={viewModel.setDate(date.toEpochDay());showDatePicker=false}) {Text("确定")}},
        dismissButton={TextButton(onClick={showDatePicker=false}) {Text("取消")}})
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除账单") },
            text = { Text("确定要删除这笔账单吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    // 全屏查看附件图片
    if (showImageViewer && state.hasAttachment && state.attachmentIsImage) {
        Dialog(
            onDismissRequest = { showImageViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = Uri.parse(state.attachmentUri),
                    contentDescription = "附件大图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showImageViewer = false }
                )
                IconButton(
                    onClick = { showImageViewer = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                }
                Button(
                    onClick = {
                        state.attachmentUri?.let {
                            activity?.openAttachmentViewer(it, state.attachmentMime)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Text("用其他应用打开")
                }
            }
        }
    }
}
