package com.khaiphraban.marketplace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.khaiphraban.marketplace.data.model.Listing
import com.khaiphraban.marketplace.data.model.Order
import com.khaiphraban.marketplace.ui.components.formatPrice
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState

private val OrderBrown = Color(0xFF4A2810)
private val OrderGold = Color(0xFFD9972D)
private val OrderCream = Color(0xFFFFFAF2)

@Composable
fun CheckoutScreen(
    listingId: Int,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onCreated: (Int) -> Unit
) {
    LaunchedEffect(listingId) {
        // Refresh before payment so bank-verification/account data is current.
        viewModel.loadListing(listingId)
    }

    when (val state = viewModel.detailState) {
        UiState.Loading, UiState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> ErrorBox(state.message) { viewModel.loadListing(listingId) }
        is UiState.Success -> CheckoutForm(state.data, viewModel, onBack, onCreated)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutForm(
    listing: Listing,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onCreated: (Int) -> Unit
) {
    var recipient by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var house by rememberSaveable { mutableStateOf("") }
    var soi by rememberSaveable { mutableStateOf("") }
    var road by rememberSaveable { mutableStateOf("") }
    var subdistrict by rememberSaveable { mutableStateOf(listing.tambon) }
    var district by rememberSaveable { mutableStateOf(listing.amphoe) }
    var province by rememberSaveable { mutableStateOf(listing.province) }
    var postalCode by rememberSaveable { mutableStateOf("") }
    val sellerPayment = listing.sellerPayment
    var paymentMethod by rememberSaveable(listing.id) {
        mutableStateOf(
            when {
                listing.allowCod -> "cod"
                sellerPayment?.isVerified == true -> "bank_transfer"
                else -> ""
            }
        )
    }
    var slipUri by remember { mutableStateOf<Uri?>(null) }
    val slipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        slipUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ข้อมูลจัดส่ง") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "ย้อนกลับ") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(OrderCream).padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = listing.images.firstOrNull()?.url,
                        contentDescription = listing.title,
                        modifier = Modifier.size(78.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(listing.title, fontWeight = FontWeight.Bold)
                        Text("฿${formatPrice(listing.price)}", color = OrderBrown, fontWeight = FontWeight.ExtraBold)
                        if (listing.hasActiveOrder) {
                            Text("มีผู้สั่งซื้อแล้ว", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Text("ผู้รับสินค้า", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            OutlinedTextField(recipient, { recipient = it.take(160) }, label = { Text("ชื่อผู้รับ *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it.take(30) }, label = { Text("เบอร์โทรศัพท์ *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text("ที่อยู่จัดส่ง", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            OutlinedTextField(house, { house = it.take(190) }, label = { Text("บ้านเลขที่ / หมู่ *") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(soi, { soi = it.take(120) }, label = { Text("ซอย") }, modifier = Modifier.weight(1f))
                OutlinedTextField(road, { road = it.take(120) }, label = { Text("ถนน") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(subdistrict, { subdistrict = it.take(100) }, label = { Text("ตำบล / แขวง *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(district, { district = it.take(100) }, label = { Text("อำเภอ / เขต *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(province, { province = it.take(100) }, label = { Text("จังหวัด *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(postalCode, { postalCode = it.filter { ch -> ch.isDigit() }.take(5) }, label = { Text("รหัสไปรษณีย์ *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text("วิธีชำระเงิน", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sellerPayment?.isVerified == true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = paymentMethod == "bank_transfer",
                                onClick = { paymentMethod = "bank_transfer" }
                            )
                            Column(Modifier.weight(1f)) {
                                Text("ชำระเงินทันที", fontWeight = FontWeight.Bold)
                                Text(
                                    "โอนเข้าบัญชีผู้ขายที่แอดมินยืนยันแล้ว",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (listing.allowCod) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = paymentMethod == "cod",
                                onClick = {
                                    paymentMethod = "cod"
                                    slipUri = null
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text("เก็บเงินปลายทาง", fontWeight = FontWeight.Bold)
                                Text(
                                    "ระบบแนะนำ หากต้องการลดความเสี่ยงจากการโอนก่อน",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF1E7A45),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (sellerPayment?.isVerified != true && !listing.allowCod) {
                        Text(
                            "ยังไม่มีวิธีชำระเงินที่ระบบรองรับ: ผู้ขายยังไม่มีบัญชีที่ยืนยัน และไม่ได้เปิดเก็บเงินปลายทาง",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (paymentMethod == "bank_transfer" && sellerPayment?.isVerified == true) {
                Surface(color = Color(0xFFE8F3EC), shape = RoundedCornerShape(14.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, null, tint = Color(0xFF1E7A45))
                            Spacer(Modifier.width(7.dp))
                            Text("บัญชีรับโอนที่ยืนยันแล้ว", fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E7A45))
                        }
                        Text("ธนาคาร: ${sellerPayment.bankName}")
                        Text("ชื่อบัญชี: ${sellerPayment.accountName}")
                        Text("เลขบัญชี: ${sellerPayment.accountNumber}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { slipPicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (slipUri == null) "แนบสลิป *" else "เปลี่ยนสลิป")
                        }
                        if (slipUri != null) {
                            Text(
                                "✓ แนบสลิปแล้ว ระบบจะเก็บสลิปแบบ private ให้เฉพาะคู่ซื้อขายและแอดมินเปิดดูได้",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1E7A45)
                            )
                        }
                    }
                }
            }

            viewModel.orderMessage?.let {
                Text(it, color = if (it.contains("แล้ว")) Color(0xFF1E7A45) else MaterialTheme.colorScheme.error)
            }

            Surface(color = Color(0xFFFFF0CC), shape = RoundedCornerShape(14.dp)) {
                Text(
                    when (paymentMethod) {
                        "cod" -> "คำแนะนำระบบ: เก็บเงินปลายทางช่วยลดความเสี่ยงจากการโอนก่อน แต่ควรตรวจสินค้าและเงื่อนไขขนส่งกับผู้ขายให้ชัดเจน"
                        "bank_transfer" -> "คำแนะนำระบบ: ก่อนโอนควรแชทยืนยันกับผู้ขายว่าสินค้ายังอยู่ และตรวจชื่อบัญชีให้ตรงกับบัญชีที่ขึ้นว่า “ยืนยันแล้ว” สลิปเป็นหลักฐานประกอบคำสั่งซื้อ การโอนเงินเป็นการโอนตรงให้ผู้ขาย แอปไม่ได้เป็นตัวกลางรับชำระเงิน "
                        else -> "กรุณาเลือกวิธีชำระเงินที่ระบบรองรับก่อนยืนยันคำสั่งซื้อ"
                    },
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = OrderBrown
                )
            }

            Button(
                onClick = {
                    viewModel.createOrder(
                        listing.id, recipient, phone, house, soi, road, subdistrict, district,
                        province, postalCode, paymentMethod, slipUri, onCreated
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !viewModel.orderBusy &&
                    listing.canBuy &&
                    listing.status == "approved" &&
                    paymentMethod.isNotBlank() &&
                    (paymentMethod != "bank_transfer" || slipUri != null),
                colors = ButtonDefaults.buttonColors(containerColor = OrderBrown)
            ) {
                if (viewModel.orderBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (listing.hasActiveOrder) "มีผู้สั่งซื้อแล้ว" else "ยืนยันคำสั่งซื้อ")
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    sellerMode: Boolean,
    viewModel: AppViewModel,
    onOpen: (Int) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(sellerMode) {
        if (sellerMode) viewModel.loadReceivedOrders() else viewModel.loadMyOrders()
    }
    val state = if (sellerMode) viewModel.receivedOrdersState else viewModel.myOrdersState

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (sellerMode) "คำสั่งซื้อที่ได้รับ" else "คำสั่งซื้อของฉัน") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "ย้อนกลับ") } }
            )
        }
    ) { padding ->
        when (state) {
            UiState.Idle, UiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(padding)) { ErrorBox(state.message) {
                if (sellerMode) viewModel.loadReceivedOrders() else viewModel.loadMyOrders()
            } }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text(if (sellerMode) "ยังไม่มีคำสั่งซื้อที่ได้รับ" else "คุณยังไม่มีคำสั่งซื้อ")
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().background(OrderCream).padding(padding),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.data, key = { it.orderId }) { order ->
                            OrderCard(order, sellerMode) { onOpen(order.orderId) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: Order, sellerMode: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = order.coverUrl,
                contentDescription = order.title,
                modifier = Modifier.size(76.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingBag, null, Modifier.size(16.dp), tint = OrderGold)
                    Spacer(Modifier.width(5.dp))
                    Text("Order #${order.orderId}", style = MaterialTheme.typography.labelMedium, color = OrderBrown)
                }
                Text(order.title, fontWeight = FontWeight.Bold, maxLines = 2)
                Text("฿${formatPrice(order.price)}", fontWeight = FontWeight.ExtraBold, color = OrderBrown)
                Text(
                    if (sellerMode) "ผู้ซื้อ: ${order.buyerUsername}" else "ผู้ขาย: ${order.sellerUsername}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(order.statusLabel, color = statusColor(order.status), fontWeight = FontWeight.Bold)
                order.paymentMethodLabel?.takeIf { it.isNotBlank() }?.let {
                    Text("ชำระเงิน: $it", style = MaterialTheme.typography.bodySmall)
                }
                order.trackingNumber?.takeIf { it.isNotBlank() }?.let {
                    Text("พัสดุ: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFF17679A))
                }
                Text(order.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: Int,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onChat: (Int, Int) -> Unit
) {
    LaunchedEffect(orderId) { viewModel.loadOrder(orderId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order #$orderId") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "ย้อนกลับ") } }
            )
        }
    ) { padding ->
        when (val state = viewModel.orderDetailState) {
            UiState.Idle, UiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(padding)) { ErrorBox(state.message) { viewModel.loadOrder(orderId) } }
            is UiState.Success -> {
                val order = state.data
                // Server is the source of truth for the participant role. The app
                // never decides seller/buyer privileges from a navigation flag.
                val isSeller = order.viewerRole == "seller"
                val isBuyer = order.viewerRole == "buyer"
                var tracking by rememberSaveable(order.orderId) { mutableStateOf(order.trackingNumber.orEmpty()) }

                Column(
                    Modifier.fillMaxSize().background(OrderCream).padding(padding)
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = if (order.status == "cancelled") MaterialTheme.colorScheme.errorContainer else Color(0xFFFFEAC0),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(order.statusLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = statusColor(order.status))
                            Text("สั่งซื้อเมื่อ ${order.createdAt}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = order.coverUrl,
                                contentDescription = order.title,
                                modifier = Modifier.size(82.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(order.title, fontWeight = FontWeight.Bold)
                                Text("฿${formatPrice(order.price)}", color = OrderBrown, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    Surface(
                        color = if (order.sellerVerified) Color(0xFFE8F3EC) else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (order.sellerVerified) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, null, tint = Color(0xFF1E7A45))
                                    Spacer(Modifier.width(7.dp))
                                    Text("ผู้ขายยืนยันตัวตนแล้ว", fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E7A45))
                                }
                                Text("ธนาคาร: ${order.sellerBankName.orEmpty()}")
                                Text("ชื่อบัญชี: ${order.sellerAccountName.orEmpty()}")
                                Text("เลขบัญชี: ${order.sellerAccountNumber.orEmpty()}", fontWeight = FontWeight.Bold)
                                Text("ข้อมูลนี้เป็น Snapshot ตอนสร้าง Order", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Text(
                                    "ผู้ขายรายนี้ยังไม่ได้ยืนยันตัวตน ณ ตอนสร้าง Order",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                if (order.sellerRatingCount > 0) "คะแนนผู้ขาย ★ ${String.format("%.1f", order.sellerRatingAverage)} • ${order.sellerRatingCount} รีวิว"
                                else "ผู้ขายยังไม่มีคะแนน",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("การชำระเงิน", fontWeight = FontWeight.ExtraBold)
                            Text(order.paymentMethodLabel ?: "คำสั่งซื้อเดิม")
                            if (order.paymentMethod == "bank_transfer" && order.hasPaymentSlip) {
                                OutlinedButton(
                                    onClick = { viewModel.loadOrderSlip(order.orderId) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("ดูสลิปการชำระเงิน")
                                }
                            } else if (order.paymentMethod == "cod") {
                                Text(
                                    "ชำระกับผู้ให้บริการขนส่งเมื่อรับสินค้า",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF1E7A45)
                                )
                            }
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("ข้อมูลจัดส่ง", fontWeight = FontWeight.ExtraBold)
                            Text("${order.recipientName}  •  ${order.phone}")
                            Text(buildAddress(order))
                            order.note?.takeIf { it.isNotBlank() }?.let { Text("หมายเหตุ: $it") }
                        }
                    }

                    if (!order.trackingNumber.isNullOrBlank()) {
                        Surface(color = Color(0xFFE8F3EC), shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalShipping, null, tint = Color(0xFF1E7A45))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("เลขพัสดุ", style = MaterialTheme.typography.bodySmall)
                                    Text(order.trackingNumber, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { onChat(order.listingId, order.buyerId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Chat, null)
                        Spacer(Modifier.width(8.dp))
                        Text("แชทกับ${if (isSeller) "ผู้ซื้อ" else if (isBuyer) "ผู้ขาย" else "คู่สนทนา"}")
                    }

                    viewModel.orderMessage?.let { message ->
                        Surface(color = Color(0xFFE8F3EC), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = viewModel::clearOrderMessage) { Text("ปิด") }
                            }
                        }
                    }

                    if (!isSeller && !isBuyer) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "ไม่สามารถยืนยันสิทธิ์ของคำสั่งซื้อนี้จาก Server ได้ กรุณารีเฟรชหลังอัปเดต Backend",
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    if (isSeller && order.status == "pending_confirmation") {
                        Button(
                            onClick = { viewModel.updateOrder(order.orderId, "confirm") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.orderBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = OrderBrown)
                        ) { Text("ยืนยันคำสั่งซื้อ") }
                        OutlinedButton(
                            onClick = { viewModel.updateOrder(order.orderId, "reject") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.orderBusy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("ปฏิเสธ / ยกเลิก") }
                    }

                    if (isSeller && order.status == "preparing") {
                        OutlinedTextField(
                            value = tracking,
                            onValueChange = { tracking = it.take(120) },
                            label = { Text("เลขพัสดุ") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = { viewModel.updateOrder(order.orderId, "ship", tracking) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.orderBusy && tracking.trim().length >= 3,
                            colors = ButtonDefaults.buttonColors(containerColor = OrderBrown)
                        ) { Text("จัดส่งแล้ว") }
                    }

                    if (isBuyer && order.status == "pending_confirmation") {
                        OutlinedButton(
                            onClick = { viewModel.updateOrder(order.orderId, "cancel") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.orderBusy
                        ) { Text("ยกเลิกคำสั่งซื้อ") }
                    }

                    if (isBuyer && order.status == "shipped") {
                        Button(
                            onClick = { viewModel.updateOrder(order.orderId, "received") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.orderBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E6A40))
                        ) { Text("ได้รับสินค้าแล้ว") }
                    }

                    if (isBuyer && order.status == "completed") {
                        if (order.reviewRating != null) {
                            Surface(color = Color(0xFFFFF0CC), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text("คะแนนที่คุณให้ผู้ขาย", fontWeight = FontWeight.ExtraBold)
                                    Text("★".repeat(order.reviewRating.coerceIn(1, 5)) + "  ${order.reviewRating}/5")
                                    order.reviewText?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        } else if (order.canRate) {
                            var selectedRating by rememberSaveable(order.orderId) { mutableIntStateOf(5) }
                            var reviewText by rememberSaveable(order.orderId) { mutableStateOf("") }
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("ให้คะแนนผู้ขาย", fontWeight = FontWeight.ExtraBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        (1..5).forEach { star ->
                                            IconButton(onClick = { selectedRating = star }) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = "$star ดาว",
                                                    tint = if (star <= selectedRating) OrderGold else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = reviewText,
                                        onValueChange = { reviewText = it.take(500) },
                                        label = { Text("รีวิวสั้น ๆ (ไม่บังคับ)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 3
                                    )
                                    Button(
                                        onClick = { viewModel.submitRating(order.orderId, selectedRating, reviewText) },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !viewModel.ratingBusy
                                    ) { Text("ส่งคะแนน") }
                                }
                            }
                        }
                        viewModel.ratingMessage?.let { message ->
                            Text(message, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }

    when (val slipState = viewModel.orderSlipState) {
        UiState.Loading -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("กำลังเปิดสลิป") },
            text = { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        )
        is UiState.Success -> AlertDialog(
            onDismissRequest = viewModel::clearOrderSlip,
            confirmButton = { TextButton(onClick = viewModel::clearOrderSlip) { Text("ปิด") } },
            title = { Text("สลิปการชำระเงิน") },
            text = {
                AsyncImage(
                    model = slipState.data,
                    contentDescription = "สลิปการชำระเงิน",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 520.dp),
                    contentScale = ContentScale.Fit
                )
            }
        )
        is UiState.Error -> AlertDialog(
            onDismissRequest = viewModel::clearOrderSlip,
            confirmButton = { TextButton(onClick = viewModel::clearOrderSlip) { Text("ปิด") } },
            title = { Text("เปิดสลิปไม่ได้") },
            text = { Text(slipState.message) }
        )
        UiState.Idle -> Unit
    }
}

private fun buildAddress(order: Order): String = buildString {
    append(order.houseNoMoo)
    order.soi?.takeIf { it.isNotBlank() }?.let { append(" ซอย ").append(it) }
    order.road?.takeIf { it.isNotBlank() }?.let { append(" ถนน ").append(it) }
    append(" ").append(order.subdistrict)
    append(" ").append(order.district)
    append(" ").append(order.province)
    append(" ").append(order.postalCode)
}

private fun statusColor(status: String): Color = when (status) {
    "pending_confirmation" -> Color(0xFF9A6714)
    "preparing" -> Color(0xFF7B4A1D)
    "shipped" -> Color(0xFF17679A)
    "completed" -> Color(0xFF1E7A45)
    "cancelled" -> Color(0xFFB3261E)
    else -> OrderBrown
}
