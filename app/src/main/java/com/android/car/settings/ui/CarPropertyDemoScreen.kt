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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.car.settings.ui.theme.CarSettingTheme
import com.example.myapplication.core.realcar.RealCarConnectionState
import com.example.myapplication.core.realcar.RealVehicleGear
import java.util.Locale

@Composable
internal fun CarPropertyDemoScreen(
    uiState: CarPropertyDemoUiState,
    onDecreaseDriverTemperature: () -> Unit,
    onIncreaseDriverTemperature: () -> Unit,
    onDecreasePassengerTemperature: () -> Unit,
    onIncreasePassengerTemperature: () -> Unit,
    onDecreaseFanSpeed: () -> Unit,
    onIncreaseFanSpeed: () -> Unit,
    onToggleAc: () -> Unit,
    onRefresh: () -> Unit,
    onRunLargeBatchRead: () -> Unit,
    onRunConfirmedBatchWrite: () -> Unit,
    onRunErrorScenarios: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Điều khiển", "Kiểu dữ liệu", "Batch & lỗi")

    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .safeDrawingPadding()
                    .padding(contentPadding),
        ) {
            DemoHeader(
                connectionState = uiState.connectionState,
                isLoading = uiState.isLoading,
                onRefresh = onRefresh,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 18.dp),
            )

            uiState.globalError?.let { message ->
                ErrorBanner(
                    message = message,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }

            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 40.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 ->
                    ControlsTab(
                        controls = uiState.controls,
                        onDecreaseDriverTemperature = onDecreaseDriverTemperature,
                        onIncreaseDriverTemperature = onIncreaseDriverTemperature,
                        onDecreasePassengerTemperature = onDecreasePassengerTemperature,
                        onIncreasePassengerTemperature = onIncreasePassengerTemperature,
                        onDecreaseFanSpeed = onDecreaseFanSpeed,
                        onIncreaseFanSpeed = onIncreaseFanSpeed,
                        onToggleAc = onToggleAc,
                    )

                1 -> PropertyTypesTab(uiState.samples)
                else ->
                    BatchAndErrorsTab(
                        uiState = uiState,
                        onRunLargeBatchRead = onRunLargeBatchRead,
                        onRunConfirmedBatchWrite = onRunConfirmedBatchWrite,
                        onRunErrorScenarios = onRunErrorScenarios,
                    )
            }
        }
    }
}

@Composable
private fun DemoHeader(
    connectionState: RealCarConnectionState,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(52.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(16.dp),
                    ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "RealCarPropertyManager Lab",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Typed • Flow • Async batch • Error handling",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ConnectionBadge(connectionState)
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = "Đọc lại tất cả property")
            }
        }
    }
}

@Composable
private fun ConnectionBadge(state: RealCarConnectionState) {
    val connected = state == RealCarConnectionState.CONNECTED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .background(
                    if (connected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    RoundedCornerShape(50),
                ).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = if (connected) Icons.Rounded.CheckCircle else Icons.Rounded.CloudSync,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = state.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ControlsTab(
    controls: CarControlsUiState,
    onDecreaseDriverTemperature: () -> Unit,
    onIncreaseDriverTemperature: () -> Unit,
    onDecreasePassengerTemperature: () -> Unit,
    onIncreasePassengerTemperature: () -> Unit,
    onDecreaseFanSpeed: () -> Unit,
    onIncreaseFanSpeed: () -> Unit,
    onToggleAc: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(40.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TemperatureControlCard(
                    title = "Tài xế • area 1",
                    icon = Icons.Rounded.Person,
                    zone = controls.driverTemperature,
                    onDecrease = onDecreaseDriverTemperature,
                    onIncrease = onIncreaseDriverTemperature,
                    modifier = Modifier.weight(1f),
                )
                TemperatureControlCard(
                    title = "Hành khách • area 4",
                    icon = Icons.Rounded.PersonOutline,
                    zone = controls.passengerTemperature,
                    onDecrease = onDecreasePassengerTemperature,
                    onIncrease = onIncreasePassengerTemperature,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StepperCard(
                    title = "HVAC_FAN_SPEED • Int",
                    value = controls.fanSpeed?.toString() ?: "--",
                    onDecrease = onDecreaseFanSpeed,
                    onIncrease = onIncreaseFanSpeed,
                    decreaseEnabled =
                        controls.fanSpeed?.let { it > controls.fanMinimum } == true,
                    increaseEnabled =
                        controls.fanSpeed?.let { it < controls.fanMaximum } == true,
                    modifier = Modifier.weight(1f),
                )
                Card(
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("HVAC_AC_ON • Boolean", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (controls.acEnabled == true) "Đang bật / ON" else "Đang tắt / OFF",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = controls.acEnabled == true,
                            onCheckedChange = { onToggleAc() },
                            enabled = controls.acEnabled != null,
                        )
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                ) {
                    LiveMetric(
                        label = "PERF_VEHICLE_SPEED • Flow",
                        value =
                            controls.speedMetersPerSecond
                                ?.let { String.format(Locale.US, "%.1f m/s", it) }
                                ?: "--",
                    )
                    LiveMetric(
                        label = "GEAR_SELECTION • Int",
                        value = controls.gear?.let(RealVehicleGear::shortNameOf) ?: "--",
                    )
                }
            }
        }
    }
}

@Composable
private fun TemperatureControlCard(
    title: String,
    icon: ImageVector,
    zone: TemperatureZoneUiState,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(22.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = zone.value?.let { String.format(Locale.US, "%.1f °C", it) } ?: "--",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                FilledIconButton(
                    onClick = onDecrease,
                    enabled = zone.value?.let { it > zone.minimum } == true,
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Giảm $title")
                }
                FilledIconButton(
                    onClick = onIncrease,
                    enabled = zone.value?.let { it < zone.maximum } == true,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Tăng $title")
                }
            }
            Text(
                text = "${zone.minimum}–${zone.maximum} °C • step ${zone.step}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepperCard(
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseEnabled: Boolean,
    increaseEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            FilledIconButton(onClick = onDecrease, enabled = decreaseEnabled) {
                Icon(Icons.Rounded.Remove, contentDescription = "Giảm tốc độ quạt")
            }
            FilledIconButton(onClick = onIncrease, enabled = increaseEnabled) {
                Icon(Icons.Rounded.Add, contentDescription = "Tăng tốc độ quạt")
            }
        }
    }
}

@Composable
private fun LiveMetric(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PropertyTypesTab(samples: List<PropertySampleUiState>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(40.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = "Scalar, vector và mixed types từ VHAL thật",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Unavailable cũng là kết quả hợp lệ và được biểu diễn bằng typed error.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        items(samples) { sample ->
            PropertySampleCard(sample)
        }
    }
}

@Composable
private fun PropertySampleCard(sample: PropertySampleUiState) {
    Card(
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(18.dp),
        ) {
            Icon(
                imageVector =
                    if (sample.status == SampleStatus.AVAILABLE) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.ErrorOutline
                    },
                contentDescription = null,
                tint =
                    if (sample.status == SampleStatus.AVAILABLE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(sample.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        sample.type,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    sample.propertyName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = sample.value,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1.2f),
            )
        }
    }
}

@Composable
private fun BatchAndErrorsTab(
    uiState: CarPropertyDemoUiState,
    onRunLargeBatchRead: () -> Unit,
    onRunConfirmedBatchWrite: () -> Unit,
    onRunErrorScenarios: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(40.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            ActionCard(
                title = "Batch read hiệu năng cao",
                description = "240 requests • 60/chunk • tối đa 4 chunks đồng thời",
                result = uiState.batchReadSummary,
                buttonText = "Chạy batch read",
                onClick = onRunLargeBatchRead,
            )
        }
        item {
            ActionCard(
                title = "Confirmed batch write",
                description = "Ghi lại temperature, fan và A/C; chờ từng xác nhận từ VHAL",
                result = uiState.batchWriteSummary,
                buttonText = "Ghi 4 properties",
                onClick = onRunConfirmedBatchWrite,
            )
        }
        item {
            ActionCard(
                title = "Error matrix",
                description = "Unsupported property, invalid area/type/value và write read-only",
                result = uiState.errorScenarioSummary,
                buttonText = "Chạy 5 lỗi an toàn",
                onClick = onRunErrorScenarios,
            )
        }
        item {
            Text(
                "Subscription event log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(uiState.eventLog) { event ->
            Text(
                text = event,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(10.dp),
                        ).padding(12.dp),
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    result: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(result, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Button(onClick = onClick) {
                    Text(buttonText)
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                .padding(12.dp),
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Preview(
    showBackground = true,
    widthDp = 1024,
    heightDp = 700,
)
@Composable
private fun CarPropertyDemoScreenPreview() {
    CarSettingTheme(dynamicColor = false) {
        CarPropertyDemoScreen(
            uiState =
                CarPropertyDemoUiState(
                    connectionState = RealCarConnectionState.CONNECTED,
                    controls =
                        CarControlsUiState(
                            driverTemperature = TemperatureZoneUiState(value = 21.5f),
                            passengerTemperature = TemperatureZoneUiState(value = 22f),
                            fanSpeed = 3,
                            acEnabled = true,
                            speedMetersPerSecond = 0f,
                            gear = 4,
                        ),
                    isLoading = false,
                ),
            onDecreaseDriverTemperature = {},
            onIncreaseDriverTemperature = {},
            onDecreasePassengerTemperature = {},
            onIncreasePassengerTemperature = {},
            onDecreaseFanSpeed = {},
            onIncreaseFanSpeed = {},
            onToggleAc = {},
            onRefresh = {},
            onRunLargeBatchRead = {},
            onRunConfirmedBatchWrite = {},
            onRunErrorScenarios = {},
        )
    }
}
