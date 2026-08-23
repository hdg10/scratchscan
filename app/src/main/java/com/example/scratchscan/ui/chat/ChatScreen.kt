package com.example.scratchscan.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scratchscan.data.ChatMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Auto-scroll logic
    LaunchedEffect(messages.size, uiState.currentStreamingText) {
        if (messages.isNotEmpty() || uiState.currentStreamingText.isNotEmpty()) {
            // Only scroll if we are already near the bottom
            val isAtBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == listState.layoutInfo.totalItemsCount - 1
            if (isAtBottom || uiState.isGenerating) {
                listState.animateScrollToItem((messages.size + if (uiState.currentStreamingText.isNotEmpty()) 1 else 0))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Assistant") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    ChatBubble(message)
                }
                
                if (uiState.currentStreamingText.isNotEmpty()) {
                    item {
                        StreamingBubble(uiState.currentStreamingText)
                    }
                }
                
                if (uiState.isGenerating && uiState.currentStreamingText.isEmpty()) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            ChatInput(
                onSend = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.sendMessage(it) 
                },
                onStop = { viewModel.stopGeneration() },
                isGenerating = uiState.isGenerating
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "USER"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 0.dp,
                bottomEnd = if (isUser) 0.dp else 16.dp
            )
        ) {
            MarkdownText(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StreamingBubble(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier.padding(end = 48.dp)
    ) {
        MarkdownText(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TypingIndicator() {
    // Simplified shimmer/dots for loading
    Text("AI is thinking...", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp))
}

@Composable
fun ChatInput(
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    isGenerating: Boolean
) {
    var text by remember { mutableStateOf("") }

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about Maryland Lottery...") },
                maxLines = 4
            )
            
            if (isGenerating) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.Red)
                }
            } else {
                IconButton(onClick = { 
                    if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    // Basic Markdown implementation using AnnotatedString
    // Real implementation would parse bold, code, etc.
    Text(text = text, modifier = modifier, color = color, fontSize = 15.sp)
}
