package com.andersonlin.moneybook.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import com.andersonlin.moneybook.R

enum class SoundType {
    CRISP,  // 干净利落的标准机械微击（主操作、记账、FAB、分类选择）
    LIGHT,  // 轻巧干脆的微触点（标签、小切换、日期翻页、Tab点选）
    NONE
}

/** 全局低延迟 UI 点击音效与震动触感控制器 */
object SoundFeedback {
    private var soundPool: SoundPool? = null
    private var soundCrispId: Int = 0
    private var soundLightId: Int = 0
    private var loadedCrisp = false
    private var loadedLight = false
    private var initialized = false

    @Volatile
    var soundEnabled: Boolean = true

    @Volatile
    var hapticsEnabled: Boolean = true

    fun init(context: Context) {
        if (initialized) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()
        soundPool = pool
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                if (sampleId == soundCrispId) loadedCrisp = true
                if (sampleId == soundLightId) loadedLight = true
            }
        }
        soundCrispId = pool.load(context.applicationContext, R.raw.click_crisp, 1)
        soundLightId = pool.load(context.applicationContext, R.raw.click_light, 1)
        initialized = true
    }

    fun play(view: View?, type: SoundType = SoundType.CRISP) {
        if (soundEnabled && initialized && soundPool != null) {
            val (id, loaded, vol) = when (type) {
                SoundType.CRISP -> Triple(soundCrispId, loadedCrisp, 0.45f)
                SoundType.LIGHT -> Triple(soundLightId, loadedLight, 0.35f)
                SoundType.NONE -> Triple(0, false, 0f)
            }
            if (id > 0 && loaded) {
                soundPool?.play(id, vol, vol, 1, 0, 1.0f)
            }
        }
        if (hapticsEnabled && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}

/** 触感按压缩放修饰符：按下轻微下沉，松手干脆回弹 */
fun Modifier.tactileScale(
    interactionSource: MutableInteractionSource,
    scaleDown: Float = 0.94f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            scale.animateTo(
                targetValue = scaleDown,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessHigh)
            )
        } else {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
            )
        }
    }
    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** 无慢速涟漪拖拽感、干脆物理下沉 + 清脆音效触感的点击修饰符 */
fun Modifier.tactileClick(
    soundType: SoundType = SoundType.CRISP,
    scaleDown: Float = 0.93f,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    this
        .tactileScale(interactionSource, scaleDown)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                SoundFeedback.play(view, soundType)
                onClick()
            }
        )
}
