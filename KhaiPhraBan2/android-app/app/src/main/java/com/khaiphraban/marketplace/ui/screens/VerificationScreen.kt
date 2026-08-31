package com.khaiphraban.marketplace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    var bankName by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var documentUri by remember { mutableStateOf<Uri?>(null) }
    var initializedFor by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) documentUri = uri
    }

    LaunchedEffect(Unit) { viewModel.loadMyProfile() }

    val profile = (viewModel.myProfileState as? UiState.Success)?.data
    val verification = profile?.verification
    LaunchedEffect(verification?.submittedAt, verification?.status) {
        val key = "${verification?.submittedAt}:${verification?.status}"
        if (verification != null && initializedFor != key) {
            bankName = verification.bankName
            accountName = verification.accountName
            accountNumber = verification.accountNumber
            initializedFor = key
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ยืนยันตัวตน") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "ย้อนกลับ")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            verification?.statusLabel ?: "ยังไม่ยืนยัน",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        "กรอกบัญชีรับเงินของคุณและแนบรูปถ่ายสมาชิกคู่กับสมุดบัญชีธนาคาร แอดมินจะตรวจและอนุมัติด้วยตนเอง",
                        style = MaterialTheme.typography.bodySmall
                    )
                    verification?.rejectionReason?.takeIf { it.isNotBlank() }?.let {
                        Text("เหตุผลที่ไม่ผ่าน: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (verification?.status == "verified") {
                        Text(
                            "หากแก้ธนาคาร ชื่อบัญชี หรือเลขบัญชี แล้วส่งใหม่ สถานะยืนยันจะกลับเป็น “รอตรวจสอบ”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it.take(120) },
                label = { Text("ธนาคาร *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it.take(160) },
                label = { Text("ชื่อบัญชี *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = accountNumber,
                onValueChange = { accountNumber = it.take(80) },
                label = { Text("เลขบัญชีรับเงิน *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("หลักฐาน: รูปถ่ายสมาชิกคู่กับสมุดบัญชีธนาคาร", fontWeight = FontWeight.Bold)
            documentUri?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "รูปหลักฐานที่เลือก",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp),
                    contentScale = ContentScale.Fit
                )
            }
            OutlinedButton(
                onClick = { picker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (documentUri == null) "เลือกรูปหลักฐาน" else "เปลี่ยนรูปหลักฐาน")
            }
            Text(
                "เพื่อความปลอดภัย รูปนี้ไม่ใช่ URL สาธารณะ และจะใช้สำหรับเจ้าหน้าที่ตรวจสอบเท่านั้น",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            viewModel.verificationMessage?.let { message ->
                Text(
                    message,
                    color = if (message.contains("ส่งข้อมูลแล้ว")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    documentUri?.let { uri ->
                        viewModel.submitVerification(bankName, accountName, accountNumber, uri)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !viewModel.verificationBusy &&
                    bankName.trim().length >= 2 &&
                    accountName.trim().length >= 2 &&
                    accountNumber.trim().length >= 5 &&
                    documentUri != null
            ) {
                if (viewModel.verificationBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("ส่งให้แอดมินตรวจสอบ")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
