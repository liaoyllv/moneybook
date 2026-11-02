package com.andersonlin.moneybook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.andersonlin.moneybook.data.settings.ThemeMode

/** 暖色调统计图表分类配色（温暖、柔和、层次鲜明） */
val ChartColors = listOf(
    Color(0xFFF59E0B), // 暖琥珀金 (Amber)
    Color(0xFFF97316), // 暖柿橙 (Warm Orange)
    Color(0xFFEF4444), // 暖珊瑚红 (Coral Red)
    Color(0xFFE17055), // 暖陶土 (Terracotta)
    Color(0xFFD97706), // 暖赭石 (Ochre)
    Color(0xFFA16207), // 暖焦糖 (Caramel)
    Color(0xFFF59E6C), // 暖杏粉 (Warm Peach)
    Color(0xFF84CC16), // 暖橄榄嫩绿 (Warm Olive Leaf)
    Color(0xFF10B981), // 暖翠绿 (Warm Emerald)
    Color(0xFF06B6D4), // 暖水青 (Warm Cyan)
    Color(0xFFFB7185), // 暖玫粉 (Muted Rose)
    Color(0xFFC084FC)  // 暖紫丁香 (Warm Lilac)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF9E4B00),             // 暖焦糖琥珀
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC6),    // 柔暖杏色容器
    onPrimaryContainer = Color(0xFF341500),
    secondary = Color(0xFF735A46),           // 暖栗棕
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDFC6),  // 柔和燕麦暖色
    onSecondaryContainer = Color(0xFF2A1809),
    tertiary = Color(0xFF8B503B),            // 暖陶土赤褐
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBD1),
    onTertiaryContainer = Color(0xFF381004),
    background = Color(0xFFFCF8F4),          // 温暖奶油棉白底色
    onBackground = Color(0xFF201A16),        // 暖意浓缩咖啡黑字
    surface = Color(0xFFFCF8F4),             // 暖白面板
    onSurface = Color(0xFF201A16),
    surfaceVariant = Color(0xFFEFE6DE),      // 温暖燕麦卡片背景
    onSurfaceVariant = Color(0xFF51443B),    // 柔和暖棕次级文字
    outline = Color(0xFF83746B),             // 暖石灰边框
    outlineVariant = Color(0xFFD6C8BF),      // 浅暖边框分隔线
    error = Color(0xFFBA2818),               // 暖朱砂红支出
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFA726),             // 暖阳金琥珀（明亮清爽、高级醒目）
    onPrimary = Color(0xFF3E1F00),
    primaryContainer = Color(0xFF382B1E),    // 雅致深暖古铜底色（告别混浊泥褐）
    onPrimaryContainer = Color(0xFFFFDDB8),  // 柔和亮金杏文本
    secondary = Color(0xFFE0BA9B),           // 暖金砂
    onSecondary = Color(0xFF3B291A),
    secondaryContainer = Color(0xFF2D2925),  // 沉静暖岩黑灰（Tab、选中胶囊背景）
    onSecondaryContainer = Color(0xFFEBE0D6),
    tertiary = Color(0xFFFF9E80),            // 暖珊瑚粉
    onTertiary = Color(0xFF4D1E10),
    tertiaryContainer = Color(0xFF3D241C),
    onTertiaryContainer = Color(0xFFFFDCD2),
    background = Color(0xFF161514),          // 高级暖曜石黑底色（纯净通透、深邃舒适、告别混浊暗黄）
    onBackground = Color(0xFFF5EFEA),        // 暖羊皮纸象牙白（清晰高对比正文）
    surface = Color(0xFF161514),             // 曜石面板底色
    onSurface = Color(0xFFF5EFEA),
    surfaceVariant = Color(0xFF242220),      // 暖灰玄武岩卡片底色（层次分明、质感现代）
    onSurfaceVariant = Color(0xFFC8BFB6),    // 暖银灰次级信息文字
    outline = Color(0xFF6E655E),             // 暖灰边界线
    outlineVariant = Color(0xFF35312D),      // 细腻精致暗分隔线
    error = Color(0xFFFF6B6B),               // 暖珊瑚红支出标记（鲜明生动）
    onError = Color(0xFF5E110A),
    errorContainer = Color(0xFF4D1A14),
    onErrorContainer = Color(0xFFFFDAD4)
)

private val AppTypography = Typography()

@Composable
fun MoneyBookTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content
    )
}
