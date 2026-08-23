package com.example.scratchscan.ui.catalog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.scratchscan.data.ScratchOffGame
import com.example.scratchscan.data.local.ScratchOffDatabase
import com.example.scratchscan.data.repository.ScratchOffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AllGamesUiState(
    val games: List<ScratchOffGame> = emptyList(),
    val isLoading: Boolean = false
)

class AllGamesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ScratchOffDatabase.getDatabase(application)
    private val repository = ScratchOffRepository(application, database.gameDao())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showOnlyFavorites = MutableStateFlow(false)
    val showOnlyFavorites: StateFlow<Boolean> = _showOnlyFavorites.asStateFlow()

    val uiState: StateFlow<AllGamesUiState> = combine(
        repository.allGames,
        _searchQuery,
        _showOnlyFavorites
    ) { games, query, onlyFavorites ->
        var filtered = games
        if (onlyFavorites) {
            filtered = filtered.filter { it.isFavorite }
        }
        if (query.isNotBlank()) {
            filtered = filtered.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.topPrize?.contains(query, ignoreCase = true) == true ||
                "$${it.price}" == query ||
                it.price.toString() == query
            }
        }
        AllGamesUiState(games = filtered, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AllGamesUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            repository.loadGamesFromAssets()
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoriteFilter() {
        _showOnlyFavorites.value = !_showOnlyFavorites.value
    }

    fun toggleFavorite(game: ScratchOffGame) {
        viewModelScope.launch {
            repository.toggleFavorite(game.gameNumber, !game.isFavorite)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AllGamesViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AllGamesViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
