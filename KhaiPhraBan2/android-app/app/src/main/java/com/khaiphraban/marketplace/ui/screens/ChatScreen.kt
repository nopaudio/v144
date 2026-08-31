package com.khaiphraban.marketplace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState
import kotlinx.coroutines.delay

private val ChatPage = Color(0xFFF7F4EF)
private val ChatBrown = Color(0xFF4B2A12)
private val ChatGold = Color(0xFFE1AD45)
private val MyBubble = Color(0xFF5B3518)
private val OtherBubble = Color(0xFFFFFFFF)
private val MutedText = Color(0xFF786C61)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInboxScreen(viewModel: AppViewModel, onOpen: (Int, Int) -> Unit) {
    LaunchedEffect(Unit) {
        viewModel.loadChatThreads()
        while (true) {
            delay(5_000)
            viewModel.loadChatThreads(silent = true)
        }
    }

    val isRefreshing = viewModel.chatThreadsState is UiState.Loading

    Column(Modifier.fillMaxSize().background(ChatPage)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("ข้อความ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = ChatBrown)
            Text("สนทนาเรื่องประกาศของคุณแบบเรียลไทม์", style = MaterialTheme.typography.bodySmall, color = MutedText)
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadChatThreads() },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = viewModel.chatThreadsState) {
                UiState.Loading, UiState.Idle -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is UiState.Error -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { viewModel.loadChatThreads() }) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text("ลองใหม่")
                    }
                }

                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(shape = CircleShape, color = Color(0xFFFFEBC1)) {
                                Icon(Icons.Default.ChatBubbleOutline, null, Modifier.padding(18.dp).size(34.dp), tint = ChatBrown)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("ยังไม่มีข้อความสนทนา", fontWeight = FontWeight.Bold, color = ChatBrown)
                            Text("เมื่อมีคนสนใจประกาศ ห้องแชทจะอยู่ที่นี่", style = MaterialTheme.typography.bodySmall, color = MutedText)
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            items(
                                items = state.data,
                                key = { "${it.listingId}-${it.buyerId}" }
                            ) { t ->
                                Card(
                                    onClick = { onOpen(t.listingId, t.buyerId) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(13.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(shape = CircleShape, color = Color(0xFFFFEDC8)) {
                                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                                Text(
                                                    t.otherUsername.take(1).uppercase(),
                                                    color = ChatBrown,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    t.otherUsername,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = ChatBrown
                                                )
                                                if (t.otherRole == "admin") {
                                                    Spacer(Modifier.width(6.dp))
                                                    Surface(
                                                        color = ChatBrown,
                                                        contentColor = Color(0xFFFFD98A),
                                                        shape = RoundedCornerShape(999.dp)
                                                    ) {
                                                        Text(
                                                            "ADMIN",
                                                            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.weight(1f))
                                                t.updatedAt?.let {
                                                    Text(it.takeLast(8), style = MaterialTheme.typography.labelSmall, color = MutedText)
                                                }
                                            }
                                            Text(
                                                t.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color(0xFF9A6927),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                t.lastMessage ?: "เริ่มการสนทนา",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MutedText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (t.unreadCount > 0) {
                                            Spacer(Modifier.width(8.dp))
                                            Badge {
                                                Text(if (t.unreadCount > 99) "99+" else t.unreadCount.toString())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    listingId: Int,
    buyerId: Int,
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val me by viewModel.session.collectAsState()
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) pendingImage = uri
    }

    LaunchedEffect(listingId, buyerId) {
        viewModel.clearChatError()
        viewModel.loadChatMessages(listingId, buyerId)
        while (true) {
            delay(3_000)
            viewModel.loadChatMessages(listingId, buyerId, silent = true)
        }
    }

    val messages = (viewModel.chatMessagesState as? UiState.Success)?.data.orEmpty()
    val activeThread = (viewModel.chatThreadsState as? UiState.Success)?.data
        ?.firstOrNull { it.listingId == listingId && (buyerId == 0 || it.buyerId == buyerId) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(ChatPage)) {
        Surface(color = Color.White, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "ย้อนกลับ") }
                Surface(shape = CircleShape, color = Color(0xFFFFEBC1)) {
                    Icon(Icons.Default.ChatBubbleOutline, null, Modifier.padding(9.dp).size(21.dp), tint = ChatBrown)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            activeThread?.otherUsername ?: "ห้องสนทนา",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ChatBrown
                        )
                        if (activeThread?.otherRole == "admin") {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = ChatBrown,
                                contentColor = Color(0xFFFFD98A),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    "ADMIN",
                                    Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                    Text("ประกาศ #$listingId • อัปเดตอัตโนมัติ", style = MaterialTheme.typography.labelSmall, color = MutedText)
                }
                IconButton(onClick = { viewModel.loadChatMessages(listingId, buyerId) }) {
                    Icon(Icons.Default.Refresh, "รีเฟรช", tint = ChatBrown)
                }
            }
        }

        viewModel.chatError?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        error,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = viewModel::clearChatError) { Text("ปิด") }
                }
            }
        }

        when (val state = viewModel.chatMessagesState) {
            is UiState.Success -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(state.data, key = { it.id }) { m ->
                    val mine = m.senderId == me.userId
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!mine) {
                            Surface(shape = CircleShape, color = Color(0xFFFFEBC1), modifier = Modifier.padding(end = 6.dp)) {
                                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                    Text("•", color = ChatBrown, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        Surface(
                            color = if (mine) MyBubble else OtherBubble,
                            shape = if (mine)
                                RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp)
                            else
                                RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp),
                            shadowElevation = if (mine) 0.dp else 1.dp
                        ) {
                            Column(
                                Modifier.widthIn(max = 286.dp).padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                m.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = "รูปในแชท",
                                        modifier = Modifier
                                            .widthIn(max = 250.dp)
                                            .heightIn(min = 120.dp, max = 300.dp)
                                            .clickable { previewImageUrl = imageUrl },
                                        contentScale = ContentScale.Fit
                                    )
                                    if (m.message.isNotBlank()) Spacer(Modifier.height(7.dp))
                                }
                                if (m.message.isNotBlank()) {
                                    Text(m.message, color = if (mine) Color.White else ChatBrown)
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    m.createdAt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mine) Color.White.copy(alpha = .68f) else MutedText
                                )
                            }
                        }
                    }
                }
            }

            UiState.Loading, UiState.Idle -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is UiState.Error -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { viewModel.loadChatMessages(listingId, buyerId) }) { Text("ลองใหม่") }
            }
        }

        Surface(color = Color.White, shadowElevation = 10.dp) {
            Row(
                Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !viewModel.chatSending
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, "ส่งรูปภาพ", tint = ChatBrown)
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 1000) text = it },
                    placeholder = { Text("พิมพ์ข้อความ…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    enabled = !viewModel.chatSending,
                    shape = RoundedCornerShape(22.dp)
                )

                Spacer(Modifier.width(5.dp))
                FilledIconButton(
                    onClick = {
                        viewModel.sendMessage(listingId, buyerId, text) { text = "" }
                    },
                    enabled = text.isNotBlank() && !viewModel.chatSending,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ChatBrown)
                ) {
                    if (viewModel.chatSending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Send, "ส่ง", tint = Color.White)
                    }
                }
            }
        }
    }

    pendingImage?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!viewModel.chatSending) pendingImage = null },
            title = { Text("ส่งรูปนี้หรือไม่?") },
            text = {
                AsyncImage(
                    model = uri,
                    contentDescription = "รูปที่เลือก",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendChatImage(listingId, buyerId, uri) { pendingImage = null }
                    },
                    enabled = !viewModel.chatSending
                ) {
                    if (viewModel.chatSending) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("ส่งรูป")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImage = null }, enabled = !viewModel.chatSending) { Text("ยกเลิก") }
            }
        )
    }

    previewImageUrl?.let { url ->
        Dialog(onDismissRequest = { previewImageUrl = null }) {
            Surface(color = Color.Black, shape = RoundedCornerShape(18.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 620.dp)) {
                    AsyncImage(
                        model = url,
                        contentDescription = "ดูรูปแชท",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { previewImageUrl = null },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Close, "ปิด", tint = Color.White)
                    }
                }
            }
        }
    }
}
