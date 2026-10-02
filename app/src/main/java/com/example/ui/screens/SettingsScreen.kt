package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.SettingsEntity
import com.example.ui.MainViewModel
import com.example.ui.components.ColorPalettePicker
import com.example.ui.components.DataBackupSection
import com.example.ui.components.IntegrationsDropdownDebridSection
import com.example.ui.components.NativeThemeSelector
import com.example.ui.components.NativeTransitionSelector
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette
import kotlinx.coroutines.launch

private enum class SettingsSection {
    MAIN_MENU,
    DISPLAY,
    PRIVACY,
    INTEGRATIONS,
    DATA_BACKUP,
    ADVANCED,
    SAMPLE_DATA
}

/**
 * MUSE-REF: Switch colors matching the exact visual reference:
 * Active track: #057DF2 (pure blue), thumb: White
 * Inactive track: White 14%, thumb: #9A9A9E, borders: Transparent
 */
@Composable
private fun museSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = Color(0xFF057DF2), // MUSE-REF
    checkedThumbColor = Color.White, // MUSE-REF
    uncheckedTrackColor = Color.White.copy(alpha = 0.14f), // MUSE-REF
    uncheckedThumbColor = Color(0xFF9A9A9E), // MUSE-REF
    uncheckedBorderColor = Color.Transparent, // MUSE-REF
    checkedBorderColor = Color.Transparent // MUSE-REF
)

/**
 * MUSE-REF: Grouped Card container matching reference specification:
 * Shape: 24.dp rounded corners
 * Container: palette.cardBg (#353638 in Dark)
 * Elevation: 0.dp (flat color contrast)
 * Border: null (no outline)
 */
@Composable
private fun GroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), // MUSE-REF
        colors = CardDefaults.cardColors(containerColor = LocalVaultPalette.current.cardBg), // MUSE-REF
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // MUSE-REF
        border = null, // MUSE-REF
        content = content
    )
}

/**
 * MUSE-REF: Hairline divider between rows:
 * Color: Color.White 10% (matches #4E4F51)
 * Thickness: 1.dp
 * Inset: 20.dp horizontal
 */
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp), // MUSE-REF
        thickness = 1.dp, // MUSE-REF
        color = Color.White.copy(alpha = 0.10f) // MUSE-REF
    )
}

/**
 * MUSE-REF: Section header text above grouped cards:
 * 13sp Bold, palette.textMuted, padding horizontal 24.dp, bottom 8.dp
 */
@Composable
private fun SettingsSectionHeader(text: String) {
    val palette = LocalVaultPalette.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 13.sp, // MUSE-REF
            fontWeight = FontWeight.Bold // MUSE-REF
        ),
        color = palette.textMuted, // MUSE-REF
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp) // MUSE-REF
    )
}

/**
 * MUSE-REF: Standardized navigation chevron icon (›) in palette.textMuted
 */
@Composable
private fun SettingsNavigationChevron() {
    val palette = LocalVaultPalette.current
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
        contentDescription = null,
        tint = palette.textMuted, // MUSE-REF
        modifier = Modifier.size(14.dp)
    )
}

/**
 * MUSE-REF: Standard Setting Row Composable:
 * Padding: vertical = 18.dp, horizontal = 20.dp
 * Title: 16sp, FontWeight.Medium, color = palette.textPrimary
 * Subtitle: 13sp, color = palette.textSecondary
 * Trailing: Switch or chevron in palette.textMuted
 * Click: standard Material ripple
 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalVaultPalette.current
    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick) // MUSE-REF regular ripple
            .padding(vertical = 18.dp, horizontal = 20.dp) // MUSE-REF
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp, horizontal = 20.dp) // MUSE-REF
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = if (trailing != null) 12.dp else 0.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp, // MUSE-REF
                    fontWeight = FontWeight.Medium // MUSE-REF
                ),
                color = palette.textPrimary // MUSE-REF
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp // MUSE-REF
                    ),
                    color = palette.textSecondary // MUSE-REF
                )
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val clipboardManager = LocalClipboardManager.current

    val currentSettingsRaw by viewModel.settings.collectAsStateWithLifecycle()
    val currentSettings = currentSettingsRaw ?: SettingsEntity()

    var themeName by remember(currentSettings) { mutableStateOf(currentSettings.currentTheme) }
    var accentHex by remember(currentSettings) { mutableStateOf(currentSettings.accentColorHex) }
    var torboxKey by remember(currentSettings) { mutableStateOf(currentSettings.torboxApiKey) }
    var rdKey by remember(currentSettings) { mutableStateOf(currentSettings.realDebridApiKey) }
    var stashDbKey by remember(currentSettings) { mutableStateOf(currentSettings.stashDbApiKey) }
    var showStashDbKey by remember { mutableStateOf(false) }

    var sampleDataStatus by remember { mutableStateOf("") }
    val initialSection = remember {
        val sec = when (viewModel.initialSettingsSection) {
            "DISPLAY" -> SettingsSection.DISPLAY
            "PRIVACY" -> SettingsSection.PRIVACY
            "INTEGRATIONS" -> SettingsSection.INTEGRATIONS
            "DATA_BACKUP" -> SettingsSection.DATA_BACKUP
            "ADVANCED" -> SettingsSection.ADVANCED
            "SAMPLE_DATA" -> SettingsSection.SAMPLE_DATA
            else -> SettingsSection.MAIN_MENU
        }
        viewModel.initialSettingsSection = null
        sec
    }
    var currentSection by remember { mutableStateOf(initialSection) }

    // Intercept hardware/gesture back press when inside a sub-category
    BackHandler(enabled = currentSection != SettingsSection.MAIN_MENU) {
        currentSection = SettingsSection.MAIN_MENU
    }

    val screenTitle = when (currentSection) {
        SettingsSection.MAIN_MENU -> "Settings"
        SettingsSection.DISPLAY -> "Display"
        SettingsSection.PRIVACY -> "Privacy"
        SettingsSection.INTEGRATIONS -> "Integrations"
        SettingsSection.DATA_BACKUP -> "Data & Backup"
        SettingsSection.ADVANCED -> "Advanced"
        SettingsSection.SAMPLE_DATA -> "Sample Data"
    }

    fun saveAllSettings() {
        viewModel.updateSettings(
            currentSettings.copy(
                currentTheme = themeName,
                accentColorHex = accentHex,
                torboxApiKey = torboxKey.trim(),
                realDebridApiKey = rdKey.trim(),
                stashDbApiKey = stashDbKey.trim()
            )
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = palette.bg, // MUSE-REF
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // MUSE-REF: Centered top bar with Bold 20sp, palette.surface (= bg in Dark) and circular #3B3C3E back button
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        screenTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold, // MUSE-REF
                            fontSize = 20.sp // MUSE-REF
                        ),
                        color = palette.textPrimary // MUSE-REF
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentSection != SettingsSection.MAIN_MENU) {
                                currentSection = SettingsSection.MAIN_MENU
                            } else {
                                viewModel.navigateBack()
                            }
                        },
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B3C3E)) // MUSE-REF Circular back button #3B3C3E
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.textPrimary, // MUSE-REF
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = palette.surface // MUSE-REF Top bar blends seamlessly with background
                )
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentSection,
            transitionSpec = {
                if (targetState != SettingsSection.MAIN_MENU) {
                    (fadeIn(animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) +
                            slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { width -> width / 5 })
                        .togetherWith(
                            fadeOut(animationSpec = androidx.compose.animation.core.tween(160, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                        )
                } else {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                        .togetherWith(
                            fadeOut(animationSpec = androidx.compose.animation.core.tween(160, easing = androidx.compose.animation.core.FastOutLinearInEasing)) +
                                    slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { width -> width / 5 }
                        )
                }
            },
            label = "settings_navigation"
        ) { section ->
            when (section) {
                SettingsSection.MAIN_MENU -> {
                    SettingsMainMenu(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                            .verticalScroll(rememberScrollState()),
                        themeName = themeName,
                        accentHex = accentHex,
                        currentSettings = currentSettings,
                        stashDbKey = stashDbKey,
                        rdKeyConfigured = rdKey.isNotBlank() || torboxKey.isNotBlank(),
                        onNavigateTo = { currentSection = it },
                        onToggleCards = {
                            viewModel.updateSettings(currentSettings.copy(showManagementCards = it))
                        },
                        onToggleGestures = {
                            viewModel.updateSettings(currentSettings.copy(enableVideoPlayerGestures = it))
                        },
                        onToggleBetaTest = {
                            viewModel.updateSettings(currentSettings.copy(betaTestPrivacy = it))
                        },
                        onToggleUncached = {
                            viewModel.updateSettings(currentSettings.copy(allowUncachedDownloads = it))
                        }
                    )
                }
                SettingsSection.DISPLAY -> {
                    SettingsDisplaySection(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        themeName = themeName,
                        onThemeChange = {
                            themeName = it
                            saveAllSettings()
                        },
                        accentHex = accentHex,
                        onAccentChange = {
                            accentHex = it
                            saveAllSettings()
                        },
                        showCards = currentSettings.showManagementCards,
                        onShowCardsChange = {
                            viewModel.updateSettings(currentSettings.copy(showManagementCards = it))
                        },
                        appIconStyle = currentSettings.appIconStyle,
                        onAppIconStyleChange = {
                            viewModel.updateSettings(currentSettings.copy(appIconStyle = it))
                        },
                        transitionStyle = currentSettings.transitionStyle,
                        onTransitionStyleChange = {
                            viewModel.updateSettings(currentSettings.copy(transitionStyle = it))
                        }
                    )
                }
                SettingsSection.PRIVACY -> {
                    SettingsPrivacySection(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        betaTestPrivacy = currentSettings.betaTestPrivacy,
                        onBetaTestPrivacyChange = {
                            viewModel.updateSettings(currentSettings.copy(betaTestPrivacy = it))
                        }
                    )
                }
                SettingsSection.INTEGRATIONS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp) // MUSE-REF
                    ) {
                        IntegrationsDropdownDebridSection(
                            modifier = Modifier.fillMaxWidth(),
                            realDebridKey = rdKey,
                            onRealDebridKeyChange = {
                                rdKey = it
                                viewModel.updateSettings(currentSettings.copy(realDebridApiKey = it.trim()))
                            },
                            torboxKey = torboxKey,
                            onTorboxKeyChange = {
                                torboxKey = it
                                viewModel.updateSettings(currentSettings.copy(torboxApiKey = it.trim()))
                            },
                            debridOrder = currentSettings.debridOrder,
                            onDebridOrderChange = {
                                viewModel.updateSettings(currentSettings.copy(debridOrder = it))
                            },
                            allowUncachedDownloads = currentSettings.allowUncachedDownloads,
                            onAllowUncachedChange = {
                                viewModel.updateSettings(currentSettings.copy(allowUncachedDownloads = it))
                            }
                        )

                        // MUSE-REF: Metadata Card in Grouped Card format
                        GroupedCard {
                            Column(
                                modifier = Modifier.padding(20.dp), // MUSE-REF
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Metadata",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = palette.textPrimary // MUSE-REF
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Key,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "StashDB API Key",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = palette.textPrimary // MUSE-REF
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            clipboardManager.getText()?.text?.let { clipboardText ->
                                                if (clipboardText.isNotBlank()) {
                                                    stashDbKey = clipboardText.trim()
                                                    viewModel.updateSettings(currentSettings.copy(stashDbApiKey = clipboardText.trim()))
                                                }
                                            }
                                        },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_app_paste),
                                            contentDescription = "Paste",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Paste", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                OutlinedTextField(
                                    value = stashDbKey,
                                    onValueChange = {
                                        stashDbKey = it
                                        viewModel.updateSettings(currentSettings.copy(stashDbApiKey = it.trim()))
                                    },
                                    placeholder = { Text("Paste StashDB API token here...", fontSize = 14.sp) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, lineHeight = 20.sp, color = palette.textPrimary),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Key,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier
                                                .padding(start = 10.dp)
                                                .size(22.dp)
                                        )
                                    },
                                    visualTransformation = if (showStashDbKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showStashDbKey = !showStashDbKey }) {
                                            Icon(
                                                imageVector = if (showStashDbKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (showStashDbKey) "Hide API Key" else "Show API Key"
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    maxLines = 1,
                                    shape = RoundedCornerShape(28.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("stashdb_api_key_input")
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "Get API key from stashdb.org profile",
                                        color = palette.textSecondary, // MUSE-REF
                                        fontSize = 12.sp
                                    )
                                }

                                if (stashDbKey.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                stashDbKey = ""
                                                viewModel.updateSettings(currentSettings.copy(stashDbApiKey = ""))
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Clear Token", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                SettingsSection.DATA_BACKUP -> {
                    DataBackupSection(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        onExportJson = { viewModel.exportDataJson() },
                        onImportJson = { jsonStr -> viewModel.importJsonData(jsonStr) }
                    )
                }
                SettingsSection.ADVANCED -> {
                    SettingsAdvancedSection(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        enableVideoPlayerGestures = currentSettings.enableVideoPlayerGestures,
                        onEnableVideoPlayerGesturesChange = {
                            viewModel.updateSettings(currentSettings.copy(enableVideoPlayerGestures = it))
                        }
                    )
                }
                SettingsSection.SAMPLE_DATA -> {
                    SettingsSampleDataSection(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        sampleDataStatus = sampleDataStatus,
                        accentColor = accent,
                        onLoadSample = {
                            viewModel.importSampleDataset { count ->
                                sampleDataStatus = "Loaded $count sample scenes successfully!"
                            }
                        },
                        onClearSample = {
                            viewModel.clearSampleDataset { count ->
                                sampleDataStatus = "Cleared $count sample scenes!"
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * MUSE-REF: Main Settings Menu organized in 6 distinct Grouped Cards:
 * [Appearance & Colors] [Playback & Downloads] [Data & Backup] [StashDB] [Advanced] [About]
 */
@Composable
private fun SettingsMainMenu(
    modifier: Modifier = Modifier,
    themeName: String,
    accentHex: String,
    currentSettings: SettingsEntity,
    stashDbKey: String,
    rdKeyConfigured: Boolean,
    onNavigateTo: (SettingsSection) -> Unit,
    onToggleCards: (Boolean) -> Unit,
    onToggleGestures: (Boolean) -> Unit,
    onToggleBetaTest: (Boolean) -> Unit,
    onToggleUncached: (Boolean) -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. [Appearance & Colors]
        SettingsSectionHeader(text = "Appearance & Colors") // MUSE-REF
        GroupedCard {
            SettingsRow(
                title = "Theme",
                subtitle = themeName,
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.DISPLAY) }
            )
            SettingsDivider()
            SettingsRow(
                title = "Accent & Color Palette",
                subtitle = accentHex.replace("_", " ").replaceFirstChar { it.uppercase() },
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.DISPLAY) }
            )
            SettingsDivider()
            SettingsRow(
                title = "Transition Animation",
                subtitle = when (currentSettings.transitionStyle) {
                    1 -> "Lateral Slide"
                    2 -> "Smooth Fade & Scale"
                    3 -> "Link Transition"
                    else -> "Dynamic Vertical"
                },
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.DISPLAY) }
            )
            SettingsDivider()
            SettingsRow(
                title = "Cards Layout",
                subtitle = "Display cards for Actors and Studios management",
                trailing = {
                    Switch(
                        checked = currentSettings.showManagementCards,
                        onCheckedChange = onToggleCards,
                        colors = museSwitchColors(), // MUSE-REF
                        modifier = Modifier.testTag("cards_management_switch")
                    )
                },
                onClick = { onToggleCards(!currentSettings.showManagementCards) }
            )
            SettingsDivider()
            SettingsRow(
                title = "App Icons",
                subtitle = "Customize app launcher icon style",
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.DISPLAY) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp)) // MUSE-REF

        // 2. [Playback & Downloads]
        SettingsSectionHeader(text = "Playback & Downloads") // MUSE-REF
        GroupedCard {
            SettingsRow(
                title = "Debrid Services",
                subtitle = if (rdKeyConfigured) "Real-Debrid / Torbox Active" else "Real-Debrid & Torbox",
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.INTEGRATIONS) }
            )
            SettingsDivider()
            SettingsRow(
                title = "Allow Uncached Downloads",
                subtitle = "Stream torrents through Debrid while downloading",
                trailing = {
                    Switch(
                        checked = currentSettings.allowUncachedDownloads,
                        onCheckedChange = onToggleUncached,
                        colors = museSwitchColors() // MUSE-REF
                    )
                },
                onClick = { onToggleUncached(!currentSettings.allowUncachedDownloads) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp)) // MUSE-REF

        // 3. [Data & Backup]
        SettingsSectionHeader(text = "Data & Backup") // MUSE-REF
        GroupedCard {
            SettingsRow(
                title = "Data & Backup",
                subtitle = "Export & Import JSON database backups",
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.DATA_BACKUP) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp)) // MUSE-REF

        // 4. [StashDB]
        SettingsSectionHeader(text = "StashDB") // MUSE-REF
        GroupedCard {
            SettingsRow(
                title = "StashDB API Key",
                subtitle = if (stashDbKey.isNotBlank()) "Configured ••••••••" else "Add your API token to search scenes & performers",
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.INTEGRATIONS) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp)) // MUSE-REF

        // 5. [Advanced]
        SettingsSectionHeader(text = "Advanced") // MUSE-REF
        GroupedCard {
            SettingsRow(
                title = "Player Gestures",
                subtitle = "Control volume and brightness by vertical swipes in player",
                trailing = {
                    Switch(
                        checked = currentSettings.enableVideoPlayerGestures,
                        onCheckedChange = onToggleGestures,
                        colors = museSwitchColors() // MUSE-REF
                    )
                },
                onClick = { onToggleGestures(!currentSettings.enableVideoPlayerGestures) }
            )
            SettingsDivider()
            SettingsRow(
                title = "Beta Test Privacy",
                subtitle = "Apply smart privacy blur to all media images",
                trailing = {
                    Switch(
                        checked = currentSettings.betaTestPrivacy,
                        onCheckedChange = onToggleBetaTest,
                        colors = museSwitchColors(), // MUSE-REF
                        modifier = Modifier.testTag("beta_test_privacy_switch")
                    )
                },
                onClick = { onToggleBetaTest(!currentSettings.betaTestPrivacy) }
            )
            SettingsDivider()
            SettingsRow(
                title = "Sample Dataset",
                subtitle = "Load realistic sample data or clean database",
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.SAMPLE_DATA) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp)) // MUSE-REF

        // 6. [About]
        SettingsSectionHeader(text = "About") // MUSE-REF
        GroupedCard {
            SettingsRow(
                title = "Goony Vault",
                subtitle = "Version 1.0.0 (Build 42)",
                trailing = null,
                onClick = null
            )
            SettingsDivider()
            SettingsRow(
                title = "Design System",
                subtitle = "Material 3 • Grouped Cards Reference",
                trailing = null,
                onClick = null
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * MUSE-REF: Advanced Section in Grouped Card format
 */
@Composable
private fun SettingsAdvancedSection(
    modifier: Modifier = Modifier,
    enableVideoPlayerGestures: Boolean,
    onEnableVideoPlayerGesturesChange: (Boolean) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GroupedCard {
            SettingsRow(
                title = "Brightness & Volume Gestures",
                subtitle = "Control volume and brightness by vertical swipes in the video overlay player. When turned off, vertical scrolling over the video passes through smoothly.",
                trailing = {
                    Switch(
                        checked = enableVideoPlayerGestures,
                        onCheckedChange = onEnableVideoPlayerGesturesChange,
                        colors = museSwitchColors() // MUSE-REF
                    )
                },
                onClick = { onEnableVideoPlayerGesturesChange(!enableVideoPlayerGestures) }
            )
        }
    }
}

/**
 * MUSE-REF: Privacy Section in Grouped Card format
 */
@Composable
private fun SettingsPrivacySection(
    modifier: Modifier = Modifier,
    betaTestPrivacy: Boolean,
    onBetaTestPrivacyChange: (Boolean) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GroupedCard {
            SettingsRow(
                title = "Beta Test",
                subtitle = "Loads all media seamlessly in the app while applying a smart privacy blur to obscure image content across all screens.",
                trailing = {
                    Switch(
                        checked = betaTestPrivacy,
                        onCheckedChange = onBetaTestPrivacyChange,
                        colors = museSwitchColors(), // MUSE-REF
                        modifier = Modifier.testTag("beta_test_privacy_switch")
                    )
                },
                onClick = { onBetaTestPrivacyChange(!betaTestPrivacy) }
            )
        }
    }
}

/**
 * MUSE-REF: Display Section in Grouped Card format
 */
@Composable
private fun SettingsDisplaySection(
    modifier: Modifier = Modifier,
    themeName: String,
    onThemeChange: (String) -> Unit,
    accentHex: String,
    onAccentChange: (String) -> Unit,
    showCards: Boolean,
    onShowCardsChange: (Boolean) -> Unit,
    appIconStyle: Int,
    onAppIconStyleChange: (Int) -> Unit,
    transitionStyle: Int,
    onTransitionStyleChange: (Int) -> Unit
) {
    val palette = LocalVaultPalette.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp) // MUSE-REF
    ) {
        // Theme selection (Native UI with Dark, Amoled, Light) in Grouped Card
        GroupedCard {
            Column(
                modifier = Modifier.padding(20.dp), // MUSE-REF
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = palette.textPrimary // MUSE-REF
                )

                NativeThemeSelector(
                    selectedTheme = themeName,
                    onSelectTheme = onThemeChange
                )
            }
        }

        // Transition Animation selection in Grouped Card
        GroupedCard {
            Column(
                modifier = Modifier.padding(20.dp), // MUSE-REF
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Transition Animation",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = palette.textPrimary // MUSE-REF
                )

                NativeTransitionSelector(
                    selectedStyle = transitionStyle,
                    onSelectStyle = onTransitionStyleChange
                )
            }
        }

        // Cards layout toggle in Grouped Card
        GroupedCard {
            SettingsRow(
                title = "Cards Layout",
                subtitle = "Display cards for Actors and Studios management",
                trailing = {
                    Switch(
                        checked = showCards,
                        onCheckedChange = onShowCardsChange,
                        colors = museSwitchColors(), // MUSE-REF
                        modifier = Modifier.testTag("cards_management_switch")
                    )
                },
                onClick = { onShowCardsChange(!showCards) }
            )
        }

        // App Icon Style Picker in Grouped Card
        GroupedCard {
            Box(modifier = Modifier.padding(20.dp)) { // MUSE-REF
                IconStylePicker(
                    selectedIndex = appIconStyle,
                    onSelectIconStyle = onAppIconStyleChange
                )
            }
        }

        // Color Palette in Grouped Card
        GroupedCard {
            Box(modifier = Modifier.padding(20.dp)) { // MUSE-REF
                ColorPalettePicker(
                    selectedId = accentHex,
                    onSelectPalette = onAccentChange
                )
            }
        }
    }
}

fun switchAppIcon(context: android.content.Context, styleIndex: Int) {
    val packageManager = context.packageManager
    val packageName = context.packageName

    val aliases = listOf(
        "com.example.MainActivityAliasInverted",
        "com.example.MainActivityAliasDefault",
        "com.example.MainActivityAliasBlue",
        "com.example.MainActivityAliasOrange",
        "com.example.MainActivityAliasDark"
    )

    for ((index, alias) in aliases.withIndex()) {
        val componentName = android.content.ComponentName(packageName, alias)
        val newState = if (index == styleIndex) {
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        try {
            packageManager.setComponentEnabledSetting(
                componentName,
                newState,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Programmatically restart the application via the newly enabled alias explicitly to apply the icon change immediately
    try {
        val targetAlias = aliases[styleIndex]
        val intent = android.content.Intent().apply {
            setClassName(packageName, targetAlias)
            putExtra("start_screen", "settings")
            putExtra("start_section", "DISPLAY")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        if (context is android.app.Activity) {
            context.finish()
        }
        // Force close and kill to let Android OS apply the launcher icon immediately!
        android.os.Process.killProcess(android.os.Process.myPid())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
private fun IconStylePicker(
    selectedIndex: Int,
    onSelectIconStyle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalAccentColor.current
    val palette = LocalVaultPalette.current // MUSE-REF
    val options = listOf(
        Triple("Inverted", Color(0xFFF3F4F6), Color.Black),
        Triple("Default", Color(0xFF58595e), Color.White),
        Triple("Blue", Color(0xFF3B82F6), Color.White),
        Triple("Orange", Color(0xFFD97706), Color.White),
        Triple("Dark", Color(0xFF1F2937), Color.White)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Icons",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = palette.textPrimary // MUSE-REF
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, (label, bgColor, fgColor) ->
                val isSelected = selectedIndex == index

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) accent.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) accent else palette.border.copy(alpha = 0.3f), // MUSE-REF
                                shape = CircleShape
                            )
                            .clickable {
                                onSelectIconStyle(index)
                                switchAppIcon(context, index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(fgColor, CircleShape)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = if (isSelected) accent else palette.textSecondary // MUSE-REF
                    )
                }
            }
        }
    }
}

/**
 * MUSE-REF: Sample Data Section in Grouped Card format
 */
@Composable
private fun SettingsSampleDataSection(
    modifier: Modifier = Modifier,
    sampleDataStatus: String,
    accentColor: Color,
    onLoadSample: () -> Unit,
    onClearSample: () -> Unit
) {
    val palette = LocalVaultPalette.current // MUSE-REF
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GroupedCard {
            Column(
                modifier = Modifier.padding(20.dp), // MUSE-REF
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Sample dataset management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary // MUSE-REF
                )
                Text(
                    "Load realistic sample data (studios, actors, scenes with magnets) or clean them completely from the database",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary // MUSE-REF
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onLoadSample,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_app_add), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load Sample")
                    }

                    OutlinedButton(
                        onClick = onClearSample,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All")
                    }
                }

                if (sampleDataStatus.isNotEmpty()) {
                    Text(
                        sampleDataStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary // MUSE-REF
                    )
                }
            }
        }
    }
}
