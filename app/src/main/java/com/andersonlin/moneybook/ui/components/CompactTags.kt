package com.andersonlin.moneybook.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.andersonlin.moneybook.util.SoundFeedback
import com.andersonlin.moneybook.util.SoundType

/**
 * 标签栏：单排展示，基于历史使用频率降序排列，支持左右平滑滑动与单选互斥
 */
@Composable
fun CompactTags(
    tags: List<String>,
    selected: List<String>,
    onSelectTag: (String) -> Unit
) {
    val view = LocalView.current
    // 合并当前选中的标签与历史频次推荐，确保已选标签在列表中且排在前列
    val allTags = remember(tags, selected) {
        (selected + tags).distinct()
    }

    if (allTags.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allTags.forEach { tag ->
                val isSelected = tag in selected
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        SoundFeedback.play(view, SoundType.LIGHT)
                        onSelectTag(tag)
                    },
                    modifier = Modifier.height(32.dp),
                    label = {
                        Text(
                            text = if (isSelected) "$tag ✓" else tag,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }
    }
}
