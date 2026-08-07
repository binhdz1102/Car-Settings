@file:Suppress("DEPRECATION")

package com.android.car.settings.feature.bluetooth.presentation

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.car.settings.core.ui.MySystemTheme
import java.nio.charset.StandardCharsets

class BluetoothPairingActivity : ComponentActivity() {
    private var input by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device =
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                ?: run {
                    finish()
                    return
                }
        val variant =
            intent.getIntExtra(
                BluetoothDevice.EXTRA_PAIRING_VARIANT,
                BluetoothDevice.ERROR,
            )
        val pairingKey =
            intent.getIntExtra(
                BluetoothDevice.EXTRA_PAIRING_KEY,
                BluetoothDevice.ERROR,
            )
        setContent {
            MySystemTheme {
                val requiresInput =
                    variant == BluetoothDevice.PAIRING_VARIANT_PIN ||
                        variant == PAIRING_VARIANT_PIN_16_DIGITS ||
                        variant == PAIRING_VARIANT_PASSKEY
                AlertDialog(
                    onDismissRequest = {
                        cancelPairing(device)
                        finish()
                    },
                    title = { Text("Pair with ${device.name ?: device.address}?") },
                    text = {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            when {
                                requiresInput -> {
                                    Text(
                                        if (variant == PAIRING_VARIANT_PASSKEY) {
                                            "Enter the passkey shown on the other device."
                                        } else {
                                            "Enter the PIN for this device."
                                        },
                                    )
                                    OutlinedTextField(
                                        value = input,
                                        onValueChange = {
                                            input = it.filter(Char::isDigit).take(16)
                                        },
                                        label = { Text("PIN or passkey") },
                                        keyboardOptions =
                                            KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                pairingKey != BluetoothDevice.ERROR -> {
                                    Text(
                                        "Confirm that this code appears on the other device:",
                                    )
                                    Text(
                                        text = "%06d".format(pairingKey),
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                }

                                else -> Text("Allow this Bluetooth pairing request?")
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            enabled = !requiresInput || input.isNotBlank(),
                            onClick = {
                                acceptPairing(device, variant, input)
                                finish()
                            },
                        ) {
                            Text("Pair")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                cancelPairing(device)
                                finish()
                            },
                        ) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }

    private fun acceptPairing(
        device: BluetoothDevice,
        variant: Int,
        value: String,
    ) {
        runCatching {
            when (variant) {
                BluetoothDevice.PAIRING_VARIANT_PIN,
                PAIRING_VARIANT_PIN_16_DIGITS,
                ->
                    BluetoothDevice::class.java
                        .getMethod("setPin", ByteArray::class.java)
                        .invoke(device, value.toByteArray(StandardCharsets.UTF_8))

                PAIRING_VARIANT_PASSKEY ->
                    BluetoothDevice::class.java
                        .getMethod("setPasskey", Int::class.javaPrimitiveType)
                        .invoke(device, value.toInt())

                else ->
                    BluetoothDevice::class.java
                        .getMethod(
                            "setPairingConfirmation",
                            Boolean::class.javaPrimitiveType,
                        ).invoke(device, true)
            }
        }
    }

    private fun cancelPairing(device: BluetoothDevice) {
        runCatching {
            BluetoothDevice::class.java
                .getMethod("cancelPairingUserInput")
                .invoke(device)
        }
    }
}

private const val PAIRING_VARIANT_PASSKEY = 1
private const val PAIRING_VARIANT_PIN_16_DIGITS = 7
