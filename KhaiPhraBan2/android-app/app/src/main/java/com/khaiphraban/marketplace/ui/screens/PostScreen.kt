package com.khaiphraban.marketplace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState
import com.khaiphraban.marketplace.util.ThaiAddressData

private val TITLE_SUGGESTIONS = listOf(
    "หลวงพ่อ", "เหรียญ", "ปิดตา", "สมเด็จ",
    "พระกริ่ง", "พระผง", "พระเนื้อดิน", "พระเนื้อชิน"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(viewModel: AppViewModel, onSuccess: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var amphoe by remember { mutableStateOf("") }
    var tambon by remember { mutableStateOf("") }
    var allowMeetup by remember { mutableStateOf(false) }
    var allowBuyNow by remember { mutableStateOf(true) }
    var allowCod by remember { mutableStateOf(false) }
    var chatFirst by remember { mutableStateOf(true) }
    var captchaAnswer by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var addresses by remember { mutableStateOf(emptyList<com.khaiphraban.marketplace.util.ThaiAddressRow>()) }
    var addressError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { picked ->
        images = picked.take(5)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCaptcha()
        viewModel.loadMyProfile(silent = true)
        runCatching { ThaiAddressData.load() }
            .onSuccess { addresses = it }
            .onFailure { addressError = "โหลดรายชื่อพื้นที่ไม่สำเร็จ กรุณาลองใหม่" }
    }

    val profile = (viewModel.myProfileState as? UiState.Success)?.data
    val hasSavedAddress = !profile?.province.isNullOrBlank() &&
        !profile?.amphoe.isNullOrBlank() &&
        !profile?.tambon.isNullOrBlank()

    LaunchedEffect(profile?.province, profile?.amphoe, profile?.tambon) {
        if (hasSavedAddress) {
            province = profile?.province.orEmpty()
            amphoe = profile?.amphoe.orEmpty()
            tambon = profile?.tambon.orEmpty()
        }
    }

    val loading = viewModel.postState is UiState.Loading
    val provinces = remember(addresses) { addresses.map { it.province }.distinct() }
    val districts = remember(addresses, province) {
        addresses.filter { it.province == province }.map { it.district }.distinct()
    }
    val subdistricts = remember(addresses, province, amphoe) {
        addresses
            .filter { it.province == province && it.district == amphoe }
            .map { it.subdistrict }
            .distinct()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ลงประกาศขายพระ", style = MaterialTheme.typography.headlineSmall)
        Text(
            "กรอกข้อมูลสั้น ๆ ระบบจะใช้ที่อยู่ผู้ขายจากบัญชี และเพิ่มรูปให้เห็นรายละเอียดพระชัดเจน",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(160) },
            label = { Text("หัวข้อประกาศ") },
            supportingText = { Text("${title.length}/160") },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "เลือกคำขึ้นต้นได้:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TITLE_SUGGESTIONS) { suggestion ->
                AssistChip(
                    onClick = {
                        val current = title.trim()
                        val oldPrefix = TITLE_SUGGESTIONS.firstOrNull {
                            current == it || current.startsWith("$it ")
                        }
                        val rest = oldPrefix
                            ?.let { current.removePrefix(it).trimStart() }
                            ?: current
                        title = if (rest.isBlank()) {
                            "$suggestion "
                        } else {
                            "$suggestion $rest"
                        }.take(160)
                    },
                    label = { Text(suggestion) }
                )
            }
        }

        OutlinedTextField(
            description,
            { description = it },
            label = { Text("รายละเอียด / ตำหนิ / ประวัติ") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("ตัวเลือกการซื้อ / รับสินค้า", style = MaterialTheme.typography.titleSmall)
                Text(
                    "เลือกได้มากกว่า 1 รายการ และผู้ซื้อจะเห็นตัวเลือกเหล่านี้หน้าโพสต์",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ListingOptionCheckbox(
                    checked = allowMeetup,
                    text = "นัดรับได้",
                    onCheckedChange = { allowMeetup = it }
                )
                ListingOptionCheckbox(
                    checked = allowBuyNow,
                    text = "สั่งซื้อได้",
                    onCheckedChange = {
                        allowBuyNow = it
                        if (!it) allowCod = false
                    }
                )
                ListingOptionCheckbox(
                    checked = allowCod,
                    text = "สั่งซื้อเก็บเงินปลายทางได้",
                    onCheckedChange = {
                        allowCod = it
                        if (it) allowBuyNow = true
                    }
                )
                ListingOptionCheckbox(
                    checked = chatFirst,
                    text = "คุยกันก่อนในแชท — ทักมาเลย",
                    onCheckedChange = { chatFirst = it }
                )
            }
        }

        OutlinedTextField(
            price,
            { price = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("ราคา (บาท)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        if (hasSavedAddress) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("ที่อยู่ผู้ขายจากบัญชี", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "$tambon • $amphoe • $province",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "V13 ใช้ที่อยู่ที่สมัครสมาชิกให้อัตโนมัติ ไม่ต้องกรอกซ้ำตอนโพสต์",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                "บัญชีนี้สร้างก่อน V13 จึงยังไม่มีที่อยู่ กรุณาเลือกครั้งนี้ครั้งเดียว ระบบจะบันทึกไว้ใช้กับโพสต์ถัดไป",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
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

        OutlinedButton(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AddPhotoAlternate, null)
            Spacer(Modifier.width(8.dp))
            Text("เพิ่มรูปสินค้า สูงสุด 5 รูป")
        }

        Text(
            "เลือกรูปพระที่ชัดเจนได้สูงสุด 5 รูป แอปจะย่อรูปให้อัตโนมัติก่อนอัปโหลด",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (images.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images) { uri ->
                    AsyncImage(
                        uri,
                        null,
                        Modifier.size(100.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        when (val captcha = viewModel.captchaState) {
            UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is UiState.Success -> {
                Text("ป้องกันสแปม: ${captcha.data.question}")
                OutlinedTextField(
                    captchaAnswer,
                    { captchaAnswer = it.filter(Char::isDigit) },
                    label = { Text("คำตอบ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { viewModel.refreshCaptcha() }) {
                    Text("เปลี่ยนคำถาม")
                }
            }
            is UiState.Error -> TextButton(onClick = { viewModel.refreshCaptcha() }) {
                Text("โหลดคำถามใหม่: ${captcha.message}")
            }
            UiState.Idle -> Unit
        }

        (viewModel.postState as? UiState.Error)?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                viewModel.createListing(
                    title,
                    description,
                    price,
                    province,
                    amphoe,
                    tambon,
                    allowMeetup,
                    allowBuyNow,
                    allowCod,
                    chatFirst,
                    captchaAnswer,
                    images,
                    onSuccess
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text("ส่งประกาศให้แอดมินตรวจ")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ListingOptionCheckbox(
    checked: Boolean,
    text: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
