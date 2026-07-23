package com.android.car.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.car.settings.ui.theme.CarSettingTheme
import java.util.Locale

@Composable
internal fun HvacTemperatureScreen(
    uiState: HvacUiState,
    onDecreaseDriverTemperature: () -> Unit,
    onIncreaseDriverTemperature: () -> Unit,
    onDecreasePassengerTemperature: () -> Unit,
    onIncreasePassengerTemperature: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .safeDrawingPadding()
                    .padding(contentPadding)
                    .padding(horizontal = 48.dp, vertical = 24.dp),
        ) {
            Header()
            Spacer(modifier = Modifier.height(24.dp))

            when {
                uiState.isLoading -> {
                    LoadingContent(modifier = Modifier.weight(1f))
                }

                uiState.errorMessage != null &&
                    !uiState.driver.isAvailable &&
                    !uiState.passenger.isAvailable -> {
                    ErrorContent(
                        message = uiState.errorMessage,
                        onRetry = onRetry,
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    uiState.errorMessage?.let { message ->
                        InlineError(message)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        TemperatureCard(
                            title = "Tài xế",
                            icon = Icons.Rounded.Person,
                            zone = uiState.driver,
                            decreaseContentDescription = "Giảm nhiệt độ tài xế",
                            increaseContentDescription = "Tăng nhiệt độ tài xế",
                            onDecrease = onDecreaseDriverTemperature,
                            onIncrease = onIncreaseDriverTemperature,
                            modifier = Modifier.weight(1f),
                        )
                        TemperatureCard(
                            title = "Hành khách",
                            icon = Icons.Rounded.PersonOutline,
                            zone = uiState.passenger,
                            decreaseContentDescription = "Giảm nhiệt độ hành khách",
                            increaseContentDescription = "Tăng nhiệt độ hành khách",
                            onDecrease = onDecreasePassengerTemperature,
                            onIncrease = onIncreasePassengerTemperature,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                imageVector = Icons.Rounded.Air,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Column {
            Text(
                text = "Điều hòa nhiệt độ",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Điều chỉnh riêng cho từng vị trí",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemperatureCard(
    title: String,
    icon: ImageVector,
    zone: TemperatureZoneUiState,
    decreaseContentDescription: String,
    increaseContentDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(28.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = zone.value?.let(::formatTemperature) ?: "--",
                    fontSize = 56.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (zone.isAvailable) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Text(
                    text = "°C",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                FilledIconButton(
                    onClick = onDecrease,
                    enabled =
                        zone.isAvailable &&
                            zone.value != null &&
                            zone.value > zone.minimum,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = decreaseContentDescription,
                        modifier = Modifier.size(32.dp),
                    )
                }

                if (zone.isUpdating) {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                FilledIconButton(
                    onClick = onIncrease,
                    enabled =
                        zone.isAvailable &&
                            zone.value != null &&
                            zone.value < zone.maximum,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = increaseContentDescription,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Text(
                text =
                    "${formatTemperature(zone.minimum)} – " +
                        "${formatTemperature(zone.maximum)} °C",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "Đang đọc nhiệt độ từ xe…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry) {
                Text("Thử lại")
            }
        }
    }
}

@Composable
private fun InlineError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp),
    )
}

private fun formatTemperature(value: Float): String = String.format(Locale.getDefault(), "%.1f", value)

@Preview(
    showBackground = true,
    widthDp = 1024,
    heightDp = 600,
)
@Composable
private fun HvacTemperatureScreenPreview() {
    CarSettingTheme(dynamicColor = false) {
        HvacTemperatureScreen(
            uiState =
                HvacUiState(
                    driver = TemperatureZoneUiState(value = 21.5f, isAvailable = true),
                    passenger = TemperatureZoneUiState(value = 22f, isAvailable = true),
                    isLoading = false,
                ),
            onDecreaseDriverTemperature = {},
            onIncreaseDriverTemperature = {},
            onDecreasePassengerTemperature = {},
            onIncreasePassengerTemperature = {},
            onRetry = {},
        )
    }
}
