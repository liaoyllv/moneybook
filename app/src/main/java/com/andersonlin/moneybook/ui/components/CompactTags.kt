package com.andersonlin.moneybook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.andersonlin.moneybook.util.SoundFeedback
import com.andersonlin.moneybook.util.SoundType

@Composable
fun CompactTags(tags: List<String>, selected: List<String>, select: (String)->Unit, add: (String)->Unit) {
    var query by remember { mutableStateOf("") }
    var suggesting by remember { mutableStateOf(false) }
    val view = LocalView.current
    // 第一排固定高频前5个标签（点击原地高亮，不跳动移位）；第二排展示其余已选自定义标签 + 次高频标签 + 右侧输入框
    val row1Tags = tags.take(5)
    val row2Tags = (selected.filter { it !in row1Tags } + tags.drop(5).take(4)).distinct()
    fun confirm(value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotBlank()) {
            SoundFeedback.play(view, SoundType.CRISP)
            add(trimmed)
            query = ""
            suggesting = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 第一排：高频推荐标签（横向滑动，点击即原地切换选中态）
        if (row1Tags.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row1Tags.forEach { tag ->
                    val isSelected = tag in selected
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            SoundFeedback.play(view, SoundType.LIGHT)
                            select(tag)
                        },
                        modifier = Modifier.height(32.dp),
                        label = {
                            Text(
                                if (isSelected) "$tag ✓" else tag,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }

        // 第二排：次高频/新加已选标签 + 右侧末尾紧凑输入框（带出历史标签，确认即添加）
        Row(
            Modifier.fillMaxWidth().height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row2Tags.forEach { tag ->
                    val isSelected = tag in selected
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            SoundFeedback.play(view, SoundType.LIGHT)
                            select(tag)
                        },
                        modifier = Modifier.height(32.dp),
                        label = {
                            Text(
                                if (isSelected) "$tag ✓" else tag,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            Box {
                Surface(
                    shape = RoundedCornerShape(17.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .widthIn(min = 110.dp, max = 140.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                query = it.take(30)
                                suggesting = it.isNotBlank()
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { confirm(query) }),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (query.isEmpty()) {
                                    Text(
                                        "+ 新标签",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (query.isNotBlank()) {
                            IconButton(
                                onClick = { confirm(query) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                val matches = tags.filter { it.contains(query, ignoreCase = true) && it !in selected }.take(6)
                DropdownMenu(
                    expanded = suggesting && matches.isNotEmpty(),
                    onDismissRequest = { suggesting = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    matches.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(tag, style = MaterialTheme.typography.bodyMedium) },
                            onClick = { confirm(tag) }
                        )
                    }
                }
            }
        }
    }
}
