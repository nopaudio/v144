package com.khaiphraban.marketplace.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.khaiphraban.marketplace.data.model.*
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState
import java.text.NumberFormat
import java.util.Locale

private val AdminBrown = Color(0xFF4B2A12)
private val AdminGold = Color(0xFFD9A441)
private val AdminCream = Color(0xFFFFFAF2)
private val AdminGreen = Color(0xFF167A43)
private val AdminRed = Color(0xFFB3261E)
private val AdminOrange = Color(0xFFB86B13)

private fun statusColor(status: String): Color = when (status.lowercase()) {
    "approved", "verified", "resolved", "completed", "active" -> AdminGreen
    "rejected", "dismissed", "cancelled", "suspended", "hidden" -> AdminRed
    "pending", "pending_confirmation", "open", "reviewing", "preparing" -> AdminOrange
    else -> AdminBrown
}

@Composable
private fun AdminOnly(viewModel: AppViewModel, content: @Composable () -> Unit) {
    val session by viewModel.session.collectAsState()
    if (!session.isLoggedIn || !session.isAdmin) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, null)
                    Spacer(Modifier.height(8.dp))
                    Text("สำหรับผู้ดูแลระบบเท่านั้น", fontWeight = FontWeight.Bold)
                    Text(
                        "สิทธิ์จริงจะถูกตรวจซ้ำจาก Server ทุกครั้งที่เรียก Admin API",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    } else content()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = Color(0xFFF6F2EC),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "ย้อนกลับ") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AdminBrown,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        content = content
    )
}

@Composable
private fun AdminMessage(viewModel: AppViewModel) {
    viewModel.adminMessage?.let { message ->
        Surface(
            color = if (message.contains("แล้ว") || message.contains("สำเร็จ"))
                Color(0xFFE7F6E9) else MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::clearAdminMessage) { Text("ปิด") }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String, label: String = status) {
    Surface(
        color = statusColor(status).copy(alpha = 0.12f),
        contentColor = statusColor(status),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptyAdminState(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(42.dp), tint = AdminGreen)
        Spacer(Modifier.height(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdminError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("ลองใหม่") }
    }
}

@Composable
private fun EvidenceDialog(viewModel: AppViewModel, title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {
            viewModel.clearAdminEvidence()
            onDismiss()
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.clearAdminEvidence()
                onDismiss()
            }) { Text("ปิด") }
        },
        title = { Text(title) },
        text = {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 560.dp),
                contentAlignment = Alignment.Center
            ) {
                when (val state = viewModel.adminEvidenceState) {
                    UiState.Idle, UiState.Loading -> CircularProgressIndicator()
                    is UiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    is UiState.Success -> {
                        val bitmap = remember(state.data) {
                            BitmapFactory.decodeByteArray(state.data, 0, state.data.size)?.asImageBitmap()
                        }
                        if (bitmap == null) {
                            Text("ไม่สามารถแสดงไฟล์หลักฐานนี้ได้")
                        } else {
                            Image(
                                bitmap = bitmap,
                                contentDescription = title,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit
) = AdminOnly(viewModel) {
    LaunchedEffect(Unit) {
        viewModel.loadAdminDashboard()
        viewModel.loadAdminNotifications(silent = true)
    }

    AdminPage(
        title = "ผู้ดูแลระบบ",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.loadAdminDashboard() }) {
                Icon(Icons.Default.Refresh, "รีเฟรช")
            }
        }
    ) { padding ->
        when (val state = viewModel.adminDashboardState) {
            UiState.Idle, UiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            is UiState.Error -> Column(Modifier.fillMaxSize().padding(padding)) {
                AdminError(state.message) { viewModel.loadAdminDashboard() }
            }
            is UiState.Success -> {
                val d = state.data
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AdminBrown),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AdminPanelSettings, null, tint = AdminGold, modifier = Modifier.size(34.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("ศูนย์งานผู้ดูแล", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                                        Text("รายการที่ต้องจัดการจากข้อมูลจริงบน Server", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (d.pendingTotal > 0) {
                                    Spacer(Modifier.height(12.dp))
                                    Text("งานรอรวม ${d.pendingTotal} รายการ", color = AdminGold, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    item { AdminMessage(viewModel) }
                    item {
                        AdminMetricGrid(
                            listOf(
                                AdminMetric("แจ้งเตือน", d.unreadNotifications, "ยังไม่ได้อ่าน", Icons.Default.Notifications, "admin/notifications"),
                                AdminMetric("เติมแต้ม", d.pendingTopups, "รออนุมัติ", Icons.Default.AccountBalanceWallet, "admin/topups"),
                                AdminMetric("ยืนยันตัวตน", d.pendingVerifications, "รอตรวจ", Icons.Default.VerifiedUser, "admin/verifications"),
                                AdminMetric("ประกาศ", d.pendingListings, "รออนุมัติ", Icons.Default.Inventory2, "admin/listings"),
                                AdminMetric("รายงาน", d.openReports, "รายการใหม่", Icons.Default.Report, "admin/reports"),
                                AdminMetric("สมาชิก", d.users, "บัญชี", Icons.Default.People, "admin/users")
                            ),
                            onOpen
                        )
                    }
                    item {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocalShipping, null, tint = AdminBrown)
                                    Spacer(Modifier.width(8.dp))
                                    Text("คำสั่งซื้อ", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    Text("${d.activeOrders} กำลังดำเนินการ")
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "V9 ไม่มีสถานะ Order ที่ต้องให้ Admin อนุมัติ จึงแสดงเพื่อตรวจสอบเท่านั้นและไม่สร้างงานแจ้งเตือนเกินจริง",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { onOpen("admin/orders") }) { Text("ดูคำสั่งซื้อ") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class AdminMetric(
    val title: String,
    val count: Int,
    val suffix: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)

@Composable
private fun AdminMetricGrid(metrics: List<AdminMetric>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { metric ->
                    Card(
                        onClick = { onOpen(metric.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Icon(metric.icon, null, tint = AdminBrown)
                            Spacer(Modifier.height(9.dp))
                            Text(metric.count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = AdminBrown)
                            Text(metric.title, fontWeight = FontWeight.Bold)
                            Text(metric.suffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun AdminNotificationsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit
) = AdminOnly(viewModel) {
    LaunchedEffect(Unit) { viewModel.loadAdminNotifications() }
    AdminPage(
        title = "แจ้งเตือนผู้ดูแล",
        onBack = onBack,
        actions = {
            TextButton(
                onClick = viewModel::markAllAdminNotificationsRead,
                enabled = !viewModel.adminBusy
            ) { Text("อ่านทั้งหมด", color = Color.White) }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { AdminMessage(viewModel) }
            when (val state = viewModel.adminNotificationsState) {
                UiState.Idle, UiState.Loading -> item {
                    Box(Modifier.fillParentMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is UiState.Error -> item { AdminError(state.message) { viewModel.loadAdminNotifications() } }
                is UiState.Success -> {
                    if (state.data.isEmpty()) item { EmptyAdminState("ยังไม่มีแจ้งเตือน") }
                    items(state.data, key = { it.id }) { n ->
                        Card(
                            onClick = {
                                val target = n.mobileRoute?.takeIf { it.startsWith("admin/") } ?: "admin"
                                viewModel.markAdminNotificationRead(n.id) { onOpen(target) }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (n.isRead) Color.White else Color(0xFFFFF1D2)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                                BadgedBox(
                                    badge = { if (!n.isRead) Badge() }
                                ) {
                                    Icon(Icons.Default.Notifications, null, tint = AdminBrown)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(n.title, fontWeight = FontWeight.ExtraBold)
                                    Text(n.message, style = MaterialTheme.typography.bodyMedium)
                                    n.relatedUsername?.let {
                                        Text("สมาชิก: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(n.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminTopupsScreen(viewModel: AppViewModel, onBack: () -> Unit) = AdminOnly(viewModel) {
    var evidenceTitle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.loadAdminTopups() }
    AdminPage("เติมแต้มรออนุมัติ", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { AdminMessage(viewModel) }
            when (val state = viewModel.adminTopupsState) {
                UiState.Idle, UiState.Loading -> item {
                    Box(Modifier.fillParentMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is UiState.Error -> item { AdminError(state.message) { viewModel.loadAdminTopups() } }
                is UiState.Success -> {
                    if (state.data.isEmpty()) item { EmptyAdminState("ไม่มีคำขอเติมแต้มรอตรวจ") }
                    items(state.data, key = { it.id }) { item ->
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("#${item.id} ${item.username}", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
                                    StatusChip(item.status)
                                }
                                Text("ยอดโอน ${formatMoney(item.amount)} บาท • ${item.points} แต้ม")
                                item.note?.takeIf { it.isNotBlank() }?.let { Text("หมายเหตุ: $it", style = MaterialTheme.typography.bodySmall) }
                                Text(item.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.hasSlip) {
                                    OutlinedButton(onClick = {
                                        evidenceTitle = "สลิป #${item.id}"
                                        viewModel.loadAdminEvidence("topup", id = item.id)
                                    }) {
                                        Icon(Icons.Default.ReceiptLong, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("ดูสลิป")
                                    }
                                }
                                if (item.status == "pending") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.reviewAdminTopup(item.id, "approved") },
                                            enabled = !viewModel.adminBusy,
                                            colors = ButtonDefaults.buttonColors(containerColor = AdminGreen),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("อนุมัติ") }
                                        Button(
                                            onClick = { viewModel.reviewAdminTopup(item.id, "rejected") },
                                            enabled = !viewModel.adminBusy,
                                            colors = ButtonDefaults.buttonColors(containerColor = AdminRed),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("ปฏิเสธ") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    evidenceTitle?.let { title ->
        EvidenceDialog(viewModel, title) { evidenceTitle = null }
    }
}

@Composable
fun AdminVerificationsScreen(viewModel: AppViewModel, onBack: () -> Unit) = AdminOnly(viewModel) {
    var evidenceTitle by remember { mutableStateOf<String?>(null) }
    var rejectUser by remember { mutableStateOf<AdminVerificationItem?>(null) }
    LaunchedEffect(Unit) { viewModel.loadAdminVerifications() }
    AdminPage("ยืนยันตัวตนรอตรวจ", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { AdminMessage(viewModel) }
            when (val state = viewModel.adminVerificationsState) {
                UiState.Idle, UiState.Loading -> item {
                    Box(Modifier.fillParentMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is UiState.Error -> item { AdminError(state.message) { viewModel.loadAdminVerifications() } }
                is UiState.Success -> {
                    if (state.data.isEmpty()) item { EmptyAdminState("ไม่มีคำขอยืนยันตัวตนรอตรวจ") }
                    items(state.data, key = { it.userId }) { item ->
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.username, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
                                    StatusChip(item.status)
                                }
                                Text("${item.bankName} • ${item.accountName}")
                                Text("เลขบัญชี ${item.accountNumber}", style = MaterialTheme.typography.bodySmall)
                                Text("ส่งเมื่อ ${item.submittedAt}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.hasDocument) {
                                    OutlinedButton(onClick = {
                                        evidenceTitle = "หลักฐานยืนยันตัวตน • ${item.username}"
                                        viewModel.loadAdminEvidence("identity", userId = item.userId)
                                    }) {
                                        Icon(Icons.Default.Badge, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("ดูหลักฐาน")
                                    }
                                }
                                if (item.status == "pending") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.reviewAdminVerification(item.userId, "approved") },
                                            enabled = !viewModel.adminBusy,
                                            colors = ButtonDefaults.buttonColors(containerColor = AdminGreen),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("อนุมัติ") }
                                        Button(
                                            onClick = { rejectUser = item },
                                            enabled = !viewModel.adminBusy,
                                            colors = ButtonDefaults.buttonColors(containerColor = AdminRed),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("ปฏิเสธ") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    evidenceTitle?.let { title ->
        EvidenceDialog(viewModel, title) { evidenceTitle = null }
    }
    rejectUser?.let { item ->
        ReasonDialog(
            title = "เหตุผลที่ไม่ผ่าน",
            confirmLabel = "ยืนยันปฏิเสธ",
            onDismiss = { rejectUser = null },
            onConfirm = { reason ->
                viewModel.reviewAdminVerification(item.userId, "rejected", reason)
                rejectUser = null
            }
        )
    }
}

@Composable
private fun ReasonDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { if (it.length <= 500) reason = it },
                label = { Text("ระบุเหตุผลอย่างน้อย 3 ตัวอักษร") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = AdminRed)
            ) { Text(confirmLabel) }
        }
    )
}

@Composable
fun AdminListingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenListing: (Int) -> Unit
) = AdminOnly(viewModel) {
    LaunchedEffect(Unit) { viewModel.loadAdminListings() }
    AdminPage("ประกาศรออนุมัติ", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { AdminMessage(viewModel) }
            when (val state = viewModel.adminListingsState) {
                UiState.Idle, UiState.Loading -> item {
                    Box(Modifier.fillParentMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is UiState.Error -> item { AdminError(state.message) { viewModel.loadAdminListings() } }
                is UiState.Success -> {
                    if (state.data.isEmpty()) item { EmptyAdminState("ไม่มีประกาศรออนุมัติ") }
                    items(state.data, key = { it.id }) { item ->
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                                AsyncImage(
                                    model = item.coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(88.dp).background(Color(0xFFE8E0D5), RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(item.title, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                                    Text("${formatMoney(item.price)} บาท", color = AdminBrown, fontWeight = FontWeight.Bold)
                                    Text("${item.username} • ${item.province}", style = MaterialTheme.typography.bodySmall)
                                    StatusChip(item.status)
                                    TextButton(onClick = { onOpenListing(item.id) }, contentPadding = PaddingValues(0.dp)) {
                                        Text("ดูรายละเอียด")
                                    }
                                }
                            }
                            if (item.status == "pending") {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.updateAdminListing(item.id, "approved") },
                                        enabled = !viewModel.adminBusy,
                                        colors = ButtonDefaults.buttonColors(containerColor = AdminGreen),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("อนุมัติ") }
                                    Button(
                                        onClick = { viewModel.updateAdminListing(item.id, "rejected") },
                                        enabled = !viewModel.adminBusy,
                                        colors = ButtonDefaults.buttonColors(containerColor = AdminRed),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("ปฏิเสธ") }
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
fun AdminReportsScreen(viewModel: AppViewModel, onBack: () -> Unit) = AdminOnly(viewModel) {
    var noteTarget by remember { mutableStateOf<AdminReportItem?>(null) }
    LaunchedEffect(Unit) { viewModel.loadAdminReports() }
    AdminPage("รายงานใหม่", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { AdminMessage(viewModel) }
            when (val state = viewModel.adminReportsState) {
                UiState.Idle, UiState.Loading -> item {
                    Box(Modifier.fillParentMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is UiState.Error -> item { AdminError(state.message) { viewModel.loadAdminReports() } }
                is UiState.Success -> {
                    if (state.data.isEmpty()) item { EmptyAdminState("ไม่มีรายงานใหม่") }
                    items(state.data, key = { it.id }) { r ->
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("#${r.id} ${r.category}", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
                                    StatusChip(r.status)
                                }
                                Text(r.details)
                                Text("ผู้แจ้ง: ${r.reporterName}", style = MaterialTheme.typography.bodySmall)
                                r.reportedName?.let { Text("ถูกรายงาน: $it", style = MaterialTheme.typography.bodySmall) }
                                r.listingTitle?.let { Text("ประกาศ: $it", style = MaterialTheme.typography.bodySmall) }
                                if (r.status == "open" || r.status == "reviewing") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (r.status == "open") {
                                            OutlinedButton(
                                                onClick = { viewModel.updateAdminReport(r.id, "reviewing") },
                                                enabled = !viewModel.adminBusy
                                            ) { Text("รับเรื่อง") }
                                        }
                                        Button(
                                            onClick = { noteTarget = r },
                                            enabled = !viewModel.adminBusy,
                                            colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                                        ) { Text("แก้ไขแล้ว") }
                                        TextButton(
                                            onClick = { viewModel.updateAdminReport(r.id, "dismiss") },
                                            enabled = !viewModel.adminBusy
                                        ) { Text("ยกเลิกเรื่อง", color = AdminRed) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    noteTarget?.let { report ->
        var note by remember(report.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text("ปิดรายงาน #${report.id}") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 1000) note = it },
                    label = { Text("บันทึกผู้ดูแล (ไม่บังคับ)") },
                    minLines = 3
                )
            },
            dismissButton = { TextButton(onClick = { noteTarget = null }) { Text("ยกเลิก") } },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAdminReport(report.id, "resolve", note)
                        noteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                ) { Text("บันทึกว่าแก้ไขแล้ว") }
            }
        )
    }
}

@Composable
fun AdminOrdersScreen(viewModel: AppViewModel, onBack: () -> Unit) = AdminOnly(viewModel) {
    LaunchedEffect(Unit) { viewModel.loadAdminOrders() }
    AdminPage("คำสั่งซื้อ • ตรวจสอบ", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(color = Color(0xFFFFF1D2), shape = RoundedCornerShape(14.dp)) {
                    Text(
                        "V9 ไม่มี Admin approval สำหรับ Order หน้านี้จึงเป็นแบบอ่านอย่างเดียว เพื่อไม่รื้อ flow ผู้ซื้อ/ผู้ขายเดิม",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            when (val state = viewModel.adminOrdersState) {
                UiState.Idle, UiState.Loading -> item {
                    Box(Modifier.fillParentMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is UiState.Error -> item { AdminError(state.message) { viewModel.loadAdminOrders() } }
                is UiState.Success -> {
                    if (state.data.isEmpty()) item { EmptyAdminState("ยังไม่มีคำสั่งซื้อ") }
                    items(state.data, key = { it.orderId }) { o ->
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("#${o.orderId} ${o.title}", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
                                    StatusChip(o.status)
                                }
                                Text("${formatMoney(o.price)} บาท")
                                Text("ผู้ซื้อ ${o.buyerUsername} • ผู้ขาย ${o.sellerUsername}", style = MaterialTheme.typography.bodySmall)
                                Text("ผู้รับ ${o.recipientName} • ${o.province} ${o.postalCode}", style = MaterialTheme.typography.bodySmall)
                                o.trackingNumber?.takeIf { it.isNotBlank() }?.let {
                                    Text("เลขพัสดุ $it", style = MaterialTheme.typography.bodySmall)
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
fun AdminUsersScreen(viewModel: AppViewModel, onBack: () -> Unit) = AdminOnly(viewModel) {
    var query by remember { mutableStateOf("") }
    var editingUser by remember { mutableStateOf<AdminUserItem?>(null) }
    LaunchedEffect(Unit) { viewModel.loadAdminUsers() }

    AdminPage("สมาชิก", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("ค้นหาชื่อ Login / ชื่อที่แสดง / อีเมล") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.loadAdminUsers(query) }) {
                            Icon(Icons.Default.ArrowForward, "ค้นหา")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.loadAdminUsers(query) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { AdminMessage(viewModel) }
            when (val state = viewModel.adminUsersState) {
                UiState.Idle, UiState.Loading -> item {
                    Box(Modifier.fillParentMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is UiState.Error -> item { AdminError(state.message) { viewModel.loadAdminUsers(query) } }
                is UiState.Success -> {
                    if (state.data.isEmpty()) item { EmptyAdminState("ไม่พบสมาชิก") }
                    items(state.data, key = { it.id }) { u ->
                        Card(
                            onClick = { editingUser = u },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = RoundedCornerShape(12.dp), color = AdminBrown.copy(alpha = 0.1f)) {
                                    Icon(Icons.Default.Person, null, Modifier.padding(10.dp), tint = AdminBrown)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        u.specialIcon?.takeIf { it.isNotBlank() }?.let { Text("$it ") }
                                        Text(u.displayName.ifBlank { u.username }, fontWeight = FontWeight.ExtraBold)
                                        if (u.isAdmin) {
                                            Spacer(Modifier.width(6.dp))
                                            StatusChip("approved", "ADMIN")
                                        }
                                    }
                                    if (u.displayName.isNotBlank() && u.displayName != u.username) {
                                        Text("Login: ${u.username}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(u.email, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${u.listingCount} ประกาศ • ${u.pointsBalance} แต้ม • #${u.id}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (u.adminStars > 0) {
                                        Text(
                                            "ดาวแอดมิน ${"★".repeat(u.adminStars.coerceIn(0, 5))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF8A5A00),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    u.pendingDisplayName?.let {
                                        Text(
                                            "มีคำขอชื่อใหม่: $it",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AdminOrange,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    StatusChip(u.status)
                                    Icon(Icons.Default.Edit, "แก้ไขสมาชิก", tint = AdminBrown)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingUser?.let { user ->
        AdminUserEditDialog(
            user = user,
            busy = viewModel.adminBusy,
            onDismiss = { if (!viewModel.adminBusy) editingUser = null },
            onSave = { displayName, stars, icon, pointsDelta, role, status ->
                viewModel.updateAdminUser(
                    user.id, displayName, stars, icon, pointsDelta, role, status
                )
                editingUser = null
            },
            onReviewName = { requestId, decision, note ->
                viewModel.reviewAdminDisplayName(requestId, decision, note)
                editingUser = null
            }
        )
    }
}

@Composable
private fun AdminUserEditDialog(
    user: AdminUserItem,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, Int, String, String) -> Unit,
    onReviewName: (Int, String, String) -> Unit
) {
    var displayName by remember(user.id) { mutableStateOf(user.displayName) }
    var stars by remember(user.id) { mutableIntStateOf(user.adminStars.coerceIn(0, 5)) }
    var specialIcon by remember(user.id) { mutableStateOf(user.specialIcon.orEmpty()) }
    var pointsDelta by remember(user.id) { mutableStateOf("") }
    var role by remember(user.id) { mutableStateOf(user.role) }
    var status by remember(user.id) { mutableStateOf(user.status) }
    var adminNote by remember(user.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("แก้ไขสมาชิก #${user.id}") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Login: ${user.username}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(40) },
                    label = { Text("ชื่อที่แสดง / ชื่อไทย") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = specialIcon,
                    onValueChange = { specialIcon = it.take(16) },
                    label = { Text("ไอคอนพิเศษ เช่น 🏆") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("ดาวจากแอดมิน: $stars / 5", fontWeight = FontWeight.Bold)
                Slider(
                    value = stars.toFloat(),
                    onValueChange = { stars = it.toInt().coerceIn(0, 5) },
                    valueRange = 0f..5f,
                    steps = 4
                )

                OutlinedTextField(
                    value = pointsDelta,
                    onValueChange = { raw ->
                        pointsDelta = raw.filterIndexed { index, ch -> ch.isDigit() || (ch == '-' && index == 0) }.take(8)
                    },
                    label = { Text("เพิ่ม/ลดแต้ม เช่น 100 หรือ -50") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("ยอดปัจจุบัน ${user.pointsBalance} แต้ม", style = MaterialTheme.typography.labelSmall)

                Text("สิทธิ์", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = role == "member", onClick = { role = "member" }, label = { Text("สมาชิก") })
                    FilterChip(selected = role == "admin", onClick = { role = "admin" }, label = { Text("แอดมิน") })
                }

                Text("สถานะบัญชี", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = status == "active", onClick = { status = "active" }, label = { Text("ใช้งาน") })
                    FilterChip(selected = status == "suspended", onClick = { status = "suspended" }, label = { Text("ระงับ") })
                }

                user.pendingDisplayNameRequestId?.let { requestId ->
                    HorizontalDivider()
                    Text("คำขอเปลี่ยนชื่อ", fontWeight = FontWeight.ExtraBold, color = AdminOrange)
                    Text("ขอเป็น: ${user.pendingDisplayName.orEmpty()}", fontWeight = FontWeight.Bold)
                    Text(
                        "เหตุผล: ${user.pendingDisplayNameReason.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = adminNote,
                        onValueChange = { adminNote = it.take(500) },
                        label = { Text("หมายเหตุแอดมิน (ไม่บังคับ)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onReviewName(requestId, "approved", adminNote) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                        ) { Text("อนุมัติชื่อ") }
                        OutlinedButton(
                            onClick = { onReviewName(requestId, "rejected", adminNote) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AdminRed)
                        ) { Text("ปฏิเสธ") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        displayName,
                        stars,
                        specialIcon,
                        pointsDelta.toIntOrNull() ?: 0,
                        role,
                        status
                    )
                },
                enabled = !busy
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("บันทึก")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("ยกเลิก") }
        }
    )
}

private fun formatMoney(value: Double): String =
    NumberFormat.getNumberInstance(Locale("th", "TH")).apply { maximumFractionDigits = 2 }.format(value)
