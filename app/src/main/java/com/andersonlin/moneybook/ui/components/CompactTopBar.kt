package com.andersonlin.moneybook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CompactTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    height: Dp = 40.dp
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(height)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                onBack != null -> {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                navigationIcon != null -> {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        navigationIcon()
                    }
                }
                else -> {
                    Spacer(Modifier.width(10.dp))
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                ) {
                    title()
                }
            }
            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    actions()
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
fun CompactCenterTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    height: Dp = 40.dp
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(height)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                onBack != null -> {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                navigationIcon != null -> {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.CenterStart),
                        contentAlignment = Alignment.Center
                    ) {
                        navigationIcon()
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth(0.6f),
                contentAlignment = Alignment.Center
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                ) {
                    title()
                }
            }
            if (actions != null) {
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    actions()
                }
            }
        }
    }
}
