package com.example.scratchscan.ui.compare

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.scratchscan.data.CalculatedStats
import com.example.scratchscan.data.local.ScratchOffDatabase
import com.example.scratchscan.data.remote.MarylandLotteryDataSource
import com.example.scratchscan.data.repository.ScratchOffRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CompareViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ScratchOffDatabase.getDatabase(application)
    private val repository = ScratchOffRepository(
        application,
        database.gameDao()
    )

    val rankedGames: StateFlow<List<CalculatedStats>> = repository.allGames
        .map { games ->
            games.map { game ->
                CalculatedStats(
                    gameNumber = game.gameNumber,
                    topPrizeAmount = 0L,
                    topPrizesRemaining = 0,
                    topPrizeRemainingRatio = 0.0,
                    prizeToTicketRatio = 0.0,
                    estimatedTicketsRemaining = 0,
                    score = (game.price * 1.5)
                )
            }.sortedByDescending { it.score }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CompareViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CompareViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
