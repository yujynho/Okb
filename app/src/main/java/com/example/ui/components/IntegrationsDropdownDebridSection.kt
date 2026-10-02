package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Debrid Service Setup",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 10.dp).size(22.dp)
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        shape = RoundedCornerShape(20.dp)
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
                                            color = if (isCurrentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isOptionConfigured) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Configured",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF10B981),
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
                                        tint = if (isCurrentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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

                // API Key Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedService.title} API Key",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
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
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 10.dp).size(22.dp)
                        )
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "Hide API Key" else "Show API Key"
                            )
                        }
                    },
                    singleLine = true,
                    maxLines = 1,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("debrid_api_key_input")
                )

                // Test Connection Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedService.tokenUrlHint,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(
                        onClick = { testConnection() },
                        enabled = activeKey.isNotBlank() && !isTestingConnection,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Test Connection", fontSize = 12.sp)
                    }
                }

                if (testStatusMessage != null) {
                    Text(
                        text = testStatusMessage!!,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (testStatusMessage!!.startsWith("✓")) Color(0xFF10B981) else MaterialTheme.colorScheme.error
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Provider Order Selection
                Text(
                    text = "Provider Priority Order",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 10.dp).size(22.dp)
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isOrderDropdownExpanded)
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = isOrderDropdownExpanded,
                        onDismissRequest = { isOrderDropdownExpanded = false },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Auto (Cache Check -> Instant Stream)") },
                            onClick = { onDebridOrderChange("AUTO"); isOrderDropdownExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Real-Debrid First") },
                            onClick = { onDebridOrderChange("REAL_DEBRID_FIRST"); isOrderDropdownExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Torbox First") },
                            onClick = { onDebridOrderChange("TORBOX_FIRST"); isOrderDropdownExpanded = false }
                        )
                    }
                }

                // Allow Uncached Downloads Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow Uncached Cloud Downloads",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "If enabled, non-cached torrents will download to cloud account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = allowUncachedDownloads,
                        onCheckedChange = onAllowUncachedChange
                    )
                }
            }
        }
    }
}
