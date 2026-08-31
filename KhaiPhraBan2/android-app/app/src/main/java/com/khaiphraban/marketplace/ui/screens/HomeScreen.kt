package com.khaiphraban.marketplace.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khaiphraban.marketplace.data.model.HomeBanner
import com.khaiphraban.marketplace.data.model.HomeHero
import com.khaiphraban.marketplace.ui.components.ListingCard
import com.khaiphraban.marketplace.ui.components.ListingListRow
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private val DeepBrown = Color(0xFF3A1F0B)
private val TempleBrown = Color(0xFF6D3B16)
private val AntiqueGold = Color(0xFFD8A33A)
private val PageCream = Color(0xFFFFFAF2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AppViewModel, onOpen: (Int) -> Unit) {
    var listMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (viewModel.homeState is UiState.Success) viewModel.refreshHome()
    }

    when (val state = viewModel.homeState) {
        UiState.Loading, UiState.Idle -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is UiState.Error -> ErrorBox(state.message, viewModel::loadHome)

        is UiState.Success -> {
            val hero = state.data.hero ?: HomeHero()
            val premiumListings = state.data.premium
            val latestListings = state.data.latest
            PullToRefreshBox(
                isRefreshing = viewModel.homeRefreshing,
                onRefresh = viewModel::refreshHome,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(PageCream),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    item {
                        MarketplaceBrandHeader(
                            brand = hero.brandTitle.ifBlank { "ตลาดพระออนไลน์" },
                            subtitle = hero.subheadline.ifBlank { "ซื้อขายง่าย เชื่อถือได้ ทุกการส่งมอบ" },
                            refreshing = viewModel.homeRefreshing,
                            onRefresh = viewModel::refreshHome
                        )
                    }

                    if (hero.enabled && state.data.banners.isNotEmpty()) {
                        item {
                            BannerCarousel(
                                banners = state.data.banners,
                                contentDescription = hero.headline.ifBlank { "ภาพประชาสัมพันธ์" }
                            )
                        }
                    }

                    item {
                        TrustCard(
                            trustTitle = hero.trustTitle,
                            trustText = hero.trustText
                        )
                    }

                    viewModel.homeRefreshError?.let { message ->
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        message,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    TextButton(onClick = viewModel::clearHomeRefreshError) { Text("ปิด") }
                                }
                            }
                        }
                    }

                    item {
                        val dismissedId by viewModel.dismissedAnnouncementId.collectAsState()
                        val latest = viewModel.announcements.firstOrNull()
                        if (latest != null && latest.id != dismissedId) {
                            AnnouncementBanner(
                                title = latest.title,
                                body = latest.body,
                                onDismiss = { viewModel.dismissAnnouncement(latest.id) }
                            )
                        }
                    }

                    item {
                        PremiumHeader()
                    }

                    item {
                        if (premiumListings.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                color = Color(0xFFFFF4D7),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    "ยังไม่มีโพสต์พรีเมียมในขณะนี้",
                                    Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DeepBrown
                                )
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(premiumListings, key = { "premium-${it.id}" }) { listing ->
                                    ListingCard(listing, Modifier.width(220.dp)) { onOpen(listing.id) }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(22.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("พระมาใหม่", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = DeepBrown)
                                Text("ประกาศล่าสุดที่ผ่านการตรวจจากแอดมิน • รายการที่ดันล่าสุดจะแสดงก่อน", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C6A58))
                            }
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = listMode,
                                    onClick = { listMode = true },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    icon = { Icon(Icons.Default.ViewAgenda, null, Modifier.size(16.dp)) },
                                    label = { Text("รายการ") }
                                )
                                SegmentedButton(
                                    selected = !listMode,
                                    onClick = { listMode = false },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    icon = { Icon(Icons.Default.GridView, null, Modifier.size(16.dp)) },
                                    label = { Text("ตาราง") }
                                )
                            }
                        }
                    }

                    if (latestListings.isEmpty()) {
                        item { EmptyText("ยังไม่มีประกาศทั่วไปที่อนุมัติ") }
                    } else if (listMode) {
                        items(latestListings, key = { "general-list-${it.id}" }) { listing ->
                            ListingListRow(
                                listing,
                                Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            ) { onOpen(listing.id) }
                        }
                    } else {
                        items(latestListings.chunked(2), key = { row -> row.joinToString("-") { it.id.toString() } }) { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { listing ->
                                    ListingCard(listing, Modifier.weight(1f)) { onOpen(listing.id) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketplaceBrandHeader(
    brand: String,
    subtitle: String,
    refreshing: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                brand,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF211710)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TempleBrown
            )
        }
        FilledTonalIconButton(onClick = onRefresh, enabled = !refreshing) {
            if (refreshing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "รีเฟรชหน้าแรก")
            }
        }
    }
}

@Composable
private fun TrustCard(trustTitle: String, trustText: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = com.khaiphraban.marketplace.R.drawable.online,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(8.dp))

            Column {
                Text(
                    trustTitle,
                    fontWeight = FontWeight.ExtraBold,
                    color = DeepBrown
                )
                Text(
                    trustText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF766655)
                )
            }
        }
    }
}
@Composable
private fun PulsingPremiumStar() {
    val transition = rememberInfiniteTransition(label = "home-premium-pulse")
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
        tint = DeepBrown,
        modifier = Modifier.size(16.dp).alpha(alpha).scale(scale)
    )
}

@Composable
private fun PremiumHeader() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        color = Color(0xFFD6AD45),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 2.dp
    ) {
        Surface(
            modifier = Modifier
                .padding(1.dp)
                .height(42.dp),
            color = Color(0xFFFFFBF2),
            shape = RoundedCornerShape(13.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {


                Spacer(Modifier.width(10.dp))

                Column {
                    Text(
                        text = "โพสต์พรีเมียม",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF3A2A18)
                    )


                }
            }
        }
    }
}

@Composable
private fun BannerCarousel(
    banners: List<HomeBanner>,
    contentDescription: String
) {
    if (banners.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { banners.size })
    LaunchedEffect(banners.size) {
        if (banners.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5_000)
            val next = (pagerState.currentPage + 1) % banners.size
            pagerState.animateScrollToPage(next)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        ) { page ->
            AsyncImage(
                model = banners[page].imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1821f / 864f),
                contentScale = ContentScale.Crop
            )
        }
        if (banners.size > 1) {
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                banners.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (index == pagerState.currentPage) TempleBrown
                                else Color(0xFFD7C8B8)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnouncementBanner(title: String, body: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        color = Color(0xFFFFF1D6),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Campaign, null, tint = TempleBrown, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = DeepBrown)
                Spacer(Modifier.height(3.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5C4A38))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "ปิด", tint = Color(0xFF9C7B4C))
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) =
    Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun ErrorBox(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = retry) { Text("ลองใหม่") }
    }
}
