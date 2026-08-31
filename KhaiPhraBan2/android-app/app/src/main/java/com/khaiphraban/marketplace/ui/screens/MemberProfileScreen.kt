package com.khaiphraban.marketplace.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khaiphraban.marketplace.ui.components.ListingListRow
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberProfileScreen(
    userId: Int,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenListing: (Int) -> Unit
) {
    LaunchedEffect(userId) { viewModel.loadMemberProfile(userId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("สมาชิก") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "ย้อนกลับ") } }
            )
        }
    ) { padding ->
        when (val state = viewModel.memberProfileState) {
            UiState.Idle, UiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(padding)) {
                ErrorBox(state.message) { viewModel.loadMemberProfile(userId) }
            }
            is UiState.Success -> {
                val profile = state.data
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAF2))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountCircle, null, Modifier.size(62.dp), tint = Color(0xFF6D3B16))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            profile.specialIcon?.takeIf { it.isNotBlank() }?.let {
                                                Text("$it ", style = MaterialTheme.typography.headlineSmall)
                                            }
                                            Text(
                                                profile.displayName.ifBlank { profile.username },
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            if (profile.isAdmin) {
                                                Spacer(Modifier.width(7.dp))
                                                Surface(
                                                    color = Color(0xFF4B2A12),
                                                    contentColor = Color(0xFFFFD98A),
                                                    shape = RoundedCornerShape(999.dp)
                                                ) {
                                                    Text("✓ ผู้ดูแลระบบ", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                                                }
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (profile.isVerified) {
                                                Icon(Icons.Default.Verified, null, Modifier.size(17.dp), tint = Color(0xFF1E7A45))
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(
                                                profile.verificationLabel,
                                                color = if (profile.isVerified) Color(0xFF1E7A45) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        if (profile.adminStars > 0) {
                                            Text(
                                                "ดาวจากแอดมิน ${"★".repeat(profile.adminStars.coerceIn(0, 5))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF8A5A00),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, null, Modifier.size(17.dp), tint = Color(0xFFD9972D))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (profile.ratingCount > 0) "${String.format("%.1f", profile.ratingAverage)} • ${profile.ratingCount} รีวิว"
                                                else "ยังไม่มีคะแนน",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                                profile.memberSince?.takeIf { it.isNotBlank() }?.let {
                                    Text("สมาชิกตั้งแต่ ${it.take(10)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    item {
                        Text("ประกาศของสมาชิก", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    }
                    if (profile.listings.isEmpty()) {
                        item { Text("ยังไม่มีประกาศที่แสดงได้", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(profile.listings, key = { it.id }) { listing ->
                            ListingListRow(listing, Modifier.fillMaxWidth()) { onOpenListing(listing.id) }
                        }
                    }
                }
            }
        }
    }
}
