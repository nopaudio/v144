package com.khaiphraban.marketplace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.khaiphraban.marketplace.data.model.Listing
import com.khaiphraban.marketplace.data.model.BoostSettings
import com.khaiphraban.marketplace.data.model.PremiumPlan
import com.khaiphraban.marketplace.data.model.WalletSummary
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val WalletBrown = Color(0xFF4B2A12)
private val WalletGold = Color(0xFFE0A93B)
private val WalletCream = Color(0xFFFFFAF2)
private val SuccessGreen = Color(0xFF167A43)

@Composable
fun PremiumScreen(viewModel: AppViewModel, onLogin: () -> Unit, onOpenListing: (Int) -> Unit) {
    val session by viewModel.session.collectAsState()
    var amount by rememberSaveable { mutableStateOf("") }
    var topupNote by rememberSaveable { mutableStateOf("") }
    var listingQuery by rememberSaveable { mutableStateOf("") }
    var showTransfer by rememberSaveable { mutableStateOf(false) }
    var slipUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) slipUri = uri
    }

    LaunchedEffect(session.token) {
        if (session.isLoggedIn) {
            viewModel.loadWallet()
            viewModel.loadMyListings()
        }
    }

    if (!session.isLoggedIn) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("กรุณาเข้าสู่ระบบเพื่อเติมแต้มและซื้อโพสต์พรีเมียม")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLogin) { Text("เข้าสู่ระบบ") }
        }
        return
    }

    when (val wallet = viewModel.walletState) {
        UiState.Loading, UiState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> ErrorBox(wallet.message) { viewModel.loadWallet() }
        is UiState.Success -> {
            val approvedListings = (viewModel.myListingsState as? UiState.Success)
                ?.data
                ?.filter { it.status == "approved" }
                .orEmpty()
            val manageableListings = approvedListings.filter { listing ->
                val query = listingQuery.trim()
                query.isBlank() ||
                    listing.title.contains(query, ignoreCase = true) ||
                    listing.tambon.contains(query, ignoreCase = true) ||
                    listing.amphoe.contains(query, ignoreCase = true) ||
                    listing.province.contains(query, ignoreCase = true)
            }
            val payment = wallet.data.payment
            val numericAmount = amount.replace(",", "").toDoubleOrNull()
            val pointPreview = numericAmount?.let { (it * payment.pointsPerBaht).roundToInt().coerceAtLeast(0) }

            LazyColumn(
                Modifier.fillMaxSize().background(WalletCream),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { BalanceHero(wallet.data.balance) }

                viewModel.walletMessage?.let { message ->
                    item {
                        Surface(
                            color = if (message.contains("สำเร็จ") || message.contains("ส่งสลิป")) Color(0xFFE7F6E9) else MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = viewModel::clearWalletMessage) { Text("ปิด") }
                            }
                        }
                    }
                }

                item {
                    Text("เติมแต้ม", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = WalletBrown)
                    Text(
                        "กรอกยอดที่ต้องการ จากนั้นระบบจะแสดงบัญชีรับโอนของแอดมิน แนบสลิปแล้วรออนุมัติ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = {
                                    amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12)
                                    showTransfer = false
                                },
                                label = { Text("ยอดเติม (บาท)") },
                                supportingText = {
                                    Text(
                                        if (numericAmount != null && pointPreview != null)
                                            "ประมาณ $pointPreview แต้ม • ขั้นต่ำ ${formatBaht(payment.minAmount)} บาท"
                                        else "กรอกจำนวนได้เอง ไม่ต้องเลือกแพ็กเกจ"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = { showTransfer = true },
                                enabled = payment.isActive &&
                                    numericAmount != null &&
                                    numericAmount >= payment.minAmount,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AccountBalance, null)
                                Spacer(Modifier.width(8.dp))
                                Text("ดูบัญชีและชำระเงิน")
                            }
                            if (!payment.isActive) {
                                Text("ระบบเติมแต้มถูกปิดชั่วคราว", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (showTransfer && numericAmount != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D7))
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("โอนเงินตามบัญชีนี้", fontWeight = FontWeight.ExtraBold, color = WalletBrown)
                                PaymentRow("ธนาคาร", payment.bankName)
                                PaymentRow("ชื่อบัญชี", payment.accountName)
                                PaymentRow("เลขบัญชี / PromptPay", payment.accountNumber)
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("ยอดที่ต้องโอน", fontWeight = FontWeight.Bold)
                                    Text("${formatBaht(numericAmount)} บาท", fontWeight = FontWeight.ExtraBold, color = WalletBrown)
                                }
                                Text(
                                    "หลังโอนแล้วให้แนบรูปสลิปด้านล่าง ระบบจะย่อรูปขนาดใหญ่ให้อัตโนมัติก่อนส่ง",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    item {
                        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("แนบสลิป", fontWeight = FontWeight.ExtraBold)
                                if (slipUri != null) {
                                    AsyncImage(
                                        model = slipUri,
                                        contentDescription = "สลิปที่เลือก",
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                OutlinedButton(
                                    onClick = { imagePicker.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !viewModel.walletBusy
                                ) {
                                    Icon(Icons.Default.ReceiptLong, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (slipUri == null) "เลือกรูปสลิป" else "เปลี่ยนรูปสลิป")
                                }
                                OutlinedTextField(
                                    value = topupNote,
                                    onValueChange = { topupNote = it.take(255) },
                                    label = { Text("หมายเหตุ (ถ้ามี)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )
                                Button(
                                    onClick = {
                                        slipUri?.let { uri ->
                                            viewModel.requestTopup(amount, topupNote, uri) {
                                                slipUri = null
                                                topupNote = ""
                                                showTransfer = false
                                                amount = ""
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = slipUri != null && !viewModel.walletBusy
                                ) {
                                    if (viewModel.walletBusy) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("ส่งสลิปให้แอดมินตรวจ")
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "จัดการประกาศ",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletBrown
                    )
                    Text(
                        "เห็นโพสต์แล้วกดดันหรือซื้อพรีเมียมได้จากรายการทันที • ดัน ${wallet.data.boost.pointsCost} แต้ม/ครั้ง",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = listingQuery,
                        onValueChange = { listingQuery = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("ค้นหาโพสต์ (${approvedListings.size})") },
                        placeholder = { Text("ชื่อโพสต์ หรือตำบล/อำเภอ/จังหวัด") }
                    )
                    if (!wallet.data.boost.isActive) {
                        Text(
                            "ระบบดันโพสต์ถูกปิดชั่วคราวโดยแอดมิน",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (manageableListings.isEmpty()) {
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(
                                    if (approvedListings.isEmpty()) "ยังไม่มีประกาศที่อนุมัติแล้ว" else "ไม่พบโพสต์ที่ค้นหา",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (approvedListings.isEmpty())
                                        "เมื่อแอดมินอนุมัติประกาศ รายการจะขึ้นที่นี่อัตโนมัติ"
                                    else "ลองค้นด้วยชื่อโพสต์หรือพื้นที่อื่น",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(manageableListings, key = { "manage-${it.id}" }) { listing ->
                        ListingActionRow(
                            listing = listing,
                            plans = wallet.data.plans,
                            boostSettings = wallet.data.boost,
                            busy = viewModel.walletBusy,
                            onBoost = viewModel::purchaseBoost,
                            onBuy = viewModel::purchasePremium,
                            onOpenListing = onOpenListing
                        )
                    }
                }

                val historyItems = buildWalletHistory(wallet.data)
                if (historyItems.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "ประวัติล่าสุด",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletBrown
                        )
                        Text(
                            "รวมดันโพสต์ พรีเมียม เติมแต้ม และรายการแต้มไว้ในที่เดียว",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item { CompactHistoryList(historyItems) }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, color = WalletBrown)
    }
}

@Composable
private fun BalanceHero(balance: Int) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(WalletBrown, Color(0xFF7B4A1D))), RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.AccountBalanceWallet, null, Modifier.padding(12.dp), tint = Color(0xFFFFD978))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("แต้มคงเหลือ", color = Color(0xFFFFE9B5), style = MaterialTheme.typography.bodyMedium)
                Text("$balance แต้ม", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun PulsingPremiumStar(size: androidx.compose.ui.unit.Dp = 24.dp) {
    val transition = rememberInfiniteTransition(label = "premium-pulse")
    val alpha by transition.animateFloat(
        initialValue = .45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha"
    )
    val scale by transition.animateFloat(
        initialValue = .92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale"
    )
    Icon(
        Icons.Default.Star,
        contentDescription = "พรีเมียม",
        tint = WalletGold,
        modifier = Modifier.size(size).alpha(alpha).scale(scale)
    )
}


@Composable
private fun ListingActionRow(
    listing: Listing,
    plans: List<PremiumPlan>,
    boostSettings: BoostSettings,
    busy: Boolean,
    onBoost: (Int) -> Unit,
    onBuy: (Int, Int) -> Unit,
    onOpenListing: (Int) -> Unit
) {
    var showPlans by rememberSaveable(listing.id) { mutableStateOf(false) }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = listing.images.firstOrNull()?.url,
                    contentDescription = listing.title,
                    modifier = Modifier.size(62.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        listing.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "฿${formatBaht(listing.price)} • ${listing.tambon} • ${listing.province}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (listing.isPremium) {
                            PulsingPremiumStar(14.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "พรีเมียมถึง ${listing.premiumUntil ?: "-"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = WalletBrown,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                listing.boostedAt?.let { "ดันล่าสุด $it" } ?: "พร้อมดัน / ซื้อพรีเมียม",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onOpenListing(listing.id) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("ดู", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = { onBoost(listing.id) },
                    enabled = boostSettings.isActive && !busy,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.TrendingUp, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ดัน ${boostSettings.pointsCost}", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        when {
                            plans.isEmpty() -> Unit
                            plans.size == 1 -> onBuy(listing.id, plans.first().id)
                            else -> showPlans = true
                        }
                    },
                    enabled = !busy && !listing.isPremium && plans.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    PulsingPremiumStar(14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (listing.isPremium) "พรีเมียมแล้ว" else "พรีเมียม",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    if (showPlans) {
        AlertDialog(
            onDismissRequest = { if (!busy) showPlans = false },
            title = { Text("เลือกแพ็กเกจพรีเมียม") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        listing.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    plans.forEach { plan ->
                        OutlinedButton(
                            onClick = {
                                showPlans = false
                                onBuy(listing.id, plan.id)
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${plan.name} • ${plan.pointsCost} แต้ม • ${plan.durationDays} วัน",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlans = false }, enabled = !busy) { Text("ยกเลิก") }
            }
        )
    }
}

private data class WalletHistoryItem(
    val key: String,
    val timestamp: String,
    val category: String,
    val title: String,
    val detail: String,
    val amountText: String? = null,
    val positive: Boolean? = null
)

private fun buildWalletHistory(wallet: WalletSummary): List<WalletHistoryItem> {
    val history = mutableListOf<WalletHistoryItem>()

    wallet.boosts.forEach { boost ->
        history += WalletHistoryItem(
            key = "boost-${boost.id}",
            timestamp = boost.boostedAt,
            category = "ดัน",
            title = boost.title,
            detail = "ดันโพสต์",
            amountText = "-${boost.pointsSpent}",
            positive = false
        )
    }
    wallet.promotions.forEach { promo ->
        history += WalletHistoryItem(
            key = "premium-${promo.id}",
            timestamp = promo.startsAt,
            category = "พรีเมียม",
            title = promo.title,
            detail = "${promo.planName} • ถึง ${promo.endsAt}",
            amountText = "-${promo.pointsSpent}",
            positive = false
        )
    }
    wallet.topupRequests.forEach { request ->
        history += WalletHistoryItem(
            key = "topup-${request.id}",
            timestamp = request.createdAt,
            category = "เติมแต้ม",
            title = "${formatBaht(request.amount)} บาท • ${request.points} แต้ม",
            detail = topupStatusLabel(request.status)
        )
    }
    wallet.transactions.forEach { transaction ->
        history += WalletHistoryItem(
            key = "tx-${transaction.id}",
            timestamp = transaction.createdAt,
            category = "แต้ม",
            title = transaction.description,
            detail = transaction.type,
            amountText = (if (transaction.amount > 0) "+" else "") + transaction.amount,
            positive = transaction.amount >= 0
        )
    }

    return history.sortedByDescending { it.timestamp }.take(30)
}

@Composable
private fun CompactHistoryList(history: List<WalletHistoryItem>) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth()) {
            history.forEachIndexed { index, item ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFFFF4D7),
                        shape = RoundedCornerShape(7.dp)
                    ) {
                        Text(
                            item.category,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = WalletBrown
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${item.detail} • ${item.timestamp}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    item.amountText?.let { amount ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            amount,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (item.positive == true) SuccessGreen else Color(0xFF9A2D20)
                        )
                    }
                }
                if (index < history.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)
                    )
                }
            }
        }
    }
}

private fun formatBaht(value: Double): String =
    NumberFormat.getNumberInstance(Locale("th", "TH")).format(value)

private fun topupStatusLabel(status: String): String = when (status) {
    "pending" -> "รอตรวจ"
    "approved" -> "สำเร็จ"
    "rejected" -> "ไม่อนุมัติ"
    else -> status
}
