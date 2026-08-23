package com.android.car.settings.feature.sound.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.ActiveOperationTracker
import com.android.car.settings.core.common.rethrowIfCancellation
import com.android.car.settings.feature.sound.domain.InterruptionMode
import com.android.car.settings.feature.sound.domain.RingerMode
import com.android.car.settings.feature.sound.domain.RingtoneKind
import com.android.car.settings.feature.sound.domain.RingtoneOption
import com.android.car.settings.feature.sound.domain.SoundState
import com.android.car.settings.feature.sound.domain.SoundUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SoundUiState(
    val sound: SoundState = SoundState(),
    val ringtoneOptions: Map<RingtoneKind, List<RingtoneOption>> = emptyMap(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SoundViewModel
    @Inject
    constructor(
        private val useCases: SoundUseCases,
    ) : ViewModel() {
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)
        private val options = MutableStateFlow<Map<RingtoneKind, List<RingtoneOption>>>(emptyMap())

        val uiState: StateFlow<SoundUiState> =
            combine(useCases.observeState(), options, working.isActive, message, ::SoundUiState)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = SoundUiState(),
                )

        init {
            execute { useCases.refresh() }
        }

        fun refresh() = execute(showProgress = false) { useCases.refresh() }

        fun setVolume(
            groupId: Int,
            value: Int,
        ) = execute(showProgress = false) {
            useCases.setVolume(groupId, value)
        }

        fun setRingerMode(mode: RingerMode) = execute { useCases.setRingerMode(mode) }

        fun setVibrateWhenRinging(enabled: Boolean) =
            execute {
                useCases.setVibrateWhenRinging(enabled)
            }

        fun setInterruptionMode(mode: InterruptionMode) =
            execute {
                useCases.setInterruptionMode(mode)
            }

        fun loadRingtoneOptions(kind: RingtoneKind) {
            if (options.value.containsKey(kind)) return
            viewModelScope.launch {
                working.track {
                    try {
                        val result = useCases.ringtoneOptions(kind)
                        options.value = options.value + (kind to result)
                    } catch (throwable: Throwable) {
                        throwable.rethrowIfCancellation()
                        message.value = throwable.message ?: "Unable to load ringtone options"
                    }
                }
            }
        }

        /** Warm the media cursor cache before a picker is opened. */
        fun preloadRingtoneOptions() {
            RingtoneKind.entries.forEach(::loadRingtoneOptions)
        }

        fun previewRingtone(uri: String?) = execute(showProgress = false) { useCases.previewRingtone(uri) }

        fun setRingtone(
            kind: RingtoneKind,
            uri: String?,
        ) = execute {
            useCases.setRingtone(kind, uri)
        }

        fun clearMessage() {
            message.value = null
        }

        private fun execute(
            showProgress: Boolean = true,
            block: suspend () -> ActionResult,
        ) {
            viewModelScope.launch {
                working.track(showProgress) {
                    when (val result = block()) {
                        ActionResult.Success -> Unit
                        is ActionResult.Failure -> message.value = result.message
                    }
                }
            }
        }
    }
