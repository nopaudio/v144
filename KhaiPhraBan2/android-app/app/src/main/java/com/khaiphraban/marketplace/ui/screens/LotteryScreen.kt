package com.khaiphraban.marketplace.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.khaiphraban.marketplace.data.model.LotteryRound
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState

@Composable
fun LotteryScreen(viewModel: AppViewModel) {
    val session by viewModel.session.collectAsState()
    var selectedNumber by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(session.token) {
        if (session.isLoggedIn) viewModel.loadLottery()
    }

    if (!session.isLoggedIn) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("กรุณาเข้าสู่ระบบก่อนร่วมสนุก")
        }
        return
    }

    when (val state = viewModel.lotteryState) {
        UiState.Idle, UiState.Loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is UiState.Error -> ErrorBox(state.message) { viewModel.loadLottery() }

        is UiState.Success -> {
            val data = state.data
            val round = data.round
            val sold = data.soldNumbers.toSet()
            val mine = data.myEntries.mapNotNull { it.number.toIntOrNull() }.toSet()
            val recentResults = data.recentResults.filterNot { it.id == round?.id }

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "ร่วมสนุกลุ้นพระ",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "ใช้แต้มเลือกเลขรัฐบาล 2 ตัว • เลขเดียวกันซื้อซ้ำในรอบเดียวกันไม่ได้",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                viewModel.lotteryMessage?.let { message ->
                    item {
                        Surface(
                            color = if (message.contains("สำเร็จ")) Color(0xFFE3F4E8) else Color(0xFFFFE8E5),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                message,
                                Modifier.fillMaxWidth().padding(12.dp),
                                color = if (message.contains("สำเร็จ")) Color(0xFF17603A) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (round == null) {
                    item {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(18.dp)) {
                                Text("ยังไม่มีรอบร่วมสนุก", fontWeight = FontWeight.Bold)
                                Text(
                                    "เมื่อแอดมินเปิดรอบใหม่ รายละเอียดรางวัลและเลข 00–99 จะปรากฏที่นี่",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item { PrizeRoundCard(round, data.balance, sold.size) }

                    if (data.myEntries.isNotEmpty()) {
                        item {
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6D8)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("เลขของฉัน", fontWeight = FontWeight.ExtraBold, color = Color(0xFF6D4518))
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        data.myEntries.joinToString(" • ") { it.number },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (round.status == "open") {
                        item {
                            Text("เลือกเลข 00–99", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "ว่าง ${100 - sold.size} เลข • สีทองคือเลขของคุณ • สีเทาคือมีเจ้าของแล้ว",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                (0..99).chunked(5).forEach { rowNumbers ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowNumbers.forEach { number ->
                                            val isMine = number in mine
                                            val isTaken = number in sold
                                            Button(
                                                onClick = { selectedNumber = number },
                                                enabled = !isTaken && !viewModel.lotteryBusy,
                                                modifier = Modifier.weight(1f).height(46.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isMine) Color(0xFFD59A2A) else Color(0xFF6D3B16),
                                                    contentColor = Color.White,
                                                    disabledContainerColor = if (isMine) Color(0xFFD59A2A) else Color(0xFFE0DDD8),
                                                    disabledContentColor = if (isMine) Color.White else Color(0xFF77716A)
                                                )
                                            ) {
                                                Text(number.toString().padStart(2, '0'), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Surface(
                                color = Color(0xFFF3EEE7),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    when (round.status) {
                                        "announced" -> "รอบนี้ประกาศผลแล้ว"
                                        "closed" -> "รอบนี้ปิดรับเลขแล้ว รอประกาศผล"
                                        else -> "รอบนี้ยังไม่เปิดรับเลข"
                                    },
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (round.status == "announced") {
                        item { ResultCard(round) }
                    }
                }

                if (recentResults.isNotEmpty()) {
                    item {
                        Text("ผลรางวัลล่าสุด", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    }
                    items(recentResults, key = { it.id }) { result ->
                        Surface(
                            color = Color(0xFFF8F4ED),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(13.dp)) {
                                Text(result.title, fontWeight = FontWeight.Bold)
                                Text(
                                    "เลข ${result.winningNumber ?: "--"} • ${result.winner?.displayName?.let { "ผู้ชนะ $it" } ?: "ไม่มีผู้ชนะ"}",
                                    color = Color(0xFF6D4518)
                                )
                            }
                        }
                    }
                }
            }

            val number = selectedNumber
            if (number != null && round != null) {
                val numberText = number.toString().padStart(2, '0')
                AlertDialog(
                    onDismissRequest = { if (!viewModel.lotteryBusy) selectedNumber = null },
                    title = { Text("ยืนยันซื้อเลข $numberText") },
                    text = {
                        Text("ใช้ ${round.pointsCost} แต้มซื้อเลข $numberText สำหรับรอบ “${round.title}”\n\nเมื่อยืนยันแล้ว เลขนี้จะถูกล็อกให้บัญชีของคุณทันที")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.buyLotteryNumber(round.id, number)
                                selectedNumber = null
                            },
                            enabled = !viewModel.lotteryBusy && data.balance >= round.pointsCost
                        ) {
                            if (viewModel.lotteryBusy) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (data.balance >= round.pointsCost) "ยืนยัน ${round.pointsCost} แต้ม" else "แต้มไม่พอ")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { selectedNumber = null },
                            enabled = !viewModel.lotteryBusy
                        ) { Text("ยกเลิก") }
                    }
                )
            }
        }
    }
}

@Composable
private fun PrizeRoundCard(round: LotteryRound, balance: Int, soldCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAF2))
    ) {
        Column(Modifier.fillMaxWidth()) {
            round.prizeImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = round.prizeName,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(round.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("รางวัล: ${round.prizeName}", fontWeight = FontWeight.Bold, color = Color(0xFF6D3B16))
                if (round.prizeDescription.isNotBlank()) {
                    Text(round.prizeDescription)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("งวด ${round.drawDate} • ${round.pointsCost} แต้ม/เลข")
                Text(
                    "แต้มของคุณ $balance • มีเจ้าของแล้ว $soldCount/100 เลข",
                    fontWeight = FontWeight.Bold,
                    color = if (balance >= round.pointsCost) Color(0xFF17603A) else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ResultCard(round: LotteryRound) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4B2A12)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ผลรางวัล 2 ตัว", color = Color(0xFFFFE4A7), fontWeight = FontWeight.Bold)
            Text(
                round.winningNumber ?: "--",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                round.winner?.displayName?.let { "ผู้ชนะ $it" } ?: "เลขนี้ไม่มีสมาชิกซื้อ",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
