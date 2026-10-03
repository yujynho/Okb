package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.SettingsEntity
import com.example.ui.MainViewModel
import com.example.ui.SettingsSection
import com.example.ui.components.ColorPalettePicker
import com.example.ui.components.DataBackupSection
import com.example.ui.components.IntegrationsDropdownDebridSection
import com.example.ui.components.NativeThemeSelector
import com.example.ui.components.NativeTransitionSelector
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette
import kotlinx.coroutines.launch

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
 * MUSE-REF: Standard Setting Row Composable with optional leading icon:
 * Padding: vertical = 18.dp, horizontal = 20.dp
 * Icon: Optional 44dp circular container with 22dp accent icon
 * Title: 16sp, FontWeight.Medium, color = palette.textPrimary
 * Subtitle: 13sp, color = palette.textSecondary
 * Trailing: Switch or chevron in palette.textMuted
 * Click: standard Material ripple
 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: Painter? = null, // ORG-NEW: Added icon parameter for iOS-style root categories
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
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
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp) // ORG-NEW: 44dp circular background
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = accent, // ORG-NEW: Accent color
                        modifier = Modifier.size(22.dp) // ORG-NEW: 22dp icon
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
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
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

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
            "PRIVACY" -> SettingsSection.PRIVACY // ORG-NEW
            "INTEGRATIONS" -> SettingsSection.INTEGRATIONS
            "FILTER" -> SettingsSection.FILTER
            "DATA_BACKUP" -> SettingsSection.DATA_BACKUP
            "SAMPLE_DATA" -> SettingsSection.SAMPLE_DATA
            else -> SettingsSection.MAIN_MENU // ORG-FIX: Any legacy intent safely lands on MAIN_MENU
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
        SettingsSection.PRIVACY -> "Privacy" // ORG-NEW
        SettingsSection.INTEGRATIONS -> "Integrations"
        SettingsSection.FILTER -> "Filter"
        SettingsSection.DATA_BACKUP -> "Data & Backup"
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

    val topBarContent = LocalTopBarContent.current
    SideEffect {
        topBarContent.value = {
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
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B3C3E)) // MUSE-REF Circular back button #3B3C3E
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null, // No ripple or click effect!
                                onClick = {
                                    if (currentSection != SettingsSection.MAIN_MENU) {
                                        currentSection = SettingsSection.MAIN_MENU
                                    } else {
                                        viewModel.navigateBack()
                                    }
                                }
                            )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_back),
                            contentDescription = "Back",
                            tint = palette.textPrimary, // MUSE-REF
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = palette.surface // MUSE-REF Top bar blends seamlessly with background
                )
            )
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = palette.bg, // MUSE-REF
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {}
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ANIM-FIX: Crossfade + Scale animation spec
            AnimatedContent(
                targetState = currentSection,
                modifier = Modifier.clipToBounds(), // مانع الانسكاب
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                animationSpec = tween(240, easing = FastOutSlowInEasing),
                                initialScale = 0.98f
                            ))
                        .togetherWith(
                            fadeOut(animationSpec = tween(150, easing = FastOutLinearInEasing)) +
                                    scaleOut(
                                        animationSpec = tween(200, easing = FastOutLinearInEasing),
                                        targetScale = 0.985f
                                    )
                        )
                },
                label = "settings_navigation"
            ) { section ->
            when (section) {
                SettingsSection.MAIN_MENU -> {
                    // ORG-FIX: Clean signature with onNavigateTo and currentSettings
                    SettingsMainMenu(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                            .verticalScroll(rememberScrollState()),
                        currentSettings = currentSettings,
                        onNavigateTo = { currentSection = it }
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
                        showManagementCards = currentSettings.showManagementCards, // ORG-MOVED: Single source in Display
                        onShowManagementCardsChange = {
                            viewModel.updateSettings(currentSettings.copy(showManagementCards = it))
                        },
                        appIconStyle = currentSettings.appIconStyle,
                        onAppIconStyleChange = {
                            viewModel.updateSettings(currentSettings.copy(appIconStyle = it))
                        },
                        transitionStyle = currentSettings.transitionStyle,
                        onTransitionStyleChange = {
                            viewModel.updateSettings(currentSettings.copy(transitionStyle = it))
                        },
                        enableVideoPlayerGestures = currentSettings.enableVideoPlayerGestures,
                        onEnableVideoPlayerGesturesChange = {
                            viewModel.updateSettings(currentSettings.copy(enableVideoPlayerGestures = it))
                        }
                    )
                }
                SettingsSection.PRIVACY -> {
                    // ORG-NEW: Dedicated Privacy Sub-screen
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

                        // INTEG-REDESIGN: Metadata Section with external header and unified GroupedCard
                        SettingsSectionHeader(text = "Metadata")
                        GroupedCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 20.dp)
                            ) {
                                Text(
                                    text = "StashDB API Key",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = palette.textPrimary
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = stashDbKey,
                                    onValueChange = {
                                        stashDbKey = it
                                        viewModel.updateSettings(currentSettings.copy(stashDbApiKey = it.trim()))
                                    },
                                    placeholder = {
                                        Text(
                                            "Paste StashDB API token here...",
                                            fontSize = 14.sp,
                                            color = palette.textSecondary
                                        )
                                    },
                                    textStyle = LocalTextStyle.current.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = palette.textPrimary
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.05f),
                                        unfocusedContainerColor = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.05f),
                                        focusedBorderColor = accent,
                                        unfocusedBorderColor = palette.border,
                                        focusedTextColor = palette.textPrimary,
                                        unfocusedTextColor = palette.textPrimary,
                                        cursorColor = accent
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_settings_integrations),
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier
                                                .padding(start = 12.dp)
                                                .size(22.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { showStashDbKey = !showStashDbKey },
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (showStashDbKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (showStashDbKey) "Hide API Key" else "Show API Key",
                                                tint = palette.textSecondary
                                            )
                                        }
                                    },
                                    visualTransformation = if (showStashDbKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    maxLines = 1,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("stashdb_api_key_input")
                                )

                                Text(
                                    text = "Get API key from stashdb.org profile",
                                    fontSize = 12.sp,
                                    color = palette.textSecondary,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                }
                SettingsSection.FILTER -> {
                    SettingsFilterSection(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        enableStudioFilter = currentSettings.enableStudioFilter,
                        onEnableStudioFilterChange = {
                            viewModel.updateSettings(currentSettings.copy(enableStudioFilter = it))
                        },
                        blockedStudioNames = currentSettings.blockedStudioNames,
                        onBlockStudio = { name -> viewModel.blockStudio(null, name) },
                        onUnblockStudio = { name -> viewModel.unblockStudio(name) },
                        onClearAll = { viewModel.clearAllBlockedStudios() }
                    )
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

        // Soft & smooth lightweight gradient mask below header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = padding.calculateTopPadding())
                .height(18.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.surface,
                            palette.surface.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
}

/**
 * ORG-FIX: Main Settings Menu organized as a clean single GroupedCard with exactly 5 category rows:
 * 1. Display
 * 2. Privacy
 * 3. Integrations
 * 4. Data & Backup
 * 5. Sample Dataset
 * Followed by a non-interactive centered version footer.
 */
@Composable
private fun SettingsMainMenu(
    modifier: Modifier = Modifier,
    currentSettings: SettingsEntity,
    onNavigateTo: (SettingsSection) -> Unit
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Single unified GroupedCard containing all category rows
        GroupedCard {
            // 1. Display
            SettingsRow(
                title = "Display",
                subtitle = "Theme, accent, icons & animations",
                icon = painterResource(id = R.drawable.ic_settings_display), // ORG-NEW
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.DISPLAY) }
            )
            SettingsDivider()
            // 2. Privacy - ORG-NEW
            SettingsRow(
                title = "Privacy",
                subtitle = "Content privacy blur",
                icon = painterResource(id = R.drawable.ic_settings_privacy), // ORG-NEW
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.PRIVACY) }
            )
            SettingsDivider()
            // 3. Integrations
            SettingsRow(
                title = "Integrations",
                subtitle = "Real-Debrid, Torbox & StashDB",
                icon = painterResource(id = R.drawable.ic_settings_integrations), // ORG-NEW
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.INTEGRATIONS) }
            )
            SettingsDivider()
            // 4. Filter - NEW
            SettingsRow(
                title = "Filter",
                subtitle = if (currentSettings.blockedStudioNames.isNotEmpty()) {
                    "${currentSettings.blockedStudioNames.size} studio(s) blocked"
                } else {
                    "Blocked studios & content filters"
                },
                icon = painterResource(id = R.drawable.ic_settings_filter),
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (currentSettings.blockedStudioNames.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = accent.copy(alpha = 0.15f),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${currentSettings.blockedStudioNames.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent
                                    )
                                }
                            }
                        }
                        SettingsNavigationChevron()
                    }
                },
                onClick = { onNavigateTo(SettingsSection.FILTER) }
            )
            SettingsDivider()
            // 5. Data & Backup
            SettingsRow(
                title = "Data & Backup",
                subtitle = "Export & restore your vault",
                icon = painterResource(id = R.drawable.ic_settings_backup), // ORG-NEW
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.DATA_BACKUP) }
            )
            SettingsDivider()
            // 6. Sample Dataset
            SettingsRow(
                title = "Sample Dataset",
                subtitle = "Demo data for testing",
                icon = painterResource(id = R.drawable.ic_settings_sample_data), // ORG-NEW
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.SAMPLE_DATA) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp)) // ORG-FIX

        // Non-interactive centered footer text replacing About card
        Text(
            text = "Goony Vault • Version 1.0.0 (Build 42)",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp
            ),
            color = palette.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        ) // ORG-FIX

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsFilterSection(
    modifier: Modifier = Modifier,
    enableStudioFilter: Boolean,
    onEnableStudioFilterChange: (Boolean) -> Unit,
    blockedStudioNames: List<String>,
    onBlockStudio: (String) -> Unit,
    onUnblockStudio: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    var newStudioInput by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = palette.cardBg,
            title = {
                Text(
                    text = "Clear All Blocked Studios?",
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            },
            text = {
                Text(
                    text = "All blocked studios will be removed from the filter and will be allowed to appear in StashDB results again.",
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("Cancel", color = palette.textSecondary, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Master Filter Switch Card
        GroupedCard {
            SettingsRow(
                title = "Enable Studio Filter",
                subtitle = "Exclude blocked studios from StashDB search results",
                icon = painterResource(id = R.drawable.ic_settings_filter),
                trailing = {
                    Switch(
                        checked = enableStudioFilter,
                        onCheckedChange = onEnableStudioFilterChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = palette.textMuted,
                            uncheckedTrackColor = palette.border
                        )
                    )
                },
                onClick = { onEnableStudioFilterChange(!enableStudioFilter) }
            )
        }

        // Add Studio Input Section
        SettingsSectionHeader(text = "Add Studio to Block")
        GroupedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = newStudioInput,
                    onValueChange = { newStudioInput = it },
                    placeholder = {
                        Text(
                            "Enter studio name to block...",
                            fontSize = 14.sp,
                            color = palette.textSecondary
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 14.sp,
                        color = palette.textPrimary
                    ),
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_nav_studio),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (newStudioInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val name = newStudioInput.trim()
                                    if (name.isNotBlank()) {
                                        onBlockStudio(name)
                                        newStudioInput = ""
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Block Studio",
                                    tint = accent
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.05f),
                        focusedBorderColor = accent,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        cursorColor = accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "You can also tap any studio name directly on StashDB scene cards to block it instantly.",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Blocked Studios List Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsSectionHeader(text = "Blocked Studios (${blockedStudioNames.size})")
            if (blockedStudioNames.isNotEmpty()) {
                TextButton(
                    onClick = { showClearConfirmDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Clear All",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (blockedStudioNames.isEmpty()) {
            GroupedCard(modifier = Modifier.height(180.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 20.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = palette.surface,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_settings_filter),
                                    contentDescription = null,
                                    tint = palette.textMuted.copy(alpha = 0.6f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text(
                            text = "No Blocked Studios",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Studios you block from StashDB will appear here. All studio scenes will be displayed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            val listScrollState = rememberScrollState()
            GroupedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(listScrollState)
                    ) {
                        blockedStudioNames.forEachIndexed { index, studioName ->
                            if (index > 0) {
                                SettingsDivider()
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_nav_studio),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = studioName,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp
                                            ),
                                            color = palette.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Blocked from StashDB",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = palette.textSecondary
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onUnblockStudio(studioName) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f),
                                        contentColor = palette.textPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = "Unblock",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    if (listScrollState.canScrollBackward) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(palette.cardBg, Color.Transparent)
                                    )
                                )
                        )
                    }
                    if (listScrollState.canScrollForward) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, palette.cardBg)
                                    )
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * ORG-NEW: Privacy Section as a dedicated sub-screen
 * Single source of truth for Beta Test Privacy
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
                title = "Beta Test Privacy",
                subtitle = "Loads all media seamlessly in the app while applying a smart privacy blur to obscure image content across all screens.",
                trailing = {
                    Switch(
                        checked = betaTestPrivacy,
                        onCheckedChange = onBetaTestPrivacyChange,
                        colors = museSwitchColors(),
                        modifier = Modifier.testTag("beta_test_privacy_switch")
                    )
                },
                onClick = { onBetaTestPrivacyChange(!betaTestPrivacy) }
            ) // ORG-MOVED: MAIN_MENU → PRIVACY (Single source of truth)
        }
    }
}

/**
 * MUSE-REF: Display Section in Grouped Card format
 * ORG-MOVED: Cards Layout toggle moved here as the single source of truth
 */
@Composable
private fun SettingsDisplaySection(
    modifier: Modifier = Modifier,
    themeName: String,
    onThemeChange: (String) -> Unit,
    accentHex: String,
    onAccentChange: (String) -> Unit,
    showManagementCards: Boolean, // ORG-MOVED
    onShowManagementCardsChange: (Boolean) -> Unit, // ORG-MOVED
    appIconStyle: Int,
    onAppIconStyleChange: (Int) -> Unit,
    transitionStyle: Int,
    onTransitionStyleChange: (Int) -> Unit,
    enableVideoPlayerGestures: Boolean,
    onEnableVideoPlayerGesturesChange: (Boolean) -> Unit
) {
    val palette = LocalVaultPalette.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp) // MUSE-REF
    ) {
        // Theme selection in Grouped Card
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

        // Cards layout toggle in Grouped Card - ORG-MOVED: Single source of truth in Display
        GroupedCard {
            SettingsRow(
                title = "Cards Layout",
                subtitle = "Display cards for Actors and Studios management",
                trailing = {
                    Switch(
                        checked = showManagementCards,
                        onCheckedChange = onShowManagementCardsChange,
                        colors = museSwitchColors(), // MUSE-REF
                        modifier = Modifier.testTag("cards_management_switch")
                    )
                },
                onClick = { onShowManagementCardsChange(!showManagementCards) }
            ) // ORG-MOVED: MAIN_MENU → DISPLAY (Single source of truth)
        }

        // Player Gestures toggle in Grouped Card - MOVED TO DISPLAY
        GroupedCard {
            SettingsRow(
                title = "Player Gestures",
                subtitle = "Control volume and brightness by vertical swipes in the video overlay player. When turned off, vertical scrolling over the video passes through smoothly.",
                trailing = {
                    Switch(
                        checked = enableVideoPlayerGestures,
                        onCheckedChange = onEnableVideoPlayerGesturesChange,
                        colors = museSwitchColors(),
                        modifier = Modifier.testTag("player_gestures_switch")
                    )
                },
                onClick = { onEnableVideoPlayerGesturesChange(!enableVideoPlayerGestures) }
            )
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
