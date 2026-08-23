package com.example.scratchscan.data.repository

import com.example.scratchscan.data.ChatMessage
import com.example.scratchscan.data.ChatSession
import com.example.scratchscan.data.local.ChatDao
import com.example.scratchscan.data.remote.ConversationManager
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.content
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import kotlin.math.pow

class GeminiRepository(
    private val chatDao: ChatDao,
    private val generativeModel: GenerativeModel,
    private val conversationManager: ConversationManager = ConversationManager()
) {
    fun getChatMessages(sessionId: String): Flow<List<ChatMessage>> =
        chatDao.getMessagesForSession(sessionId)

    fun generateResponse(sessionId: String, userMessage: String): Flow<String> = flow {
        // 1. Save user message
        val userMsg = ChatMessage(sessionId = sessionId, role = "USER", content = userMessage)
        chatDao.insertMessage(userMsg)
        
        // 2. Prepare context using sliding window
        // In a real app, you'd convert history to SDK Content types
        
        // 3. Stream response with retry logic
        var fullResponse = ""
        generativeModel.generateContentStream(userMessage)
            .retryWhen { cause, attempt ->
                if (attempt < 3 && isTransientError(cause)) {
                    val delayTime = 2.0.pow(attempt.toDouble()).toLong() * 1000
                    delay(delayTime)
                    true
                } else {
                    false
                }
            }
            .collect { response ->
                val chunk = response.text ?: ""
                fullResponse += chunk
                emit(chunk)
            }
            
        // 4. Save model response
        val modelMsg = ChatMessage(sessionId = sessionId, role = "MODEL", content = fullResponse)
        chatDao.insertMessage(modelMsg)
        
        // 5. Update session timestamp
        chatDao.getSessionById(sessionId)?.let {
            chatDao.updateSession(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    private fun isTransientError(throwable: Throwable): Boolean {
        // Checking for common transient errors or rate limits
        val msg = throwable.message ?: ""
        return throwable is IOException || msg.contains("500") || msg.contains("503") || msg.contains("429")
    }
    
    suspend fun createNewSession(title: String): String {
        val session = ChatSession(title = title)
        chatDao.insertSession(session)
        return session.id
    }
}
