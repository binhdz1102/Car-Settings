package com.android.car.settings.feature.privacy.data

import android.content.Context
import android.content.pm.PackageManager
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.feature.privacy.domain.PrivacyAccessApp
import com.android.car.settings.feature.privacy.domain.PrivacyPermissionApp
import com.android.car.settings.feature.privacy.domain.PrivacyPermissionType
import com.android.car.settings.feature.privacy.domain.PrivacySensorState
import com.android.car.settings.feature.privacy.domain.PrivacyState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Implements the microphone, camera, location and per-app permission pages from AAOS Privacy. */
@Singleton
internal class AndroidPrivacyPlatform @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : PrivacyPlatform {
    private val packageManager = context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(PrivacyState())

    override val state: StateFlow<PrivacyState> = mutableState.asStateFlow()

    init {
        // MANAGE/OBSERVE_SENSOR_PRIVACY are internal role permissions. A platform-signed app
        // which is not installed as the AAOS Settings role cannot query or observe the state.
        // Do not make the complete Privacy page fail in that valid deployment configuration.
        if (canObserveSensorPrivacy()) {
            runCatching {
                PrivacyHiddenApiBridge.registerSensorChangedListener(
                    context,
                    java.util.concurrent.Executor { command -> scope.launch { command.run() } },
                ) { scope.launch { refresh() } }
            }
        }
        scope.launch { refresh() }
    }

    override suspend fun refresh(): ActionResult = execute {
        val selectedType = mutableState.value.selectedPermissionType
        mutableState.value = PrivacyState(
            microphone = sensorState(PrivacyHiddenApiBridge.microphoneSensor()),
            camera = sensorState(PrivacyHiddenApiBridge.cameraSensor()),
            locationEnabled = PrivacyHiddenApiBridge.isLocationEnabled(context),
            recentMicrophoneAccess = loadRecentAccesses(PrivacyHiddenApiBridge.microphoneOps()),
            recentCameraAccess = loadRecentAccesses(PrivacyHiddenApiBridge.cameraOps()),
            recentLocationAccess = loadRecentAccesses(PrivacyHiddenApiBridge.locationOps()),
            selectedPermissionType = selectedType,
            permissionApps = selectedType?.let(::loadPermissionApps).orEmpty(),
        )
    }

    override suspend fun setMicrophoneAccessEnabled(enabled: Boolean): ActionResult =
        setSensorAccessEnabled(PrivacyHiddenApiBridge.microphoneSensor(), enabled)

    override suspend fun setCameraAccessEnabled(enabled: Boolean): ActionResult =
        setSensorAccessEnabled(PrivacyHiddenApiBridge.cameraSensor(), enabled)

    override suspend fun setLocationEnabled(enabled: Boolean): ActionResult = execute {
        PrivacyHiddenApiBridge.setLocationEnabled(context, enabled)
        refreshOrThrow()
    }

    override suspend fun selectPermissionType(type: PrivacyPermissionType): ActionResult = execute {
        mutableState.value = mutableState.value.copy(
            selectedPermissionType = type,
            permissionApps = loadPermissionApps(type),
        )
    }

    override suspend fun setAppPermission(
        packageName: String,
        type: PrivacyPermissionType,
        granted: Boolean,
    ): ActionResult = execute {
        PrivacyHiddenApiBridge.setRuntimePermission(context, packageName, type.permission, granted)
        mutableState.value = mutableState.value.copy(permissionApps = loadPermissionApps(type))
    }

    private suspend fun setSensorAccessEnabled(sensor: Int, enabled: Boolean): ActionResult = execute {
        check(canObserveSensorPrivacy()) { SENSOR_PRIVACY_ROLE_REQUIRED_MESSAGE }
        check(PrivacyHiddenApiBridge.isSensorToggleSupported(context, sensor)) { "This sensor privacy toggle is not supported" }
        // SensorPrivacy API stores "blocked" while Settings labels the switch as "access enabled".
        PrivacyHiddenApiBridge.setSensorAccessEnabled(context, sensor, enabled)
        refreshOrThrow()
    }

    private fun sensorState(sensor: Int): PrivacySensorState {
        if (!canObserveSensorPrivacy()) {
            return PrivacySensorState(unavailableReason = SENSOR_PRIVACY_ROLE_REQUIRED_MESSAGE)
        }
        return runCatching {
            PrivacySensorState(
                supported = PrivacyHiddenApiBridge.isSensorToggleSupported(context, sensor),
                accessEnabled = PrivacyHiddenApiBridge.isSensorAccessEnabled(context, sensor),
            )
        }.getOrElse { throwable ->
            PrivacySensorState(
                unavailableReason = throwable.message ?: SENSOR_PRIVACY_ROLE_REQUIRED_MESSAGE,
            )
        }
    }

    private fun canObserveSensorPrivacy(): Boolean =
        context.checkSelfPermission(PERMISSION_OBSERVE_SENSOR_PRIVACY) == PackageManager.PERMISSION_GRANTED

    private fun loadRecentAccesses(operations: IntArray): List<PrivacyAccessApp> =
        runCatching {
            PrivacyHiddenApiBridge.getRecentAccesses(context, operations)
                .mapNotNull { access ->
                    val appInfo = runCatching { packageManager.getApplicationInfo(access.packageName, 0) }.getOrNull()
                        ?: return@mapNotNull null
                    PrivacyAccessApp(
                        packageName = access.packageName,
                        label = appInfo.loadLabel(packageManager).toString(),
                        lastAccessMillis = access.lastAccessMillis,
                    )
                }
                .sortedByDescending(PrivacyAccessApp::lastAccessMillis)
        }.getOrDefault(emptyList())

    private fun loadPermissionApps(type: PrivacyPermissionType): List<PrivacyPermissionApp> =
        packageManager
            .getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS)
            .asSequence()
            .filter { type.permission in it.requestedPermissions.orEmpty() }
            .mapNotNull { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                PrivacyPermissionApp(
                    packageName = packageInfo.packageName,
                    label = appInfo.loadLabel(packageManager).toString(),
                    granted = packageManager.checkPermission(type.permission, packageInfo.packageName) == PackageManager.PERMISSION_GRANTED,
                    isEnabled = appInfo.enabled,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()

    private suspend fun execute(block: suspend () -> Unit): ActionResult = withContext(dispatcher) {
        try {
            block()
            ActionResult.Success
        } catch (throwable: Throwable) {
            val message = throwable.cause?.message ?: throwable.message ?: "Unknown privacy error"
            mutableState.value = mutableState.value.copy(lastError = message)
            ActionResult.Failure(message, throwable)
        }
    }

    private suspend fun refreshOrThrow() {
        check(refresh() == ActionResult.Success) { "Unable to refresh privacy settings" }
    }
}

private const val PERMISSION_OBSERVE_SENSOR_PRIVACY = "android.permission.OBSERVE_SENSOR_PRIVACY"
private const val SENSOR_PRIVACY_ROLE_REQUIRED_MESSAGE =
    "Sensor privacy is available only when My System App is installed with the AAOS Settings role"
