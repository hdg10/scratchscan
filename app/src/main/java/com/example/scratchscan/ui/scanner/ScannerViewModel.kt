package com.example.scratchscan.ui.scanner

import android.app.Application
import android.graphics.Point
import android.graphics.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.scratchscan.data.ScratchOffGame
import com.example.scratchscan.data.local.ScratchOffDatabase
import com.example.scratchscan.data.repository.ScratchOffRepository
import com.example.scratchscan.telemetry.TelemetryManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

sealed class ScannerState {
    object Searching : ScannerState()
    data class Detected(val boundingBox: Rect, val corners: List<Point>? = null) : ScannerState()
    data class Confirming(val game: ScratchOffGame, val boundingBox: Rect, val corners: List<Point>? = null) : ScannerState()
    data class Locked(val game: ScratchOffGame, val boundingBox: Rect, val corners: List<Point>? = null) : ScannerState()
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val STABILIZATION_THRESHOLD = 2
        private const val DETECTION_TIMEOUT_MS = 1500L
        private const val AUTO_RESET_FAILURES_THRESHOLD = 15
    }

    private val database = ScratchOffDatabase.getDatabase(application)
    private val repository = ScratchOffRepository(application, database.gameDao())

    private val _uiState = MutableStateFlow<ScannerState>(ScannerState.Searching)
    val uiState: StateFlow<ScannerState> = _uiState.asStateFlow()

    private val _diagnostics = MutableStateFlow("Ready to scan")
    val diagnostics: StateFlow<String> = _diagnostics.asStateFlow()

    private val _imageDimensions = MutableStateFlow<Pair<Int, Int>?>(null)
    val imageDimensions: StateFlow<Pair<Int, Int>?> = _imageDimensions.asStateFlow()

    private var detectionCount = 0
    private var failureCount = 0
    private var lastIdentifiedGameId: Int? = null
    private var detectionExpiryJob: Job? = null

    fun onObjectDetected(boundingBox: Rect, corners: List<Point>? = null) {
        val currentState = _uiState.value
        if ((currentState is ScannerState.Searching) || (currentState is ScannerState.Detected)) {
            _uiState.value = ScannerState.Detected(boundingBox, corners)
            resetDetectionExpiry()
        }
    }

    fun onGameIdentified(gameName: String?, boundingBox: Rect, corners: List<Point>?) {
        viewModelScope.launch {
            val games = repository.allGames.first()
            val cleanCameraText = gameName?.lowercase() ?: ""
            
            val matchedGame = games.asSequence().filter { game ->
                // Split DB name into words, stripping symbols (e.g., "Ravens X2!" -> ["ravens", "x2"])
                val dbWords = game.name.lowercase()
                    .replace(Regex("[^a-z0-9 ]"), "")
                    .split(" ")
                    .filter { it.isNotBlank() }
                
                dbWords.isNotEmpty() && dbWords.all { word -> cleanCameraText.contains(word) }
            }.maxByOrNull { it.name.length } // Prioritize longer specific names (e.g. "Diamond Bingo" beats "Bingo")

            if (matchedGame != null) {
                handleMatchedGame(matchedGame, boundingBox, corners)
            } else {
                handleIdentificationFailure()
            }
        }
    }

    private fun handleMatchedGame(game: ScratchOffGame, boundingBox: Rect, corners: List<Point>?) {
        if (game.gameNumber == lastIdentifiedGameId) {
            detectionCount++
        } else {
            lastIdentifiedGameId = game.gameNumber
            detectionCount = 1
        }

        if (detectionCount >= STABILIZATION_THRESHOLD) {
            if ((_uiState.value !is ScannerState.Confirming) && (_uiState.value !is ScannerState.Locked)) {
                _uiState.value = ScannerState.Confirming(game, boundingBox, corners)
                updateDiagnostics("Match found: ${game.name}. Tap to confirm.")
                TelemetryManager.logScanSuccess(game.gameNumber, 0, -1)
            }
            failureCount = 0
            resetDetectionExpiry()
        }
    }

    private fun handleIdentificationFailure() {
        failureCount++
        if (failureCount >= AUTO_RESET_FAILURES_THRESHOLD) {
            resetScanner()
            updateDiagnostics("Resetting scanner due to low confidence...")
        }
    }

    fun confirmGame(game: ScratchOffGame) {
        val currentState = _uiState.value
        if (currentState is ScannerState.Confirming) {
            _uiState.value = ScannerState.Locked(game, currentState.boundingBox, currentState.corners)
            updateDiagnostics("Confirmed: ${game.name}")
        }
    }

    private fun resetDetectionExpiry() {
        detectionExpiryJob?.cancel()
        detectionExpiryJob = viewModelScope.launch {
            delay(DETECTION_TIMEOUT_MS.milliseconds)
            if ((_uiState.value !is ScannerState.Locked) && (_uiState.value !is ScannerState.Confirming)) {
                clearOutlines()
            }
        }
    }

    fun clearOutlines() {
        if (_uiState.value !is ScannerState.Searching) {
            _uiState.value = ScannerState.Searching
            updateDiagnostics("Searching for Maryland scratch-offs...")
        }
    }

    fun setImageDimensions(width: Int, height: Int) {
        _imageDimensions.value = width to height
    }

    fun updateDiagnostics(message: String) {
        _diagnostics.value = message
    }

    fun resetScanner() {
        TelemetryManager.logScanFailure("USER_RESET")
        detectionCount = 0
        failureCount = 0
        lastIdentifiedGameId = null
        _uiState.value = ScannerState.Searching
        _diagnostics.value = "Ready to scan"
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ScannerViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
