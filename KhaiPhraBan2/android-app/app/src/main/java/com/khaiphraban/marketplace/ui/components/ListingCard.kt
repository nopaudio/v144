package com.khaiphraban.marketplace.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.khaiphraban.marketplace.data.model.Listing
import java.text.NumberFormat
import java.util.Locale

private val PremiumGold = Color(0xFFD79B21)
private val PremiumBrown = Color(0xFF5C3214)

fun formatPrice(value: Double): String = NumberFormat.getNumberInstance(Locale("th", "TH")).format(value)

@Composable
fun ListingCard(listing: Listing, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (listing.isPremium) 7.dp else 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = listing.images.firstOrNull()?.url,
                    contentDescription = listing.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.25f),
                    contentScale = ContentScale.Crop
                )
                if (listing.isPremium) {
                    Surface(
                        modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                        color = PremiumGold,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PremiumPulseStar(14.dp)
                            Spacer(Modifier.width(3.dp))
                            Text("พรีเมียม", style = MaterialTheme.typography.labelSmall, color = PremiumBrown, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (listing.status == "sold" || listing.hasActiveOrder) {
                    Surface(
                        modifier = Modifier.padding(8.dp).align(Alignment.TopEnd),
                        color = if (listing.status == "sold") Color(0xFF5E2E23) else Color(0xFF7B4A1D),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            if (listing.status == "sold") "ขายแล้ว" else "มีผู้สั่งซื้อแล้ว",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Column(
                Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    listing.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "฿${formatPrice(listing.price)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${listing.tambon} • ${listing.province}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                listing.boostedAt?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrendingUp, null, Modifier.size(12.dp), tint = PremiumBrown)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "ดันล่าสุด $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumBrown,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ListingListRow(listing: Listing, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (listing.isPremium) 5.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(88.dp)) {
                AsyncImage(
                    model = listing.images.firstOrNull()?.url,
                    contentDescription = listing.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (listing.isPremium) {
                    Surface(
                        modifier = Modifier.padding(4.dp).align(Alignment.TopStart),
                        color = PremiumGold,
                        shape = RoundedCornerShape(50)
                    ) {
                        Box(Modifier.padding(3.dp)) { PremiumPulseStar(13.dp) }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listing.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "฿${formatPrice(listing.price)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${listing.tambon} • ${listing.amphoe} • ${listing.province}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (listing.isPremium || listing.boostedAt != null) {
                    Text(
                        when {
                            listing.isPremium && listing.boostedAt != null -> "พรีเมียม • ดันล่าสุด ${listing.boostedAt}"
                            listing.isPremium -> "โพสต์พรีเมียม"
                            else -> "ดันล่าสุด ${listing.boostedAt}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumBrown,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


@Composable
private fun PremiumPulseStar(size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "premium-star")
    val alpha by transition.animateFloat(
        initialValue = .45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "premium-alpha"
    )
    val scale by transition.animateFloat(
        initialValue = .9f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "premium-scale"
    )
    Icon(
        Icons.Default.Star,
        contentDescription = "พรีเมียม",
        modifier = Modifier.size(size).alpha(alpha).scale(scale),
        tint = PremiumBrown
    )
}

@Composable
fun StatusPill(status: String) {
    val label = when (status) {
        "pending" -> "รอตรวจ"
        "approved" -> "เผยแพร่"
        "hidden" -> "ซ่อน"
        "rejected" -> "ไม่อนุมัติ"
        "sold" -> "ขายแล้ว"
        else -> status
    }
    AssistChip(onClick = {}, label = { Text(label) })
}
