package com.khaiphraban.marketplace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.khaiphraban.marketplace.notifications.NotificationHelper
import com.khaiphraban.marketplace.ui.MarketplaceApp
import com.khaiphraban.marketplace.ui.theme.KhaiPhraBanTheme
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    private var notificationRoute by mutableStateOf<String?>(null)
    private var notificationRouteVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationRoute = routeFromIntent(intent)
        if (notificationRoute != null) notificationRouteVersion++

        // Keep announcements fresh while the app is foregrounded.
        // Presence/online-count polling is intentionally not used by the user UI.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    appViewModel.loadAnnouncements()
                    delay(60_000)
                }
            }
        }

        setContent {
            KhaiPhraBanTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* แอปยังใช้งานได้แม้ผู้ใช้ไม่อนุญาต */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!granted) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                MarketplaceApp(
                    viewModel = appViewModel,
                    externalRoute = notificationRoute,
                    externalRouteVersion = notificationRouteVersion
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationRoute = routeFromIntent(intent)
        if (notificationRoute != null) notificationRouteVersion++
    }

    private fun routeFromIntent(intent: Intent?): String? {
        val adminRoute = intent?.getStringExtra(NotificationHelper.EXTRA_ADMIN_ROUTE)
        if (adminRoute == "admin" || adminRoute?.startsWith("admin/") == true) {
            return adminRoute
        }
        val orderId = intent?.getIntExtra(NotificationHelper.EXTRA_ORDER_ID, 0) ?: 0
        if (orderId > 0) {
            return "order/$orderId"
        }
        val listingId = intent?.getIntExtra(NotificationHelper.EXTRA_LISTING_ID, 0) ?: 0
        val buyerId = intent?.getIntExtra(NotificationHelper.EXTRA_BUYER_ID, 0) ?: 0
        if (listingId > 0 && buyerId > 0) {
            return "chat/$listingId/$buyerId"
        }
        return if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_CHATS, false) == true) {
            "chats"
        } else {
            null
        }
    }
}
