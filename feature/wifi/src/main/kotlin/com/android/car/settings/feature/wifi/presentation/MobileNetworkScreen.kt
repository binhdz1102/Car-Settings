package com.android.car.settings.feature.wifi.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.AutomotiveAlertDialog
import com.android.car.settings.core.ui.KeyValueRow
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.core.ui.AutomotiveButton as Button
import com.android.car.settings.core.ui.AutomotiveTextButton as TextButton
import com.android.car.settings.core.ui.BMaterialLazyColumn as LazyColumn

private sealed interface MobileConfirmation {
    data object DisableData : MobileConfirmation

    data object EnableRoaming : MobileConfirmation

    data class ChangeDefaultData(
        val subscription: com.android.car.settings.feature.wifi.domain.MobileSubscription,
    ) : MobileConfirmation
}

@Composable
@Suppress("CyclomaticComplexMethod")
fun MobileNetworkRoute(
    viewModel: WifiViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mobile = state.wifi.mobileNetwork
    var confirmation by remember { mutableStateOf<MobileConfirmation?>(null) }
    SettingsScaffold(title = "Mobile network", onBack = onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = "Mobile data",
                    summary = if (mobile.mobileDataEnabled) "On" else "Off",
                    checked = mobile.mobileDataEnabled,
                    enabled = mobile.mobileDataChangeAllowed && mobile.isSupported && mobile.subscriptions.isNotEmpty() && !state.isWorking,
                    onCheckedChange = { enabled ->
                        if (!enabled && mobile.mobileDataEnabled) {
                            confirmation = MobileConfirmation.DisableData
                        } else {
                            viewModel.setMobileDataEnabled(enabled)
                        }
                    },
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Data roaming",
                    summary = "Use mobile data while roaming",
                    checked = mobile.roamingEnabled,
                    enabled = mobile.roamingChangeAllowed && mobile.isSupported && mobile.subscriptions.isNotEmpty() && !state.isWorking,
                    onCheckedChange = { enabled ->
                        if (enabled && !mobile.roamingEnabled) {
                            confirmation = MobileConfirmation.EnableRoaming
                        } else {
                            viewModel.setDataRoamingEnabled(enabled)
                        }
                    },
                )
            }
            item { SettingsSection("SIMs") {} }
            if (mobile.subscriptions.isEmpty()) {
                item {
                    SettingsActionRow(
                        title = "No mobile subscriptions",
                        summary =
                            if (mobile.isSupported) {
                                "Insert or enable a SIM to use mobile data"
                            } else {
                                "Telephony is not available on this vehicle"
                            },
                        enabled = false,
                        leading = { Icon(Icons.Default.NetworkCell, contentDescription = null) },
                        onClick = {},
                    )
                }
            }
            items(mobile.subscriptions, key = { it.subscriptionId }) { subscription ->
                SettingsActionRow(
                    title = subscription.displayName,
                    summary =
                        buildString {
                            if (subscription.carrierName.isNotBlank()) append(subscription.carrierName)
                            if (subscription.isDefaultData) {
                                if (isNotEmpty()) append(" · ")
                                append("Default for mobile data")
                            }
                        }.ifBlank { "Subscription ${subscription.subscriptionId}" },
                    leading = { Icon(Icons.Default.NetworkCell, contentDescription = null) },
                    onClick = {
                        if (!subscription.isDefaultData && mobile.defaultDataChangeAllowed) {
                            confirmation = MobileConfirmation.ChangeDefaultData(subscription)
                        }
                    },
                    focusId = "mobile-subscription-${subscription.subscriptionId}",
                )
            }
            item {
                SettingsSection("Usage") {
                    KeyValueRow(key = "Last 30 days", value = mobile.dataUsageBytes.toDataUsageString())
                    mobile.warningBytes?.let { KeyValueRow(key = "Warning", value = it.toDataUsageString()) }
                    mobile.limitBytes?.let { KeyValueRow(key = "Limit", value = it.toDataUsageString()) }
                }
            }
            mobile.error?.let { error ->
                item {
                    SettingsActionRow(
                        title = "Mobile network unavailable",
                        summary = error,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
    }

    confirmation?.let { action ->
        val title: String
        val message: String
        val confirmLabel: String
        when (action) {
            MobileConfirmation.DisableData -> {
                title = "Turn off mobile data?"
                message = "Wi‑Fi will be required for internet access until mobile data is turned on again."
                confirmLabel = "Turn off"
            }
            MobileConfirmation.EnableRoaming -> {
                title = "Turn on data roaming?"
                message = "Roaming data may incur additional charges from your carrier."
                confirmLabel = "Turn on"
            }
            is MobileConfirmation.ChangeDefaultData -> {
                title = "Use ${action.subscription.displayName} for mobile data?"
                message = "The current subscription will no longer be used for mobile data."
                confirmLabel = "Switch"
            }
        }
        AutomotiveAlertDialog(
            dialogKey = "mobile-network-confirm",
            onDismissRequest = { confirmation = null },
            title = { Text(title) },
            text = { Text(message) },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) { Text("Cancel") }
            },
            dismissAction = { confirmation = null },
            confirmButton = {
                Button(onClick = { /* native CCP action invokes confirmAction */ }) { Text(confirmLabel) }
            },
            confirmAction = {
                when (action) {
                    MobileConfirmation.DisableData -> viewModel.setMobileDataEnabled(false)
                    MobileConfirmation.EnableRoaming -> viewModel.setDataRoamingEnabled(true)
                    is MobileConfirmation.ChangeDefaultData -> viewModel.setDefaultDataSubscription(action.subscription.subscriptionId)
                }
                confirmation = null
            },
        )
    }
}
