package com.moblin.android.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moblin.android.chat.ChatMessage

@Composable
fun ChatOverlay(
    messages: List<ChatMessage>,
    fontSize: Int,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            ChatMessageItem(message = message, fontSize = fontSize)
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    fontSize: Int,
) {
    val usernameColor = message.color?.let {
        try {
            Color(android.graphics.Color.parseColor(it))
        } catch (_: Exception) {
            Color(0xFF9C7CF4)
        }
    } ?: Color(0xFF9C7CF4)

    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = usernameColor, fontWeight = FontWeight.Bold)) {
                append(message.username)
            }
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.5f))) {
                append(": ")
            }
            withStyle(SpanStyle(color = Color.White)) {
                append(message.message)
            }
        },
        fontSize = fontSize.sp,
        lineHeight = (fontSize + 4).sp,
    )
}
