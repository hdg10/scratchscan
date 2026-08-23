package com.example.scratchscan.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.scratchscan.data.ChatMessage
import com.example.scratchscan.data.local.ScratchOffDatabase
import com.example.scratchscan.data.repository.GeminiRepository
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val currentStreamingText: String = "",
    val error: String? = null,
)

class ChatViewModel(
    application: Application,
    private val sessionId: String
) : AndroidViewModel(application) {
    
    private val database = ScratchOffDatabase.getDatabase(application)
    // Using the Firebase Vertex AI extension to get the model
    private val generativeModel = Firebase.vertexAI.generativeModel(
        modelName = "gemini-1.5-flash"
    )
    private val repository = GeminiRepository(database.chatDao(), generativeModel)
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var generationJob: Job? = null
    
    val messages: StateFlow<List<ChatMessage>> = repository.getChatMessages(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isGenerating) return
        
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, currentStreamingText = "", error = null) }
            
            try {
                repository.generateResponse(sessionId, text).collect { chunk ->
                    _uiState.update { it.copy(currentStreamingText = it.currentStreamingText + chunk) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage) }
            } finally {
                _uiState.update { it.copy(isGenerating = false, currentStreamingText = "") }
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false) }
    }

    override fun onCleared() {
        super.onCleared()
        stopGeneration() // Ensure cleanup on navigation or closing
    }

    class Factory(
        private val application: Application,
        private val sessionId: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, sessionId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
