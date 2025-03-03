package com.lkubicki.foilrecord.ui.main

import androidx.lifecycle.viewModelScope
import com.lkubicki.foilrecord.data.repository.RunRepository
import com.lkubicki.foilrecord.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val runRepository: RunRepository
) : BaseViewModel<MainState, MainEvent>() {

    override fun initialState() = MainState(
        isRecording = false,
        currentLocation = null,
        currentSpeed = 0f,
        currentAcceleration = 0f,
        accuracy = 0f
    )

    fun startRecording() {
        viewModelScope.launch {
            val success = runRepository.startRecording()
            if (success) {
                updateState { it.copy(isRecording = true) }
                emitEvent(MainEvent.ShowMessage("Recording started"))
            } else {
                emitEvent(MainEvent.ShowError("Failed to start recording"))
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            val file = runRepository.stopRecording()
            if (file != null) {
                updateState { it.copy(isRecording = false) }
                emitEvent(MainEvent.ShowMessage("Recording stopped"))

                // Extract run ID from file name
                val runId = file.name.substringAfter("gps_data_").substringBefore(".csv")
                emitEvent(MainEvent.NavigateToRunDetails(runId))
            } else {
                emitEvent(MainEvent.ShowError("Failed to stop recording"))
            }
        }
    }

    fun updateLocationData(latitude: Double, longitude: Double, velocity: Float, acceleration: Float, accuracy: Float) {
        updateState {
            it.copy(
                currentLocation = LocationData(latitude, longitude),
                currentSpeed = velocity,
                currentAcceleration = acceleration,
                accuracy = accuracy
            )
        }
    }

    fun onViewRunsClicked() {
        emitEvent(MainEvent.NavigateToRunList)
    }
}

data class MainState(
    val isRecording: Boolean,
    val currentLocation: LocationData?,
    val currentSpeed: Float,
    val currentAcceleration: Float,
    val accuracy: Float
)

data class LocationData(
    val latitude: Double,
    val longitude: Double
)

sealed class MainEvent {
    data class ShowMessage(val message: String) : MainEvent()
    data class ShowError(val message: String) : MainEvent()
    data class NavigateToRunDetails(val runId: String) : MainEvent()
    object NavigateToRunList : MainEvent()
}