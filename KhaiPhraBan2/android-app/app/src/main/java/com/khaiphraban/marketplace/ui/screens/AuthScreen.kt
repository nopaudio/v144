package com.khaiphraban.marketplace.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState
import com.khaiphraban.marketplace.util.ThaiAddressData
import com.khaiphraban.marketplace.util.ThaiAddressRow

@Composable
fun AuthScreen(viewModel: AppViewModel, onSuccess: () -> Unit) {
    var registerMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var lineId by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var amphoe by remember { mutableStateOf("") }
    var tambon by remember { mutableStateOf("") }
    var addresses by remember { mutableStateOf(emptyList<ThaiAddressRow>()) }
    var addressError by remember { mutableStateOf<String?>(null) }
    val loading = viewModel.authState is UiState.Loading

    LaunchedEffect(registerMode) {
        if (registerMode && addresses.isEmpty()) {
            addressError = null
            runCatching { ThaiAddressData.load() }
                .onSuccess { addresses = it }
                .onFailure { addressError = "โหลดรายชื่อพื้นที่ไม่สำเร็จ กรุณาลองใหม่" }
        }
    }

    val provinces = remember(addresses) { addresses.map { it.province }.distinct() }
    val districts = remember(addresses, province) {
        addresses.filter { it.province == province }.map { it.district }.distinct()
    }
    val subdistricts = remember(addresses, province, amphoe) {
        addresses.filter { it.province == province && it.district == amphoe }
            .map { it.subdistrict }
            .distinct()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (registerMode) "สมัครสมาชิก" else "เข้าสู่ระบบ", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(username, { username = it }, label = { Text("ชื่อผู้ใช้") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (registerMode) {
            OutlinedTextField(email, { email = it }, label = { Text("อีเมล") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        OutlinedTextField(
            password, { password = it }, label = { Text("รหัสผ่าน (อย่างน้อย 8 ตัว)") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        if (registerMode) {
            OutlinedTextField(phone, { phone = it }, label = { Text("เบอร์โทร (ไม่บังคับ)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(lineId, { lineId = it }, label = { Text("LINE ID (ไม่บังคับ)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text(
                "ที่อยู่ผู้ขาย",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "เลือกจังหวัด อำเภอ และตำบลครั้งเดียว ระบบจะนำไปใช้ตอนลงประกาศให้อัตโนมัติ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AddressDropdown("จังหวัด", province, provinces, enabled = addresses.isNotEmpty()) {
                province = it
                amphoe = ""
                tambon = ""
            }
            AddressDropdown("อำเภอ / เขต", amphoe, districts, enabled = province.isNotBlank()) {
                amphoe = it
                tambon = ""
            }
            AddressDropdown("ตำบล / แขวง", tambon, subdistricts, enabled = amphoe.isNotBlank()) {
                tambon = it
            }
            addressError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        (viewModel.authState as? UiState.Error)?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                viewModel.clearAuthMessage()
                if (registerMode) {
                    viewModel.register(
                        username, email, password, phone, lineId,
                        province, amphoe, tambon, onSuccess
                    )
                } else {
                    viewModel.login(username, password, onSuccess)
                }
            },
            modifier = Modifier.fillMaxWidth(), enabled = !loading
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(if (registerMode) "สมัครและเข้าสู่ระบบ" else "เข้าสู่ระบบ")
        }
        TextButton(onClick = { registerMode = !registerMode; viewModel.clearAuthMessage() }) {
            Text(if (registerMode) "มีบัญชีแล้ว — เข้าสู่ระบบ" else "ยังไม่มีบัญชี — สมัครสมาชิก")
        }
    }
}
