package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.ScreenState
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette

sealed class NuvioNavItem(
    val routeState: ScreenState,
    val title: String,
    val iconRes: Int,
    val testTag: String
) {
    object Home : NuvioNavItem(ScreenState.Home, "Home", R.drawable.ic_nav_home, "nuvio_nav_home")
    object Studio : NuvioNavItem(ScreenState.Studios, "Studio", R.drawable.ic_nav_studio, "nuvio_nav_studio")
    object Actor : NuvioNavItem(ScreenState.Actors, "Actor", R.drawable.ic_nav_actor, "nuvio_nav_actor")
    object Setting : NuvioNavItem(ScreenState.Settings, "Setting", R.drawable.ic_nav_settings, "nuvio_nav_setting")
}

@Composable
fun NuvioNavigationBar(
    currentScreen: ScreenState,
    onNavigate: (ScreenState) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    val items = listOf(
        NuvioNavItem.Home,
        NuvioNavItem.Studio,
        NuvioNavItem.Actor,
        NuvioNavItem.Setting
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(elevation = 12.dp, shape = CircleShape, clip = false),
        shape = CircleShape,
        color = palette.cardBg,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = when (item.routeState) {
                    is ScreenState.Home -> currentScreen is ScreenState.Home
                    is ScreenState.Studios -> currentScreen is ScreenState.Studios || currentScreen is ScreenState.AddEditStudio || currentScreen is ScreenState.StudioScenes
                    is ScreenState.Actors -> currentScreen is ScreenState.Actors || currentScreen is ScreenState.AddEditActor || currentScreen is ScreenState.ActorScenes
                    is ScreenState.Settings -> currentScreen is ScreenState.Settings
                    else -> false
                }

                NuvioNavItemView(
                    item = item,
                    isSelected = isSelected,
                    onClick = { onNavigate(item.routeState) }
                )
            }
        }
    }
}

@Composable
private fun NuvioNavItemView(
    item: NuvioNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    val activeBgColor by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(220),
        label = "NuvioNavBg"
    )

    val activeContentColor by animateColorAsState(
        targetValue = if (isSelected) accent else palette.textSecondary,
        animationSpec = tween(220),
        label = "NuvioNavContent"
    )

    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(activeBgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = accent),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(item.testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.title,
                tint = activeContentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = activeContentColor,
                maxLines = 1
            )
        }
    }
}
