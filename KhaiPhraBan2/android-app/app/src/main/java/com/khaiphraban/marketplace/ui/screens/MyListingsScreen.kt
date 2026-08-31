package com.khaiphraban.marketplace.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.khaiphraban.marketplace.data.model.Listing
import com.khaiphraban.marketplace.ui.components.formatPrice
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState

private const val FILTER_ALL = "all"
private const val FILTER_SELLING = "selling"
private const val FILTER_PREMIUM = "premium"
private const val FILTER_SOLD = "sold"

@Composable
fun MyListingsScreen(
    viewModel: AppViewModel,
    onOpen: (Int) -> Unit,
    onLogin: () -> Unit,
    onPromote: () -> Unit,
    onMyOrders: () -> Unit,
    onReceivedOrders: () -> Unit,
    onVerification: () -> Unit,
    onLottery: () -> Unit,
    onAdmin: () -> Unit = {}
) {
    val session by viewModel.session.collectAsState()
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var showDisplayNameDialog by rememberSaveable { mutableStateOf(false) }
    var newDisplayName by rememberSaveable { mutableStateOf("") }
    var displayNameReason by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(session.token) {
        if (session.isLoggedIn) {
            viewModel.loadMyListings()
            viewModel.loadMyProfile()
        }
    }

    if (!session.isLoggedIn) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("กรุณาเข้าสู่ระบบเพื่อดูประกาศของคุณ")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLogin) { Text("เข้าสู่ระบบ") }
        }
        return
    }

    val profile = (viewModel.myProfileState as? UiState.Success)?.data
    val currentDisplayName = profile?.displayName?.takeIf { it.isNotBlank() }
        ?: profile?.username
        ?: session.username.orEmpty()

    when (val state = viewModel.myListingsState) {
        UiState.Loading, UiState.Idle -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is UiState.Error -> ErrorBox(state.message) { viewModel.loadMyListings() }

        is UiState.Success -> {
            val filtered = when (filter) {
                FILTER_SELLING -> state.data.filter { it.status == "approved" }
                FILTER_PREMIUM -> state.data.filter { it.isPremium }
                FILTER_SOLD -> state.data.filter { it.status == "sold" }
                else -> state.data
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Text("ของฉัน", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAF2))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountCircle, null, Modifier.size(52.dp), tint = Color(0xFF6D3B16))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        profile?.specialIcon?.takeIf { it.isNotBlank() }?.let {
                                            Text("$it ", style = MaterialTheme.typography.titleLarge)
                                        }
                                        Text(
                                            currentDisplayName,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        if (profile?.isAdmin == true || session.isAdmin) {
                                            Spacer(Modifier.width(7.dp))
                                            Surface(
                                                color = Color(0xFF4B2A12),
                                                contentColor = Color(0xFFFFD98A),
                                                shape = RoundedCornerShape(999.dp)
                                            ) {
                                                Text("ADMIN", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (profile?.isVerified == true) {
                                            Icon(Icons.Default.Verified, null, Modifier.size(16.dp), tint = Color(0xFF1E7A45))
                                            Spacer(Modifier.width(4.dp))
                                            Text("ยืนยันแล้ว", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1E7A45))
                                        } else {
                                            Text(profile?.verification?.statusLabel ?: "ยังไม่ยืนยัน", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(
                                        if ((profile?.ratingCount ?: 0) > 0) "★ ${String.format("%.1f", profile?.ratingAverage ?: 0.0)} • ${profile?.ratingCount} รีวิว" else "ยังไม่มีคะแนน",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF7C5C2A)
                                    )
                                    if ((profile?.adminStars ?: 0) > 0) {
                                        Text(
                                            "ดาวจากแอดมิน ${"★".repeat((profile?.adminStars ?: 0).coerceIn(0, 5))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF8A5A00),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    profile?.memberSince?.takeIf { it.isNotBlank() }?.let {
                                        Text("สมาชิกตั้งแต่ ${it.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            profile?.pendingDisplayNameRequest?.let { request ->
                                Surface(
                                    color = Color(0xFFFFF0CC),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "รอแอดมินอนุมัติชื่อ “${request.requestedName}”",
                                        Modifier.fillMaxWidth().padding(10.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF6A4710)
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    newDisplayName = currentDisplayName
                                    displayNameReason = ""
                                    viewModel.clearDisplayNameMessage()
                                    showDisplayNameDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = profile?.pendingDisplayNameRequest == null && !viewModel.displayNameBusy
                            ) {
                                Text(if (profile?.canChangeDisplayNameDirectly != false) "ตั้ง / เปลี่ยนชื่อภาษาไทย" else "ขอเปลี่ยนชื่อ")
                            }
                            if (!showDisplayNameDialog) {
                                viewModel.displayNameMessage?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (it.contains("แล้ว")) Color(0xFF1E7A45) else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Button(
                                onClick = onLottery,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB97916))
                            ) {
                                Text("ร่วมสนุกลุ้นพระ • เลขรัฐบาล 2 ตัว")
                            }
                            OutlinedButton(onClick = onVerification, modifier = Modifier.fillMaxWidth()) {
                                Text("ยืนยันตัวตน • ${profile?.verification?.statusLabel ?: "ยังไม่ยืนยัน"}")
                            }
                            if (profile?.isAdmin == true || session.isAdmin) {
                                Button(
                                    onClick = onAdmin,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B2A12))
                                ) {
                                    Icon(Icons.Default.AdminPanelSettings, null)
                                    Spacer(Modifier.width(7.dp))
                                    Text("ผู้ดูแลระบบ")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onMyOrders, modifier = Modifier.weight(1f)) {
                            Text("คำสั่งซื้อของฉัน", maxLines = 1)
                        }
                        OutlinedButton(onClick = onReceivedOrders, modifier = Modifier.weight(1f)) {
                            Text("คำสั่งซื้อที่ได้รับ", maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "ประกาศของ $currentDisplayName",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                }

                item {
                    val filters = listOf(
                        FILTER_ALL to "ทั้งหมด",
                        FILTER_SELLING to "กำลังขาย",
                        FILTER_PREMIUM to "พรีเมียม",
                        FILTER_SOLD to "ขายแล้ว"
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 6.dp)
                    ) {
                        items(filters, key = { it.first }) { option ->
                            FilterChip(
                                selected = filter == option.first,
                                onClick = { filter = option.first },
                                label = { Text(option.second) }
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                if (state.data.isEmpty()) "ยังไม่มีประกาศ" else "ไม่มีประกาศในสถานะนี้",
                                modifier = Modifier.padding(14.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { listing ->
                        MyListingCompactRow(
                            listing = listing,
                            onOpen = { onOpen(listing.id) },
                            onPromote = onPromote,
                            onMarkSold = { viewModel.markSold(listing.id) },
                            onDelete = { viewModel.deleteListing(listing.id) }
                        )
                    }
                }
            }
        }
    }

    if (showDisplayNameDialog) {
        AlertDialog(
            onDismissRequest = { if (!viewModel.displayNameBusy) showDisplayNameDialog = false },
            title = {
                Text(
                    if (profile?.canChangeDisplayNameDirectly != false) "ตั้งชื่อที่แสดง" else "ส่งคำขอเปลี่ยนชื่อ"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "ชื่อที่แสดงรองรับภาษาไทยและไม่กระทบ username ที่ใช้เข้าสู่ระบบ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newDisplayName,
                        onValueChange = {
                            newDisplayName = it.take(40)
                            viewModel.clearDisplayNameMessage()
                        },
                        label = { Text("ชื่อที่แสดง") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    viewModel.displayNameMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (profile?.canChangeDisplayNameDirectly == false) {
                        Text(
                            "คุณใช้สิทธิ์เปลี่ยนเอง 1 ครั้งแล้ว ครั้งนี้ต้องให้แอดมินอนุมัติ",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8A5A00)
                        )
                        OutlinedTextField(
                            value = displayNameReason,
                            onValueChange = { displayNameReason = it.take(500) },
                            label = { Text("เหตุผล *") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDisplayName(newDisplayName, displayNameReason) {
                            showDisplayNameDialog = false
                        }
                    },
                    enabled = !viewModel.displayNameBusy &&
                        newDisplayName.trim().length in 2..40 &&
                        (profile?.canChangeDisplayNameDirectly != false || displayNameReason.trim().length >= 5)
                ) {
                    if (viewModel.displayNameBusy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (profile?.canChangeDisplayNameDirectly != false) "บันทึก" else "ส่งให้แอดมิน")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisplayNameDialog = false },
                    enabled = !viewModel.displayNameBusy
                ) { Text("ยกเลิก") }
            }
        )
    }
}

@Composable
private fun MyListingCompactRow(
    listing: Listing,
    onOpen: () -> Unit,
    onPromote: () -> Unit,
    onMarkSold: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                AsyncImage(
                    model = listing.images.firstOrNull()?.url,
                    contentDescription = listing.title,
                    modifier = Modifier.size(82.dp).clip(RoundedCornerShape(11.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        listing.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "฿${formatPrice(listing.price)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    MiniListingBadge(statusLabel(listing.status), statusBadgeColor(listing.status))
                    if (listing.isPremium || !listing.boostedAt.isNullOrBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (listing.isPremium) {
                                MiniListingBadge("พรีเมียม", Color(0xFFFFE3A1), Icons.Default.Star)
                            }
                            if (!listing.boostedAt.isNullOrBlank()) {
                                MiniListingBadge("Boost", Color(0xFFFFEDD0), Icons.Default.TrendingUp)
                            }
                        }
                    }
                    Text(
                        "ลงเมื่อ ${listing.createdAt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "จัดการประกาศ")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("ลบประกาศ") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (listing.status == "approved") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = onPromote, modifier = Modifier.weight(1f)) {
                        Text("พรีเมียม / ดันโพสต์", maxLines = 1)
                    }
                    TextButton(onClick = onMarkSold, modifier = Modifier.weight(1f)) {
                        Text("ขายแล้ว", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniListingBadge(
    text: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(2.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "pending" -> "รอตรวจ"
    "approved" -> "กำลังขาย"
    "hidden" -> "ซ่อน"
    "rejected" -> "ไม่อนุมัติ"
    "sold" -> "ขายแล้ว"
    else -> status
}

private fun statusBadgeColor(status: String): Color = when (status) {
    "approved" -> Color(0xFFE6F3E9)
    "sold" -> Color(0xFFF0DDD7)
    "rejected" -> Color(0xFFFFE2E0)
    "pending" -> Color(0xFFFFF0C8)
    else -> Color(0xFFECECEC)
}
