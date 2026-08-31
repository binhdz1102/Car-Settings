@file:Suppress("DEPRECATION")
@file:SuppressLint("MissingPermission", "InlinedApi", "NewApi")

package com.android.car.settings.feature.sound.data

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.car.Car
import android.car.CarOccupantZoneManager
import android.car.media.CarAudioManager
import android.car.media.CarVolumeGroupEventCallback
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.common.rethrowIfCancellation
import com.android.car.settings.core.vehicle.CarServiceProvider
import com.android.car.settings.feature.sound.domain.DoNotDisturbState
import com.android.car.settings.feature.sound.domain.InterruptionMode
import com.android.car.settings.feature.sound.domain.RingerMode
import com.android.car.settings.feature.sound.domain.RingtoneKind
import com.android.car.settings.feature.sound.domain.RingtoneOption
import com.android.car.settings.feature.sound.domain.RingtoneSelection
import com.android.car.settings.feature.sound.domain.SoundState
import com.android.car.settings.feature.sound.domain.SoundVolume
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Framework-backed implementation for the active Android user. On AAOS it uses CarAudioManager
 * volume groups, exactly like Car Settings, so routing policy remains the source of truth.
 */
@Singleton
internal class AndroidSoundPlatform
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val carServiceProvider: CarServiceProvider,
    ) : SoundPlatform {
        private val audioManager = context.getSystemService(AudioManager::class.java)
        private val notificationManager = context.getSystemService(NotificationManager::class.java)
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val mutableState = MutableStateFlow(SoundState())
        private val callbackExecutor = Executor { command -> scope.launch { command.run() } }

        @Volatile private var carAudioManager: CarAudioManager? = null

        @Volatile private var audioZoneId = CarAudioManager.PRIMARY_AUDIO_ZONE
        private var usesVolumeGroupEvents = false
        private var preview: Ringtone? = null

        override val state: StateFlow<SoundState> = mutableState.asStateFlow()

        private val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    scope.launch { refresh() }
                }
            }

        private val volumeGroupEventCallback =
            CarVolumeGroupEventCallback { events ->
                if (events.any { event ->
                        event.carVolumeGroupInfos.any { it.zoneId == audioZoneId }
                    }
                ) {
                    scope.launch { refresh() }
                }
            }

        private val legacyVolumeCallback =
            object : CarAudioManager.CarVolumeCallback() {
                override fun onGroupVolumeChanged(
                    zoneId: Int,
                    groupId: Int,
                    flags: Int,
                ) {
                    if (zoneId == audioZoneId) scope.launch { refresh() }
                }

                override fun onGroupMuteChanged(
                    zoneId: Int,
                    groupId: Int,
                    flags: Int,
                ) {
                    if (zoneId == audioZoneId) scope.launch { refresh() }
                }
            }

        init {
            val filter =
                IntentFilter().apply {
                    addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                    addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            carServiceProvider.register { connectedCar, ready ->
                onCarLifecycleChanged(connectedCar, ready)
            }
            scope.launch { refresh() }
        }

        override suspend fun refresh(): ActionResult =
            execute {
                val manager = requireNotNull(audioManager) { "Audio is not supported" }
                val resolver = context.contentResolver
                mutableState.value =
                    SoundState(
                        volumes = carVolumes().ifEmpty { audioManagerVolumes(manager) },
                        ringerMode = manager.ringerMode.toRingerMode(),
                        isRingerModeSupported = !manager.isVolumeFixed,
                        vibrateWhenRinging =
                            Settings.System.getInt(
                                resolver,
                                Settings.System.VIBRATE_WHEN_RINGING,
                                0,
                            ) != 0,
                        doNotDisturb =
                            DoNotDisturbState(
                                hasPolicyAccess = notificationManager?.isNotificationPolicyAccessGranted == true,
                                mode = notificationManager?.currentInterruptionFilter.toInterruptionMode(),
                            ),
                        ringtones = RingtoneKind.entries.map(::currentRingtone),
                    )
            }

        override suspend fun setVolume(
            groupId: Int,
            value: Int,
        ): ActionResult =
            execute {
                val carManager = carAudioManager
                if (carManager != null && groupId >= 0) {
                    val clamped =
                        value.coerceIn(
                            carManager.getGroupMinVolume(audioZoneId, groupId),
                            carManager.getGroupMaxVolume(audioZoneId, groupId),
                        )
                    carManager.setGroupVolume(audioZoneId, groupId, clamped, 0)
                } else {
                    val manager = requireNotNull(audioManager) { "Audio is not supported" }
                    val streamType = groupId.toFallbackStreamType()
                    val clamped =
                        value.coerceIn(manager.safeStreamMinVolume(streamType), manager.getStreamMaxVolume(streamType))
                    manager.setStreamVolume(streamType, clamped, AudioManager.FLAG_SHOW_UI)
                }
                refreshOrThrow()
            }

        override suspend fun setVolumeMuted(
            groupId: Int,
            muted: Boolean,
        ): ActionResult =
            execute {
                val carManager = carAudioManager
                if (carManager != null &&
                    groupId >= 0 &&
                    carManager.isAudioFeatureEnabled(CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING)
                ) {
                    carManager.setVolumeGroupMute(audioZoneId, groupId, muted, 0)
                } else {
                    val manager = requireNotNull(audioManager) { "Audio is not supported" }
                    val streamType = groupId.toFallbackStreamType()
                    manager.adjustStreamVolume(
                        streamType,
                        if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                        AudioManager.FLAG_SHOW_UI,
                    )
                }
                refreshOrThrow()
            }

        override suspend fun setRingerMode(mode: RingerMode): ActionResult =
            execute {
                val manager = requireNotNull(audioManager) { "Audio is not supported" }
                check(!manager.isVolumeFixed) {
                    "Ringer mode is not available on this fixed-volume Automotive device"
                }
                manager.ringerMode = mode.toPlatformMode()
                check(manager.ringerMode == mode.toPlatformMode()) {
                    "The system did not apply the requested ringer mode"
                }
                refreshOrThrow()
            }

        override suspend fun setVibrateWhenRinging(enabled: Boolean): ActionResult =
            execute {
                check(
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.VIBRATE_WHEN_RINGING,
                        if (enabled) 1 else 0,
                    ),
                ) { "The system did not allow changing call vibration" }
                refreshOrThrow()
            }

        override suspend fun setInterruptionMode(mode: InterruptionMode): ActionResult =
            execute {
                val manager = requireNotNull(notificationManager) { "Do Not Disturb is not supported" }
                check(manager.isNotificationPolicyAccessGranted) {
                    "Grant Do Not Disturb access before changing this setting"
                }
                manager.setInterruptionFilter(mode.toPlatformFilter())
                refreshOrThrow()
            }

        override suspend fun ringtoneOptions(kind: RingtoneKind): List<RingtoneOption> =
            withContext(dispatcher) {
                val manager = RingtoneManager(context).apply { setType(kind.toRingtoneType()) }
                val cursor = manager.cursor ?: return@withContext emptyList()
                try {
                    buildList {
                        if (cursor.moveToFirst()) {
                            do {
                                val position = cursor.position
                                val uri = manager.getRingtoneUri(position)?.toString() ?: continue
                                // Match Car Settings: names come from the media cursor. Creating a
                                // Ringtone here opens every media item and makes the picker appear
                                // to contain only "Silent" while that work completes.
                                val title =
                                    cursor.getString(
                                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE),
                                    ) ?: "Unknown sound"
                                add(RingtoneOption(title = title, uri = uri))
                            } while (cursor.moveToNext())
                        }
                    }
                } finally {
                    cursor.close()
                }
            }

        override suspend fun setRingtone(
            kind: RingtoneKind,
            uri: String?,
        ): ActionResult =
            execute {
                RingtoneManager.setActualDefaultRingtoneUri(
                    context,
                    kind.toRingtoneType(),
                    uri?.let(Uri::parse),
                )
                refreshOrThrow()
            }

        override suspend fun previewRingtone(uri: String?): ActionResult =
            execute {
                preview?.stop()
                preview = uri?.let { RingtoneManager.getRingtone(context, Uri.parse(it)) }
                preview?.play()
                scope.launch {
                    delay(PREVIEW_DURATION_MILLIS)
                    preview?.takeIf(Ringtone::isPlaying)?.stop()
                }
            }

        @Synchronized
        private fun onCarLifecycleChanged(
            connectedCar: Car,
            ready: Boolean,
        ) {
            unregisterCarVolumeCallback()
            if (!ready) {
                carAudioManager = null
                scope.launch { refresh() }
                return
            }
            val manager = connectedCar.getCarManager(Car.AUDIO_SERVICE) as? CarAudioManager
            carAudioManager = manager
            if (manager != null) {
                audioZoneId = connectedCar.findAudioZoneId(manager)
                usesVolumeGroupEvents =
                    manager.isAudioFeatureEnabled(CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_EVENTS)
                runCatching {
                    if (usesVolumeGroupEvents) {
                        manager.registerCarVolumeGroupEventCallback(callbackExecutor, volumeGroupEventCallback)
                    } else {
                        manager.registerCarVolumeCallback(legacyVolumeCallback)
                    }
                }.onFailure { throwable ->
                    Log.w(TAG, "Car audio callbacks unavailable; using safe refresh fallback", throwable)
                    usesVolumeGroupEvents = false
                    carAudioManager = null
                }
            }
            scope.launch { refresh() }
        }

        private fun Car.findAudioZoneId(manager: CarAudioManager): Int {
            val occupantZone =
                runCatching {
                    val occupantManager =
                        getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE) as? CarOccupantZoneManager
                    occupantManager
                        ?.getMyOccupantZone()
                        ?.let(occupantManager::getAudioZoneIdForOccupant)
                }.getOrNull()
            val uidZone = runCatching { manager.getZoneIdForUid(Process.myUid()) }.getOrNull()
            return occupantZone
                ?.takeIf { it != CarAudioManager.INVALID_AUDIO_ZONE }
                ?: uidZone
                    .takeIf { it != CarAudioManager.INVALID_AUDIO_ZONE }
                ?: CarAudioManager.PRIMARY_AUDIO_ZONE
        }

        private fun unregisterCarVolumeCallback() {
            val manager = carAudioManager ?: return
            runCatching {
                if (usesVolumeGroupEvents) {
                    manager.unregisterCarVolumeGroupEventCallback(volumeGroupEventCallback)
                } else {
                    manager.unregisterCarVolumeCallback(legacyVolumeCallback)
                }
            }
            usesVolumeGroupEvents = false
        }

        private fun carVolumes(): List<SoundVolume> =
            runCatching {
                val manager = carAudioManager ?: return@runCatching emptyList()
                (0 until manager.getVolumeGroupCount(audioZoneId)).map { groupId ->
                    val usages = manager.getUsagesForVolumeGroupId(audioZoneId, groupId)
                    SoundVolume(
                        id = groupId,
                        label = usages.volumeGroupLabel(),
                        current = manager.getGroupVolume(audioZoneId, groupId),
                        minimum = manager.getGroupMinVolume(audioZoneId, groupId),
                        maximum = manager.getGroupMaxVolume(audioZoneId, groupId),
                        isMuted =
                            manager.isAudioFeatureEnabled(
                                CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING,
                            ) &&
                                manager.isVolumeGroupMuted(audioZoneId, groupId),
                    )
                }
            }.getOrDefault(emptyList())

        private fun audioManagerVolumes(manager: AudioManager): List<SoundVolume> =
            FALLBACK_VOLUME_STREAMS.map { descriptor ->
                SoundVolume(
                    id = descriptor.streamType.toFallbackId(),
                    label = descriptor.label,
                    current = manager.getStreamVolume(descriptor.streamType),
                    minimum = manager.safeStreamMinVolume(descriptor.streamType),
                    maximum = manager.getStreamMaxVolume(descriptor.streamType),
                    isMuted = manager.isStreamMute(descriptor.streamType),
                )
            }

        private suspend fun execute(block: suspend () -> Unit): ActionResult =
            withContext(dispatcher) {
                try {
                    block()
                    ActionResult.Success
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    ActionResult.Failure(
                        message = throwable.message ?: throwable.javaClass.simpleName,
                        cause = throwable,
                    )
                }
            }

        private suspend fun refreshOrThrow() {
            val result = runCatching { refresh() }.getOrElse { throw it }
            check(result == ActionResult.Success) {
                (result as? ActionResult.Failure)?.message ?: "Unable to refresh sound state"
            }
        }

        private fun currentRingtone(kind: RingtoneKind): RingtoneSelection {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, kind.toRingtoneType())
            return RingtoneSelection(
                kind = kind,
                title = uri?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) } ?: "Silent",
                uri = uri?.toString(),
            )
        }
    }

private const val PREVIEW_DURATION_MILLIS = 1_500L
private const val TAG = "AndroidSoundPlatform"

private data class FallbackVolumeStream(
    val streamType: Int,
    val label: String,
)

private val FALLBACK_VOLUME_STREAMS =
    listOf(
        FallbackVolumeStream(AudioManager.STREAM_MUSIC, "Media"),
        FallbackVolumeStream(AudioManager.STREAM_RING, "Calls"),
        FallbackVolumeStream(AudioManager.STREAM_NOTIFICATION, "Notifications"),
        FallbackVolumeStream(AudioManager.STREAM_ALARM, "Alarm"),
        FallbackVolumeStream(AudioManager.STREAM_SYSTEM, "System sounds"),
    )

private fun Int.toFallbackId(): Int = -this - 1

private fun Int.toFallbackStreamType(): Int {
    check(this < 0) { "AAOS volume group IDs must be handled by CarAudioManager" }
    return -this - 1
}

private fun AudioManager.safeStreamMinVolume(streamType: Int): Int =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        getStreamMinVolume(streamType)
    } else {
        0
    }

private fun IntArray.volumeGroupLabel(): String =
    when {
        any { it == AudioAttributes.USAGE_VOICE_COMMUNICATION } -> "Calls"
        any { it == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE } -> "Navigation guidance"
        any { it == AudioAttributes.USAGE_MEDIA || it == AudioAttributes.USAGE_GAME } -> "Media"
        any { it == AudioAttributes.USAGE_ALARM } -> "Alarm & system sounds"
        any { it == AudioAttributes.USAGE_NOTIFICATION_RINGTONE } -> "Ringtone & notifications"
        any { it == AudioAttributes.USAGE_NOTIFICATION } -> "Notifications"
        any { it == AudioAttributes.USAGE_ASSISTANT } -> "Assistant"
        else -> "Vehicle sound"
    }

private fun Int.toRingerMode(): RingerMode =
    when (this) {
        AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
        AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
        else -> RingerMode.NORMAL
    }

private fun RingerMode.toPlatformMode(): Int =
    when (this) {
        RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
        RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
        RingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
    }

private fun RingtoneKind.toRingtoneType(): Int =
    when (this) {
        RingtoneKind.PHONE -> RingtoneManager.TYPE_RINGTONE
        RingtoneKind.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
        RingtoneKind.ALARM -> RingtoneManager.TYPE_ALARM
    }

private fun Int?.toInterruptionMode(): InterruptionMode =
    when (this) {
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> InterruptionMode.PRIORITY
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> InterruptionMode.ALARMS
        NotificationManager.INTERRUPTION_FILTER_NONE -> InterruptionMode.NONE
        else -> InterruptionMode.ALL
    }

private fun InterruptionMode.toPlatformFilter(): Int =
    when (this) {
        InterruptionMode.ALL -> NotificationManager.INTERRUPTION_FILTER_ALL
        InterruptionMode.PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
        InterruptionMode.ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
        InterruptionMode.NONE -> NotificationManager.INTERRUPTION_FILTER_NONE
    }
