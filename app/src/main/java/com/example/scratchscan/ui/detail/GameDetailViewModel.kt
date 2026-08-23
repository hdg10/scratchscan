package com.example.scratchscan.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.scratchscan.data.GameWithPrizes
import com.example.scratchscan.data.local.ScratchOffDatabase
import com.example.scratchscan.data.remote.MarylandLotteryDataSource
import com.example.scratchscan.data.repository.ScratchOffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val gameWithPrizes: GameWithPrizes) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

class GameDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ScratchOffDatabase.getDatabase(application)
    private val repository = ScratchOffRepository(application, database.gameDao())

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadGameDetails(gameNumber: Int) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            repository.getGameWithPrizes(gameNumber)
                .catch { e ->
                    _uiState.value = DetailUiState.Error(e.localizedMessage ?: "Unknown Error")
                }
                .collect { result ->
                    if (result != null) {
                        _uiState.value = DetailUiState.Success(result)
                    } else {
                        _uiState.value = DetailUiState.Error("Game details not found")
                    }
                }
        }
    }

    fun toggleFavorite(gameWithPrizes: GameWithPrizes) {
        viewModelScope.launch {
            repository.toggleFavorite(gameWithPrizes.game.gameNumber, !gameWithPrizes.game.isFavorite)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GameDetailViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return GameDetailViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
