package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * SELECT-UNIFY: Integrations Debrid Section unified into MUSE-REF Design System:
 * - Card containerColor: palette.cardBg, radius 24.dp, elevation 0.dp, border null
 * - Section headers: 13sp Bold, palette.textMuted with padding(bottom=4.dp)
 * - OutlinedTextFields: RoundedCornerShape(16.dp) with unfocusedContainerColor Color.White.copy(0.05f)
 * - Test Connection: TextButton row style with accent color
 * - Allow Uncached Downloads: SettingsRow layout with MUSE-REF Switch styling
 * - DropdownMenu: containerColor palette.cardBg, RoundedCornerShape(16.dp)
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val palette = LocalVaultPalette.current // SELECT-UNIFY
    val accent = LocalAccentColor.current // SELECT-UNIFY

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

    // Common TextField colors matching MUSE-REF
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White.copy(alpha = 0.05f), // SELECT-UNIFY
        unfocusedContainerColor = Color.White.copy(alpha = 0.05f), // SELECT-UNIFY
        focusedBorderColor = accent, // SELECT-UNIFY
        unfocusedBorderColor = palette.border, // SELECT-UNIFY
        focusedTextColor = palette.textPrimary, // SELECT-UNIFY
        unfocusedTextColor = palette.textPrimary, // SELECT-UNIFY
        focusedLabelColor = accent, // SELECT-UNIFY
        unfocusedLabelColor = palette.textSecondary, // SELECT-UNIFY
        cursorColor = accent // SELECT-UNIFY
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SELECT-UNIFY: GroupedCard with shape = 24.dp and palette.cardBg
        Card(
            shape = RoundedCornerShape(24.dp), // SELECT-UNIFY: 24dp
            colors = CardDefaults.cardColors(containerColor = palette.cardBg), // SELECT-UNIFY: palette.cardBg
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // SELECT-UNIFY: 0dp
            border = null, // SELECT-UNIFY: No border
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp), // SELECT-UNIFY: 20dp padding
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section Header
                Text(
                    text = "Debrid Service Setup",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp, // SELECT-UNIFY: 13sp Bold section header
                        fontWeight = FontWeight.Bold
                    ),
                    color = palette.textMuted, // SELECT-UNIFY: palette.textMuted
                    modifier = Modifier.padding(bottom = 4.dp) // SELECT-UNIFY
                )

                // Service Dropdown
                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedService.title,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Provider") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Cloud,
                                contentDescription = null,
                                tint = accent, // SELECT-UNIFY
                                modifier = Modifier.padding(start = 10.dp).size(22.dp)
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                        },
                        shape = RoundedCornerShape(16.dp), // SELECT-UNIFY: 16dp shape
                        colors = textFieldColors, // SELECT-UNIFY
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        shape = RoundedCornerShape(16.dp), // SELECT-UNIFY: 16dp
                        modifier = Modifier.background(palette.cardBg) // SELECT-UNIFY: palette.cardBg
                    ) {
                        DebridServiceOption.values().forEach { option ->
                            val isCurrentSelected = selectedService == option
                            val isOptionConfigured = when (option) {
                                DebridServiceOption.REAL_DEBRID -> realDebridKey.isNotBlank()
                                DebridServiceOption.TORBOX -> torboxKey.isNotBlank()
                            }

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option.title,
                                            fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrentSelected) accent else palette.textPrimary // SELECT-UNIFY
                                        )
                                        if (isOptionConfigured) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFF30D158).copy(alpha = 0.15f) // SELECT-UNIFY: 0xFF30D158
                                            ) {
                                                Text(
                                                    text = "Configured",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF30D158), // SELECT-UNIFY
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Cloud,
                                        contentDescription = null,
                                        tint = if (isCurrentSelected) accent else palette.textSecondary // SELECT-UNIFY
                                    )
                                },
                                onClick = {
                                    selectedService = option
                                    testStatusMessage = null
                                    isDropdownExpanded = false
                                },
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }

                // API Key Field Title & Paste Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = accent, // SELECT-UNIFY
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedService.title} API Key",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.textPrimary // SELECT-UNIFY
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { clipboardText ->
                                if (clipboardText.isNotBlank()) {
                                    onActiveKeyChange(clipboardText.trim())
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
                    value = activeKey,
                    onValueChange = onActiveKeyChange,
                    placeholder = { Text("Paste ${selectedService.title} API token here...", fontSize = 14.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, lineHeight = 20.sp, color = palette.textPrimary),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = accent, // SELECT-UNIFY
                            modifier = Modifier.padding(start = 10.dp).size(22.dp)
                        )
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "Hide API Key" else "Show API Key",
                                tint = palette.textSecondary // SELECT-UNIFY
                            )
                        }
                    },
                    singleLine = true,
                    maxLines = 1,
                    shape = RoundedCornerShape(16.dp), // SELECT-UNIFY: 16dp
                    colors = textFieldColors, // SELECT-UNIFY
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("debrid_api_key_input")
                )

                // Test Connection Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedService.tokenUrlHint,
                        fontSize = 12.sp,
                        color = palette.textSecondary, // SELECT-UNIFY
                        modifier = Modifier.weight(1f)
                    )

                    // SELECT-UNIFY: TextButton row style with accent color
                    TextButton(
                        onClick = { testConnection() },
                        enabled = activeKey.isNotBlank() && !isTestingConnection,
                        shape = RoundedCornerShape(12.dp) // SELECT-UNIFY
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = accent) // SELECT-UNIFY
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "Test Connection",
                            fontSize = 14.sp, // SELECT-UNIFY: 14sp Medium
                            fontWeight = FontWeight.Medium,
                            color = accent // SELECT-UNIFY
                        )
                    }
                }

                if (testStatusMessage != null) {
                    Text(
                        text = testStatusMessage!!,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (testStatusMessage!!.startsWith("✓")) Color(0xFF30D158) else MaterialTheme.colorScheme.error // SELECT-UNIFY
                    )
                }

                if (activeKey.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onActiveKeyChange("") },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Token", fontSize = 13.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = palette.border.copy(alpha = 0.3f))

                // Provider Priority Order Header
                Text(
                    text = "Provider Priority Order",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp, // SELECT-UNIFY: 13sp Bold section header
                        fontWeight = FontWeight.Bold
                    ),
                    color = palette.textMuted, // SELECT-UNIFY: palette.textMuted
                    modifier = Modifier.padding(bottom = 4.dp) // SELECT-UNIFY
                )

                ExposedDropdownMenuBox(
                    expanded = isOrderDropdownExpanded,
                    onExpandedChange = { isOrderDropdownExpanded = !isOrderDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val orderLabel = when (debridOrder) {
                        "REAL_DEBRID_FIRST" -> "Real-Debrid First"
                        "TORBOX_FIRST" -> "Torbox First"
                        else -> "Auto (Cache Check -> Instant Stream)"
                    }

                    OutlinedTextField(
                        value = orderLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority Strategy") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = null,
                                tint = accent, // SELECT-UNIFY
                                modifier = Modifier.padding(start = 10.dp).size(22.dp)
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isOrderDropdownExpanded)
                        },
                        shape = RoundedCornerShape(16.dp), // SELECT-UNIFY: 16dp
                        colors = textFieldColors, // SELECT-UNIFY
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = isOrderDropdownExpanded,
                        onDismissRequest = { isOrderDropdownExpanded = false },
                        shape = RoundedCornerShape(16.dp), // SELECT-UNIFY: 16dp
                        modifier = Modifier.background(palette.cardBg) // SELECT-UNIFY: palette.cardBg
                    ) {
                        DropdownMenuItem(
                            text = { Text("Auto (Cache Check -> Instant Stream)", color = palette.textPrimary) },
                            onClick = { onDebridOrderChange("AUTO"); isOrderDropdownExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Real-Debrid First", color = palette.textPrimary) },
                            onClick = { onDebridOrderChange("REAL_DEBRID_FIRST"); isOrderDropdownExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Torbox First", color = palette.textPrimary) },
                            onClick = { onDebridOrderChange("TORBOX_FIRST"); isOrderDropdownExpanded = false }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = palette.border.copy(alpha = 0.3f))

                // SELECT-UNIFY: Allow Uncached Downloads Row in SettingsRow style
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onAllowUncachedChange(!allowUncachedDownloads) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Allow Uncached Cloud Downloads",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp, // SELECT-UNIFY: 16sp Medium
                                fontWeight = FontWeight.Medium
                            ),
                            color = palette.textPrimary // SELECT-UNIFY
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "If enabled, non-cached torrents will download to cloud account.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp // SELECT-UNIFY: 13sp
                            ),
                            color = palette.textSecondary // SELECT-UNIFY
                        )
                    }

                    Switch(
                        checked = allowUncachedDownloads,
                        onCheckedChange = onAllowUncachedChange,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFF057DF2), // SELECT-UNIFY: MUSE-REF Switch style
                            checkedThumbColor = Color.White, // SELECT-UNIFY
                            uncheckedTrackColor = Color.White.copy(alpha = 0.14f), // SELECT-UNIFY
                            uncheckedThumbColor = Color(0xFF9A9A9E), // SELECT-UNIFY
                            uncheckedBorderColor = Color.Transparent, // SELECT-UNIFY
                            checkedBorderColor = Color.Transparent // SELECT-UNIFY
                        )
                    )
                }
            }
        }
    }
}
