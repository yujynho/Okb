package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.R

object BtnColors {
    val Magnet = Color(0xFF8B5CF6)
    val Url = Color(0xFF2F80ED)
    val Hd = Color(0xFF06B6D4)
    val K4 = Color(0xFFEC4899)
    val Save = Color(0xFFF59E0B)
    val Edit = Color(0xFF22A877)
    val Delete = Color(0xFFE84C4C)
    val Cancel = Color(0xFF64748B)
}

val LocalActionsInteractive = compositionLocalOf { true }

object ClickDebounce {
    private var lastClickTime = 0L

    @Synchronized
    fun canClick(currentTime: Long = System.currentTimeMillis()): Boolean {
        if (currentTime - lastClickTime >= 280L) {
            lastClickTime = currentTime
            return true
        }
        return false
    }
}

private val ActionTextShadowSingle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    shadow = androidx.compose.ui.graphics.Shadow(
        color = Color.Black.copy(alpha = 0.35f),
        offset = androidx.compose.ui.geometry.Offset(0f, 1f),
        blurRadius = 4f
    )
)

@Composable
fun ActionCircleButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    iconRotation: Float = 0f,
    text: String? = null,
    strongHaptic: Boolean = false,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val interactive = LocalActionsInteractive.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var isDebounceBlocked by remember { mutableStateOf(false) }

    // 1) Separated press vs release specs
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && interactive) 0.92f else 1f,
        animationSpec = if (pressed) {
            androidx.compose.animation.core.tween(90, easing = androidx.compose.animation.core.FastOutLinearInEasing)
        } else {
            androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
            )
        },
        label = "press"
    )

    Column(
        modifier = modifier.graphicsLayer {
            // 3) Visual feedback: brief alpha decrease when debounce is blocked
            alpha = if (enabled) (if (isDebounceBlocked) 0.60f else 1f) else 0.42f
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(if (enabled) color else color.copy(alpha = 0.5f))
                .border(BorderStroke(2.dp, Color.White.copy(alpha = if (enabled) 0.28f else 0.12f)), CircleShape)
                .pointerInput(enabled, interactive) {
                    if (!enabled || !interactive) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressed = false
                            }
                        },
                        onTap = {
                            // 3) Unified Debounce using ClickDebounce object
                            if (ClickDebounce.canClick()) {
                                // 2) Haptic moved from onPress to onTap
                                haptic.performHapticFeedback(
                                    if (strongHaptic) HapticFeedbackType.LongPress
                                    else HapticFeedbackType.TextHandleMove
                                )
                                onClick()
                            } else {
                                // 3) Trigger visual feedback when blocked
                                scope.launch {
                                    isDebounceBlocked = true
                                    kotlinx.coroutines.delay(120)
                                    isDebounceBlocked = false
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp).rotate(iconRotation)
                )
            } else if (text != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 9) Replaced double-text shadow hack with single Text + shadow style
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        style = ActionTextShadowSingle
                    )
                }
            }
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

enum class Source { MAGNET, URL }

@Composable
fun MainActionMenu(
    onMagnetClick: () -> Unit,
    onUrlClick: () -> Unit,
    onSave: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    showMagnet: Boolean = true,
    showUrl: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showMagnet) {
            ActionCircleButton(
                label = "Magnet",
                color = BtnColors.Magnet,
                onClick = onMagnetClick,
                icon = painterResource(R.drawable.ic_magnet),
                iconRotation = 0f
            )
        }
        if (showUrl) {
            ActionCircleButton(
                label = "URL",
                color = BtnColors.Url,
                onClick = onUrlClick,
                icon = painterResource(R.drawable.ic_url_link)
            )
        }
        ActionCircleButton(
            label = if (isSaved) "Saved" else "Save",
            color = BtnColors.Save,
            onClick = onSave,
            icon = painterResource(if (isSaved) R.drawable.ic_bookmark_saved else R.drawable.ic_bookmark_save)
        )
        ActionCircleButton(
            label = "Edit",
            color = BtnColors.Edit,
            onClick = onEdit,
            icon = painterResource(R.drawable.ic_edit_pencil)
        )
        ActionCircleButton(
            label = "Delete",
            color = BtnColors.Delete,
            onClick = onDelete,
            icon = painterResource(R.drawable.ic_delete_trash)
        )
    }
}

@Composable
fun QualitySelectMenu(
    onSelectHD: () -> Unit,
    onSelect4K: () -> Unit,
    modifier: Modifier = Modifier,
    hasHD: Boolean = true,
    has4K: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasHD) {
            ActionCircleButton(
                label = "HD",
                color = BtnColors.Hd,
                onClick = onSelectHD,
                text = "HD"
            )
        }
        if (has4K) {
            ActionCircleButton(
                label = "4K",
                color = BtnColors.K4,
                onClick = onSelect4K,
                text = "4K"
            )
        }
    }
}

@Composable
fun DeleteConfirmMenu(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Delete this item?",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionCircleButton(
                label = "Cancel",
                color = BtnColors.Cancel,
                onClick = onCancel,
                icon = painterResource(R.drawable.ic_action_cancel)
            )
            ActionCircleButton(
                label = "Delete",
                color = BtnColors.Delete,
                onClick = onConfirm,
                icon = painterResource(R.drawable.ic_delete_trash),
                strongHaptic = true
            )
        }
    }
}
