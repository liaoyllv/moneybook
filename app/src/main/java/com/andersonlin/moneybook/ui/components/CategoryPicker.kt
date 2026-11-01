package com.andersonlin.moneybook.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.andersonlin.moneybook.data.model.Category

@Composable
fun CategoryPicker(
    categories: List<Category>,
    selected: Long?,
    onSelect: (Long) -> Unit,
    onOrder: (List<Long>) -> Unit
) {
    var expanded by rememberSaveable(categories.firstOrNull()?.type) { mutableStateOf(false) }
    val columns = 4
    // 默认展示前 3 排（11 个分类 + 1 个展开按钮），展开展示全部
    val hasOverflow = categories.size > 11
    val visible = if (expanded || !hasOverflow) categories else categories.take(columns * 3 - 1)

    Column(Modifier.fillMaxWidth()) {
        Layout(content = {
            visible.forEach { category ->
                key(category.id) {
                    Surface(
                        onClick = { onSelect(category.id) },
                        color = if (selected == category.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Row(
                            Modifier
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .heightIn(min = 34.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(category.icon)
                            Spacer(Modifier.width(3.dp))
                            Text(category.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
            if (hasOverflow && !expanded) {
                Surface(
                    onClick = { expanded = true },
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(Modifier.padding(2.dp).heightIn(min = 34.dp), contentAlignment = Alignment.Center) {
                        Text("更多 ▾", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }) { measurables, constraints ->
            val width = constraints.maxWidth / columns
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = width, maxWidth = width, minHeight = 0)) }
            val heights = placeables.chunked(columns).map { row -> row.maxOf { it.height } }
            layout(constraints.maxWidth, heights.sum()) {
                var y = 0
                placeables.chunked(columns).forEachIndexed { row, items ->
                    items.forEachIndexed { col, p -> p.placeRelative(col * width, y) }
                    y += heights[row]
                }
            }
        }

        if (expanded && hasOverflow) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
                    onClick = { expanded = false },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text("收起分类 ▴", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (!expanded) {
            categories.firstOrNull { it.id == selected && it !in visible }?.let {
                Text(
                    "当前所选：${it.icon} ${it.name}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
