package com.khaiphraban.marketplace.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.khaiphraban.marketplace.data.model.Listing
import com.khaiphraban.marketplace.ui.components.ListingCard
import com.khaiphraban.marketplace.ui.components.formatPrice
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState

@Composable
fun ListingDetailScreen(
    id: Int,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onChat: (Int, Int) -> Unit,
    onBuy: (Int) -> Unit,
    onLogin: () -> Unit,
    onSellerProfile: (Int) -> Unit,
    onListing: (Int) -> Unit
) {
    LaunchedEffect(id) { viewModel.loadListing(id) }

    val context = LocalContext.current
    val session by viewModel.session.collectAsState()

    when (val state = viewModel.detailState) {
        UiState.Loading, UiState.Idle -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is UiState.Error -> ErrorBox(state.message) { viewModel.loadListing(id) }

        is UiState.Success -> {
            val listing = state.data
            val seller = listing.seller
            val isOwnListing = session.isLoggedIn && seller?.id == session.userId
            val phone = seller?.phone?.takeIf { it.isNotBlank() }
            var fullScreenIndex by remember { mutableStateOf<Int?>(null) }
            var showPhone by remember(id) { mutableStateOf(false) }
            var showReportDialog by remember(id) { mutableStateOf(false) }

            Scaffold(
                bottomBar = {
                    ProductContactBar(
                        listing = listing,
                        isLoggedIn = session.isLoggedIn,
                        isOwnListing = isOwnListing,
                        favoriteBusy = viewModel.favoriteBusy,
                        onChat = {
                            if (!session.isLoggedIn) onLogin()
                            else if (!isOwnListing) onChat(listing.id, 0)
                        },
                        onBuy = {
                            if (!session.isLoggedIn) onLogin()
                            else if (!isOwnListing && listing.canBuy && listing.status == "approved") onBuy(listing.id)
                        },
                        onFavorite = {
                            if (!session.isLoggedIn) onLogin()
                            else viewModel.toggleFavorite(listing.id)
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "ย้อนกลับ")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                val shareUrl = listing.shareUrl
                                if (!shareUrl.isNullOrBlank()) {
                                    val fb = Uri.parse("https://www.facebook.com/sharer/sharer.php?u=${Uri.encode(shareUrl)}")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, fb))
                                } else {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, listing.title)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "แชร์ประกาศ"))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, "แชร์ไป Facebook")
                        }
                        if (session.isLoggedIn && !isOwnListing) {
                            IconButton(
                                onClick = { viewModel.toggleFavorite(listing.id) },
                                enabled = !viewModel.favoriteBusy
                            ) {
                                Icon(
                                    if (listing.isFavorite) Icons.Default.Favorite
                                    else Icons.Default.FavoriteBorder,
                                    if (listing.isFavorite) "สนใจแล้ว" else "บันทึกไว้"
                                )
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        itemsIndexed(listing.images) { index, image ->
                            AsyncImage(
                                model = image.url,
                                contentDescription = listing.title,
                                modifier = Modifier
                                    .width(320.dp)
                                    .height(260.dp)
                                    .clickable { fullScreenIndex = index },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    if (listing.images.isNotEmpty()) {
                        Text(
                            "แตะรูปเพื่อดูเต็มจอและซูมรายละเอียด",
                            Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (listing.isPremium) {
                            Surface(
                                color = Color(0xFFFFE7A3),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DetailPremiumStar()
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "โพสต์พรีเมียม${listing.premiumUntil?.let { " • ถึง $it" } ?: ""}",
                                        color = Color(0xFF6A3B12),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                        Text(
                            listing.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "฿${formatPrice(listing.price)}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        when {
                            listing.status == "sold" -> PurchaseStatusCard(
                                title = "ขายแล้ว",
                                detail = "ประกาศนี้ปิดการขายแล้วและไม่สามารถสั่งซื้อซ้ำได้",
                                strong = true
                            )
                            listing.hasActiveOrder -> PurchaseStatusCard(
                                title = "มีผู้สั่งซื้อแล้ว",
                                detail = "กำลังรอผู้ขายดำเนินการ จึงปิดปุ่มซื้อชั่วคราว",
                                strong = false
                            )
                            isOwnListing -> PurchaseStatusCard(
                                title = "ประกาศของคุณ",
                                detail = if (listing.allowBuyNow) "ผู้ซื้อสามารถสั่งซื้อผ่านระบบตามตัวเลือกที่คุณเปิดไว้" else "ผู้ซื้อจะติดต่อคุณผ่านแชท/การนัดรับตามตัวเลือกที่เปิดไว้",
                                strong = false
                            )
                            !listing.allowBuyNow -> PurchaseStatusCard(
                                title = "ติดต่อผู้ขายก่อน",
                                detail = "ประกาศนี้ไม่ได้เปิดสั่งซื้อผ่านระบบ กรุณาแชทกับผู้ขายก่อนตกลง",
                                strong = false
                            )
                            !listing.canBuy -> PurchaseStatusCard(
                                title = "ยังสั่งซื้อผ่านระบบไม่ได้",
                                detail = "ผู้ขายยังไม่มีบัญชีรับโอนที่ยืนยันแล้ว และไม่ได้เปิดเก็บเงินปลายทาง",
                                strong = false
                            )
                            else -> PurchaseStatusCard(
                                title = "พร้อมสั่งซื้อ",
                                detail = if (listing.allowCod) "เลือกโอนเข้าบัญชีที่ยืนยันแล้ว หรือเก็บเงินปลายทางได้" else "ชำระโดยโอนเข้าบัญชีผู้ขายที่ผ่านการยืนยัน และแนบสลิปในคำสั่งซื้อ",
                                strong = false
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val shareUrl = listing.shareUrl
                                if (!shareUrl.isNullOrBlank()) {
                                    val fb = Uri.parse("https://www.facebook.com/sharer/sharer.php?u=${Uri.encode(shareUrl)}")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, fb))
                                } else {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, listing.title)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "แชร์ประกาศ"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text("แชร์ประกาศไป Facebook")
                        }

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, null)
                                Spacer(Modifier.width(8.dp))
                                Text("${listing.tambon} • ${listing.amphoe} • ${listing.province}")
                            }
                        }

                        HorizontalDivider()
                        Text("รายละเอียด", fontWeight = FontWeight.Bold)
                        Text(
                            listing.description?.ifBlank { "ผู้ขายไม่ได้ใส่รายละเอียด" }
                                ?: "ผู้ขายไม่ได้ใส่รายละเอียด"
                        )

                        ListingPurchaseOptions(listing)

                        HorizontalDivider()

                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "ข้อมูลผู้ขาย",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, null)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            seller?.specialIcon?.takeIf { it.isNotBlank() }?.let {
                                                Text("$it ", style = MaterialTheme.typography.titleLarge)
                                            }
                                            Text(
                                                seller?.username ?: "สมาชิก",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (seller?.isAdmin == true) {
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
                                        seller?.memberSince?.takeIf { it.isNotBlank() }?.let {
                                            Text(
                                                "สมาชิกตั้งแต่ ${it.take(10)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (seller?.isVerified == true) {
                                                Icon(Icons.Default.Verified, null, Modifier.size(16.dp), tint = Color(0xFF1E7A45))
                                                Spacer(Modifier.width(4.dp))
                                                Text("ยืนยันแล้ว", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1E7A45))
                                            } else {
                                                Text("ยังไม่ยืนยันตัวตน", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        if ((seller?.adminStars ?: 0) > 0) {
                                            Text(
                                                "ดาวจากแอดมิน ${"★".repeat((seller?.adminStars ?: 0).coerceIn(0, 5))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF8A5A00),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = Color(0xFFD9972D))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if ((seller?.ratingCount ?: 0) > 0) "${String.format("%.1f", seller?.ratingAverage ?: 0.0)} • ${seller?.ratingCount} รีวิว"
                                                else "ยังไม่มีคะแนน",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }

                                seller?.id?.takeIf { it > 0 }?.let { sellerId ->
                                    OutlinedButton(
                                        onClick = { onSellerProfile(sellerId) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("ดูโปรไฟล์ผู้ขาย") }
                                }

                                Text(
                                    "พื้นที่ประกาศ: ${listing.province}",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (phone != null) {
                                    if (showPhone) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Phone, null)
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    phone,
                                                    modifier = Modifier.weight(1f),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                TextButton(
                                                    onClick = {
                                                        context.startActivity(
                                                            Intent(
                                                                Intent.ACTION_DIAL,
                                                                Uri.parse("tel:$phone")
                                                            )
                                                        )
                                                    }
                                                ) {
                                                    Text("โทร")
                                                }
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { showPhone = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Visibility, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("ดูเบอร์โทรผู้ขาย")
                                        }
                                    }
                                } else {
                                    Text(
                                        "ผู้ขายยังไม่ได้ใส่เบอร์โทร",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                seller?.lineId?.takeIf { it.isNotBlank() }?.let { line ->
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(
                                                        "https://line.me/ti/p/~${Uri.encode(line)}"
                                                    )
                                                )
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("ติดต่อ LINE: $line")
                                    }
                                }

                                if (!isOwnListing) {
                                    OutlinedButton(
                                        onClick = {
                                            if (!session.isLoggedIn) onLogin() else showReportDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Report, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("แจ้งปัญหา / รายงานผู้ใช้")
                                    }
                                }
                            }
                        }

                        if (!session.isLoggedIn) {
                            Text(
                                "เข้าสู่ระบบเพื่อซื้อเลย แชทกับผู้ขาย หรือบันทึกรายการที่สนใจ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (isOwnListing) {
                            Text(
                                "นี่คือประกาศของคุณ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        viewModel.reportMessage?.let { message ->
                            Surface(
                                color = Color(0xFFE9F7EE),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = viewModel::clearReportMessage) { Text("ปิด") }
                                }
                            }
                        }

                        Text(
                            "คำเตือน: บัญชีที่ขึ้นว่ายืนยันแล้วช่วยให้ตรวจสอบผู้รับโอนได้ แต่ไม่ใช่การรับประกันสินค้า หากมีเก็บเงินปลายทางและต้องการลดความเสี่ยง ระบบแนะนำให้พิจารณาตัวเลือกนั้น",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )

                        val suggestions = (viewModel.homeState as? UiState.Success)
                            ?.data?.random
                            ?.filter { it.id != listing.id }
                            ?.take(6)
                            .orEmpty()

                        if (suggestions.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                "เผื่อคุณสนใจ",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "ประกาศสุ่มจากรายการที่กำลังเผยแพร่",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(suggestions, key = { it.id }) { suggested ->
                                    ListingCard(
                                        listing = suggested,
                                        modifier = Modifier.width(220.dp),
                                        onClick = { onListing(suggested.id) }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            if (showReportDialog) {
                ReportUserDialog(
                    busy = viewModel.reportBusy,
                    onDismiss = { if (!viewModel.reportBusy) showReportDialog = false },
                    onSubmit = { category, details ->
                        viewModel.reportUser(
                            listingId = listing.id,
                            reportedUserId = seller?.id ?: 0,
                            category = category,
                            details = details
                        ) { showReportDialog = false }
                    }
                )
            }

            fullScreenIndex?.let { index ->
                ZoomImageDialog(
                    urls = listing.images.map { it.url },
                    initialIndex = index,
                    title = listing.title,
                    onDismiss = { fullScreenIndex = null }
                )
            }
        }
    }
}

@Composable
private fun DetailPremiumStar() {
    val transition = rememberInfiniteTransition(label = "detail-premium")
    val alpha by transition.animateFloat(
        initialValue = .45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "alpha"
    )
    val scale by transition.animateFloat(
        initialValue = .9f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "scale"
    )
    Icon(
        Icons.Default.Star,
        contentDescription = "พรีเมียม",
        tint = Color(0xFF6A3B12),
        modifier = Modifier.size(18.dp).alpha(alpha).scale(scale)
    )
}

@Composable
private fun ListingPurchaseOptions(listing: Listing) {
    val options = buildList {
        if (listing.allowMeetup) add("✓ นัดรับได้")
        if (listing.allowBuyNow) add("✓ สั่งซื้อผ่านระบบได้")
        if (listing.allowCod) add("✓ เก็บเงินปลายทางได้")
        if (listing.chatFirst) add("✓ คุยกันก่อนในแชท — ทักมาเลย")
    }
    if (options.isEmpty()) return

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("ตัวเลือกจากผู้ขาย", fontWeight = FontWeight.Bold)
            options.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ReportUserDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    val categories = listOf(
        "fraud" to "สงสัยโกง",
        "payment" to "ปัญหาการชำระเงิน",
        "fake_listing" to "ประกาศน่าสงสัย",
        "inappropriate" to "พฤติกรรมไม่เหมาะสม",
        "other" to "อื่น ๆ"
    )
    var selected by remember { mutableStateOf("fraud") }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("แจ้งปัญหา / รายงานผู้ใช้") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("เลือกประเภทปัญหา", fontWeight = FontWeight.Bold)
                categories.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = value },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == value, onClick = { selected = value })
                        Text(label)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(1000) },
                    label = { Text("รายละเอียด") },
                    placeholder = { Text("เช่น โอนเงินแล้วไม่ได้รับสินค้า พร้อมข้อมูลที่ช่วยให้แอดมินตรวจสอบ") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selected, details) },
                enabled = details.trim().length >= 5 && !busy
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(7.dp))
                }
                Text("ส่งให้แอดมิน")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("ยกเลิก") } }
    )
}

@Composable
private fun ProductContactBar(
    listing: Listing,
    isLoggedIn: Boolean,
    isOwnListing: Boolean,
    favoriteBusy: Boolean,
    onChat: () -> Unit,
    onBuy: () -> Unit,
    onFavorite: () -> Unit
) {
    val buyEnabled = !isOwnListing &&
        listing.status == "approved" &&
        listing.allowBuyNow &&
        listing.canBuy &&
        !listing.hasActiveOrder

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = Color(0xFFFFFBF4)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onChat,
                modifier = Modifier.weight(.86f),
                enabled = !isOwnListing,
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Icon(Icons.Default.Chat, null)
                Spacer(Modifier.width(4.dp))
                Text("แชท")
            }

            Button(
                onClick = onBuy,
                modifier = Modifier.weight(1.35f),
                enabled = buyEnabled,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, null)
                Spacer(Modifier.width(5.dp))
                Text(
                    when {
                        listing.status == "sold" -> "ขายแล้ว"
                        listing.hasActiveOrder -> "มีผู้สั่งแล้ว"
                        isOwnListing -> "ประกาศของคุณ"
                        !listing.allowBuyNow -> "ติดต่อก่อน"
                        !listing.canBuy -> "ยังซื้อไม่ได้"
                        else -> "ซื้อเลย"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            }

            IconButton(
                onClick = onFavorite,
                enabled = !isOwnListing && !favoriteBusy
            ) {
                if (favoriteBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (listing.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (listing.isFavorite) "สนใจแล้ว" else "บันทึกไว้"
                    )
                }
            }
        }
    }
}

@Composable
private fun PurchaseStatusCard(title: String, detail: String, strong: Boolean) {
    Surface(
        color = if (strong) Color(0xFFF4E7E0) else Color(0xFFFFF4D7),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ShoppingCart,
                contentDescription = null,
                tint = if (strong) Color(0xFF8B3325) else Color(0xFF7A4B12)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4A2A13))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ZoomImageDialog(
    urls: List<String>,
    initialIndex: Int,
    title: String,
    onDismiss: () -> Unit
) {
    var index by remember { mutableIntStateOf(initialIndex) }
    var scale by remember(index) { mutableFloatStateOf(1f) }
    var offsetX by remember(index) { mutableFloatStateOf(0f) }
    var offsetY by remember(index) { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = urls[index],
                contentDescription = title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .transformable(transformState),
                contentScale = ContentScale.Fit
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, "ปิด", tint = Color.White)
            }

            if (index > 0) {
                IconButton(
                    onClick = { index-- },
                    modifier = Modifier.align(Alignment.CenterStart).padding(8.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, "รูปก่อนหน้า", tint = Color.White)
                }
            }

            if (index < urls.lastIndex) {
                IconButton(
                    onClick = { index++ },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, "รูปถัดไป", tint = Color.White)
                }
            }

            Text(
                "${index + 1}/${urls.size}  •  ใช้ 2 นิ้วซูมได้สูงสุด 6 เท่า",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
            )
        }
    }
}
