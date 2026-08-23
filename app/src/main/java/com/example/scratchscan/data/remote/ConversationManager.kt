package com.example.scratchscan.data.remote

import com.example.scratchscan.data.ChatMessage
import com.example.scratchscan.data.ChatSession

class ConversationManager(
    private val maxHistoryTurns: Int = 10,
    private val maxTokens: Int = 4000
) {
    val systemInstruction = """
        You are the Scratch Scan AI Assistant, an expert on the Maryland Lottery.
        Your goal is to help users find the best scratch-off games based on odds, prizes, and prices.
        Use the provided tools to query real-time game data.
        Be concise, helpful, and transparent about the risks of gambling.
        If a user asks for a recommendation, consider the top prizes remaining and the price point.
    """.trimIndent()

    fun getPromptContext(history: List<ChatMessage>): List<ChatMessage> {
        // Implement sliding window: keep last N turns
        if (history.size <= maxHistoryTurns) return history
        
        // Simple strategy: Keep the last N messages
        // In a real app, you'd use a tokenizer to count tokens accurately
        return history.takeLast(maxHistoryTurns)
    }
    
    // Future: Add summarization logic for older turns
}
