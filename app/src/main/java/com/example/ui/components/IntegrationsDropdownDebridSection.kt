package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.network.NetworkClient
import com.example.network.await
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

enum class DebridServiceOption(
    val id: String,
    val title: String,
    val tokenUrlHint: String
) {
    REAL_DEBRID(
        id = "real_debrid",
        title = "Real-Debrid",
        tokenUrlHint = "Get API token from real-debrid.com/apitoken"
    ),
    TORBOX(
        id = "torbox",
        title = "Torbox",
        tokenUrlHint = "Get API key from torbox.app/settings"
    )
}

/**
 * INTEG-REDESIGN: Integrations Debrid Section redesigned in full MUSE-REF Grouped Card style:
 * - Section header "Debrid" outside the Card
 * - Card: RoundedCornerShape(24.dp), palette.cardBg, elevation 0.dp, border null
 * - Row 1: Provider selection row with anchored DropdownMenu (Check icon, 48dp height items)
 * - Row 2: API key field with 16dp radius, inline eye & paste icon buttons
 * - Row 3: Test connection row with 36dp Zap icon circle and inline color-coded result
 * - Row 4: Priority selection row with anchored DropdownMenu
 * - Row 5: Allow uncached downloads SettingsRow with MUSE-REF Switch styling
 */
@Composable
fun IntegrationsDropdownDebridSection(
    realDebridKey: String,
    onRealDebridKeyChange: (String) -> Unit,
    torboxKey: String,
    onTorboxKeyChange: (String) -> Unit,
    debridOrder: String = "AUTO",
    onDebridOrderChange: (String) -> Unit = {},
    allowUncachedDownloads: Boolean = false,
    onAllowUncachedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current // INTEG-REDESIGN
    val accent = LocalAccentColor.current // INTEG-REDESIGN
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f // INTEG-REDESIGN

    var selectedService by remember {
        mutableStateOf(
            if (realDebridKey.isNotBlank()) DebridServiceOption.REAL_DEBRID
            else if (torboxKey.isNotBlank()) DebridServiceOption.TORBOX
            else DebridServiceOption.REAL_DEBRID
        )
    }

    var isDropdownExpanded by remember { mutableStateOf(false) }
    var isOrderDropdownExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var showApiKey by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    val activeKey = when (selectedService) {
        DebridServiceOption.REAL_DEBRID -> realDebridKey
        DebridServiceOption.TORBOX -> torboxKey
    }

    val onActiveKeyChange: (String) -> Unit = { newKey ->
        testStatusMessage = null
        when (selectedService) {
            DebridServiceOption.REAL_DEBRID -> onRealDebridKeyChange(newKey)
            DebridServiceOption.TORBOX -> onTorboxKeyChange(newKey)
        }
    }

    fun testConnection() {
        val key = activeKey.trim()
        if (key.isEmpty()) {
            testStatusMessage = "❌ API key is empty."
            return
        }

        isTestingConnection = true
        testStatusMessage = "Testing connection..."

        coroutineScope.launch {
            val resultMsg = withContext(Dispatchers.IO) {
                try {
                    if (selectedService == DebridServiceOption.REAL_DEBRID) {
                        val req = Request.Builder()
                            .url("https://api.real-debrid.com/rest/1.0/user")
                            .header("Authorization", "Bearer $key")
                            .build()
                        val resp = NetworkClient.apiClient.newCall(req).await()
                        val body = resp.body?.string().orEmpty()
                        val code = resp.code
                        resp.close()

                        if (code in 200..299) {
                            val json = JSONObject(body)
                            val username = json.optString("username", "User")
                            val type = json.optString("type", "free")
                            val expiration = json.optString("expiration", "")
                            val expDate = if (expiration.length >= 10) expiration.take(10) else ""
                            "✓ Connected! $username ($type) ${if (expDate.isNotEmpty()) "Expires: $expDate" else ""}"
                        } else {
                            "❌ Auth Failed (HTTP $code)"
                        }
                    } else {
                        val req = Request.Builder()
                            .url("https://api.torbox.app/v1/api/user/me")
                            .header("Authorization", "Bearer $key")
                            .build()
                        val resp = NetworkClient.apiClient.newCall(req).await()
                        val body = resp.body?.string().orEmpty()
                        val code = resp.code
                        resp.close()

                        if (code in 200..299) {
                            val json = JSONObject(body)
                            val dataObj = json.optJSONObject("data")
                            val email = dataObj?.optString("email", "User") ?: "User"
                            val plan = dataObj?.optInt("plan", 0) ?: 0
                            val isPremium = plan > 0
                            "✓ Connected! $email (${if (isPremium) "Premium Plan $plan" else "Free Plan"})"
                        } else {
                            "❌ Auth Failed (HTTP $code)"
                        }
                    }
                } catch (e: Exception) {
                    "❌ Connection error: ${e.message}"
                }
            }
            testStatusMessage = resultMsg
            isTestingConnection = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ① SettingsSectionHeader("Debrid") outside the Card
        Text(
            text = "Debrid",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp, // INTEG-REDESIGN
                fontWeight = FontWeight.Bold
            ),
            color = palette.textMuted, // INTEG-REDESIGN
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp) // INTEG-REDESIGN
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp), // INTEG-REDESIGN: 24dp radius
            colors = CardDefaults.cardColors(containerColor = palette.cardBg), // INTEG-REDESIGN: palette.cardBg
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // INTEG-REDESIGN: 0dp elevation
            border = null // INTEG-REDESIGN: no border
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row 1: Provider Selection Row
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDropdownExpanded = true }
                            .padding(vertical = 18.dp, horizontal = 20.dp), // INTEG-REDESIGN
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Provider",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp, // INTEG-REDESIGN
                                fontWeight = FontWeight.Medium
                            ),
                            color = palette.textPrimary // INTEG-REDESIGN
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = selectedService.title,
                                fontSize = 14.sp, // INTEG-REDESIGN
                                color = palette.textPrimary // INTEG-REDESIGN
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                                contentDescription = null,
                                tint = palette.textMuted, // INTEG-REDESIGN
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier.background(palette.cardBg), // INTEG-REDESIGN: palette.cardBg
                        shape = RoundedCornerShape(16.dp), // INTEG-REDESIGN: 16dp
                        tonalElevation = 8.dp // INTEG-REDESIGN: 8dp
                    ) {
                        DebridServiceOption.values().forEach { option ->
                            val isCurrentSelected = selectedService == option
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp) // INTEG-REDESIGN: 48dp height
                                            .padding(horizontal = 16.dp), // INTEG-REDESIGN: 16dp horizontal
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isCurrentSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = accent, // INTEG-REDESIGN
                                                modifier = Modifier.size(18.dp) // INTEG-REDESIGN: 18dp check
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.width(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = option.title,
                                            fontSize = 14.sp, // INTEG-REDESIGN: 14sp
                                            color = palette.textPrimary, // INTEG-REDESIGN
                                            fontWeight = if (isCurrentSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                },
                                onClick = {
                                    selectedService = option
                                    testStatusMessage = null
                                    isDropdownExpanded = false
                                },
                                contentPadding = PaddingValues(0.dp)
                            )
                        }
                    }
                }

                // Divider 1
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp), // INTEG-REDESIGN
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.10f) // INTEG-REDESIGN
                )

                // Row 2: API Key OutlinedTextField
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 20.dp) // INTEG-REDESIGN
                ) {
                    OutlinedTextField(
                        value = activeKey,
                        onValueChange = onActiveKeyChange,
                        placeholder = {
                            Text(
                                "Paste ${selectedService.title} API token here...",
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
                            focusedContainerColor = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.05f), // INTEG-REDESIGN
                            unfocusedContainerColor = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.05f), // INTEG-REDESIGN
                            focusedBorderColor = accent, // INTEG-REDESIGN
                            unfocusedBorderColor = palette.border, // INTEG-REDESIGN
                            focusedTextColor = palette.textPrimary, // INTEG-REDESIGN
                            unfocusedTextColor = palette.textPrimary, // INTEG-REDESIGN
                            cursorColor = accent
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null,
                                tint = accent, // INTEG-REDESIGN
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(22.dp) // INTEG-REDESIGN: 22dp
                            )
                        },
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showApiKey) "Hide API Key" else "Show API Key",
                                        tint = palette.textSecondary // INTEG-REDESIGN
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboardManager.getText()?.text?.let { clipboardText ->
                                            if (clipboardText.isNotBlank()) {
                                                onActiveKeyChange(clipboardText.trim())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_app_paste),
                                        contentDescription = "Paste",
                                        tint = palette.textSecondary, // INTEG-REDESIGN
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(16.dp), // INTEG-REDESIGN: 16dp
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp) // INTEG-REDESIGN: 56dp
                            .testTag("debrid_api_key_input")
                    )

                    Text(
                        text = selectedService.tokenUrlHint,
                        fontSize = 12.sp,
                        color = palette.textSecondary, // INTEG-REDESIGN
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                // Divider 2
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp), // INTEG-REDESIGN
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.10f) // INTEG-REDESIGN
                )

                // Row 3: Test Connection Row
                val testSubtitle = when {
                    isTestingConnection -> "Testing connection..."
                    testStatusMessage != null -> testStatusMessage!!
                    else -> "Verify your API key with the provider"
                }
                val testSubtitleColor = when {
                    testStatusMessage?.startsWith("✓") == true -> Color(0xFF30D158) // INTEG-REDESIGN: 0xFF30D158
                    testStatusMessage?.startsWith("❌") == true -> MaterialTheme.colorScheme.error
                    else -> palette.textSecondary
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = activeKey.isNotBlank() && !isTestingConnection) {
                            testConnection()
                        }
                        .padding(vertical = 18.dp, horizontal = 20.dp), // INTEG-REDESIGN
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp) // INTEG-REDESIGN: 36dp circle
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.15f)), // INTEG-REDESIGN
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt, // INTEG-REDESIGN: Bolt icon
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp) // INTEG-REDESIGN: 18dp
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Test Connection",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp, // INTEG-REDESIGN
                                    fontWeight = FontWeight.Medium
                                ),
                                color = palette.textPrimary // INTEG-REDESIGN
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = testSubtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp // INTEG-REDESIGN
                                ),
                                color = testSubtitleColor
                            )
                        }
                    }

                    if (isTestingConnection) {
                        Spacer(modifier = Modifier.width(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = accent
                        )
                    }
                }

                // Divider 3
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp), // INTEG-REDESIGN
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.10f) // INTEG-REDESIGN
                )

                // Row 4: Priority Selection Row
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isOrderDropdownExpanded = true }
                            .padding(vertical = 18.dp, horizontal = 20.dp), // INTEG-REDESIGN
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Priority",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp, // INTEG-REDESIGN
                                    fontWeight = FontWeight.Medium
                                ),
                                color = palette.textPrimary // INTEG-REDESIGN
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (debridOrder) {
                                    "REAL_DEBRID_FIRST" -> "Real-Debrid first"
                                    "TORBOX_FIRST" -> "Torbox first"
                                    else -> "Auto (Cache check -> instant stream)"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp // INTEG-REDESIGN
                                ),
                                color = palette.textSecondary // INTEG-REDESIGN
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                            contentDescription = null,
                            tint = palette.textMuted, // INTEG-REDESIGN
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isOrderDropdownExpanded,
                        onDismissRequest = { isOrderDropdownExpanded = false },
                        modifier = Modifier.background(palette.cardBg), // INTEG-REDESIGN: palette.cardBg
                        shape = RoundedCornerShape(16.dp), // INTEG-REDESIGN: 16dp
                        tonalElevation = 8.dp // INTEG-REDESIGN: 8dp
                    ) {
                        val priorityOptions = listOf(
                            "AUTO" to "Auto (Cache check -> instant stream)",
                            "REAL_DEBRID_FIRST" to "Real-Debrid first",
                            "TORBOX_FIRST" to "Torbox first"
                        )
                        priorityOptions.forEach { (key, label) ->
                            val isCurrentSelected = debridOrder == key || (key == "AUTO" && debridOrder != "REAL_DEBRID_FIRST" && debridOrder != "TORBOX_FIRST")
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp) // INTEG-REDESIGN: 48dp height
                                            .padding(horizontal = 16.dp), // INTEG-REDESIGN: 16dp horizontal
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isCurrentSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = accent, // INTEG-REDESIGN
                                                modifier = Modifier.size(18.dp) // INTEG-REDESIGN: 18dp check
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.width(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = label,
                                            fontSize = 14.sp, // INTEG-REDESIGN: 14sp
                                            color = palette.textPrimary, // INTEG-REDESIGN
                                            fontWeight = if (isCurrentSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                },
                                onClick = {
                                    onDebridOrderChange(key)
                                    isOrderDropdownExpanded = false
                                },
                                contentPadding = PaddingValues(0.dp)
                            )
                        }
                    }
                }

                // Divider 4
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp), // INTEG-REDESIGN
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.10f) // INTEG-REDESIGN
                )

                // Row 5: Allow Uncached Downloads Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAllowUncachedChange(!allowUncachedDownloads) }
                        .padding(vertical = 18.dp, horizontal = 20.dp), // INTEG-REDESIGN
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Allow Uncached Downloads",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp, // INTEG-REDESIGN
                                fontWeight = FontWeight.Medium
                            ),
                            color = palette.textPrimary // INTEG-REDESIGN
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "If enabled, non-cached torrents will download to cloud account.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp // INTEG-REDESIGN
                            ),
                            color = palette.textSecondary // INTEG-REDESIGN
                        )
                    }

                    Switch(
                        checked = allowUncachedDownloads,
                        onCheckedChange = onAllowUncachedChange,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFF057DF2), // INTEG-REDESIGN: MUSE-REF Switch style
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.14f),
                            uncheckedThumbColor = Color(0xFF9A9A9E),
                            uncheckedBorderColor = Color.Transparent,
                            checkedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}
