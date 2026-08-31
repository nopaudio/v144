package com.khaiphraban.marketplace.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.khaiphraban.marketplace.ui.screens.*
import com.khaiphraban.marketplace.ui.viewmodel.AppViewModel
import com.khaiphraban.marketplace.ui.viewmodel.UiState
import kotlinx.coroutines.delay

data class BottomDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceApp(viewModel: AppViewModel, startDestination: String = "home", externalRoute: String? = null, externalRouteVersion: Int = 0) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route
    val session by viewModel.session.collectAsState()
    val bottoms = listOf(
        BottomDestination("home", "หน้าแรก", Icons.Default.Home),
        BottomDestination("post", "ลงประกาศ", Icons.Default.AddCircle),
        BottomDestination("premium", "พรีเมียม", Icons.Default.Star),
        BottomDestination("chats", "แชท", Icons.Default.Chat),
        BottomDestination("my", "ของฉัน", Icons.Default.AccountCircle)
    )

    LaunchedEffect(externalRoute, externalRouteVersion) {
        val target = externalRoute ?: return@LaunchedEffect
        navController.navigate(target) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(session.token, session.role) {
        if (!session.isLoggedIn) return@LaunchedEffect

        // Re-read the authenticated profile so the locally cached Admin badge/menu
        // follows a role change made on the server. Authorization never relies on
        // this cache; every Admin API still performs its own server-side role check.
        viewModel.loadMyProfile(silent = true)

        while (true) {
            viewModel.loadChatUnreadCount()
            if (session.isAdmin) viewModel.loadAdminDashboard(silent = true)
            delay(15_000)
        }
    }

    Scaffold(
        topBar = {
            val immersiveRoute = route?.startsWith("detail/") == true ||
                route?.startsWith("checkout/") == true ||
                route?.startsWith("order/") == true ||
                route?.startsWith("orders/") == true ||
                route?.startsWith("member/") == true ||
                route?.startsWith("admin") == true ||
                route == "verification"
            if (!immersiveRoute) {
                TopAppBar(
                    title = { Text("ตลาดพระออนไลน์") },
                    actions = {
                        if (session.isAdmin) {
                            val adminUnread = (viewModel.adminDashboardState as? UiState.Success)
                                ?.data?.unreadNotifications ?: 0
                            IconButton(onClick = { navController.navigate("admin") { launchSingleTop = true } }) {
                                if (adminUnread > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(if (adminUnread > 99) "99+" else adminUnread.toString())
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.AdminPanelSettings, contentDescription = "ผู้ดูแลระบบ")
                                    }
                                } else {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = "ผู้ดูแลระบบ")
                                }
                            }
                        }
                        if (session.isLoggedIn) {
                            TextButton(onClick = { viewModel.logout(); navController.navigate("home") }) {
                                Text("ออกจากระบบ")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (route?.startsWith("detail") != true &&
                route?.startsWith("checkout") != true &&
                route?.startsWith("order/") != true &&
                route?.startsWith("orders/") != true &&
                route?.startsWith("member/") != true &&
                route?.startsWith("admin") != true &&
                route != "verification" &&
                route != "auth") {
                NavigationBar {
                    bottoms.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                val target = if ((item.route == "post" || item.route == "premium" || item.route == "my" || item.route == "chats") && !session.isLoggedIn) "auth" else item.route
                                navController.navigate(target) {
                                    popUpTo("home") { saveState = false }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            },
                            icon = {
                                if (item.route == "chats" && viewModel.chatUnreadCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(if (viewModel.chatUnreadCount > 99) "99+" else viewModel.chatUnreadCount.toString())
                                            }
                                        }
                                    ) { Icon(item.icon, contentDescription = item.label) }
                                } else {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = startDestination, modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(viewModel, onOpen = { navController.navigate("detail/$it") })
            }
            composable("auth") {
                AuthScreen(viewModel, onSuccess = { navController.navigate("home") { popUpTo("auth") { inclusive = true } } })
            }
            composable("post") {
                PostScreen(viewModel, onSuccess = {
                    viewModel.clearPostMessage()
                    navController.navigate("my") { popUpTo("post") { inclusive = true } }
                })
            }
            composable("premium") {
                PremiumScreen(
                    viewModel = viewModel,
                    onLogin = { navController.navigate("auth") },
                    onOpenListing = { navController.navigate("detail/$it") }
                )
            }
            composable("chats") { ChatInboxScreen(viewModel, onOpen = { listingId, buyerId -> navController.navigate("chat/$listingId/$buyerId") }) }
            composable("chat/{listingId}/{buyerId}", arguments = listOf(navArgument("listingId") { type = NavType.IntType }, navArgument("buyerId") { type = NavType.IntType })) { backStack ->
                ChatScreen(backStack.arguments?.getInt("listingId") ?: 0, backStack.arguments?.getInt("buyerId") ?: 0, viewModel, onBack = { navController.popBackStack() })
            }
            composable("my") {
                MyListingsScreen(
                    viewModel,
                    onOpen = { navController.navigate("detail/$it") },
                    onLogin = { navController.navigate("auth") },
                    onPromote = { navController.navigate("premium") },
                    onMyOrders = { navController.navigate("orders/buying") },
                    onReceivedOrders = { navController.navigate("orders/selling") },
                    onVerification = { navController.navigate("verification") },
                    onLottery = { navController.navigate("lottery") },
                    onAdmin = { navController.navigate("admin") }
                )
            }
            composable("lottery") {
                LotteryScreen(viewModel = viewModel)
            }
            composable("verification") {
                VerificationScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(
                "member/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.IntType })
            ) { backStack ->
                MemberProfileScreen(
                    userId = backStack.arguments?.getInt("userId") ?: 0,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenListing = { navController.navigate("detail/$it") }
                )
            }
            composable(
                "orders/{mode}",
                arguments = listOf(navArgument("mode") { type = NavType.StringType })
            ) { backStack ->
                OrdersScreen(
                    sellerMode = backStack.arguments?.getString("mode") == "selling",
                    viewModel = viewModel,
                    onOpen = { navController.navigate("order/$it") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "order/{orderId}",
                arguments = listOf(navArgument("orderId") { type = NavType.IntType })
            ) { backStack ->
                OrderDetailScreen(
                    orderId = backStack.arguments?.getInt("orderId") ?: 0,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onChat = { listingId, buyerId -> navController.navigate("chat/$listingId/$buyerId") }
                )
            }
            composable(
                "checkout/{listingId}",
                arguments = listOf(navArgument("listingId") { type = NavType.IntType })
            ) { backStack ->
                CheckoutScreen(
                    listingId = backStack.arguments?.getInt("listingId") ?: 0,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onCreated = { orderId ->
                        navController.navigate("order/$orderId") {
                            popUpTo("checkout/${backStack.arguments?.getInt("listingId") ?: 0}") { inclusive = true }
                        }
                    }
                )
            }
            composable("admin") {
                AdminDashboardScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(it) { launchSingleTop = true } }
                )
            }
            composable("admin/notifications") {
                AdminNotificationsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(it) { launchSingleTop = true } }
                )
            }
            composable("admin/topups") {
                AdminTopupsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable("admin/verifications") {
                AdminVerificationsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable("admin/listings") {
                AdminListingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenListing = { navController.navigate("detail/$it") }
                )
            }
            composable("admin/reports") {
                AdminReportsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable("admin/orders") {
                AdminOrdersScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable("admin/users") {
                AdminUsersScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(
                "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { backStack ->
                ListingDetailScreen(
                    id = backStack.arguments?.getInt("id") ?: 0,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onChat = { listingId, buyerId ->
                        navController.navigate("chat/$listingId/$buyerId")
                    },
                    onBuy = { listingId ->
                        if (!session.isLoggedIn) navController.navigate("auth")
                        else navController.navigate("checkout/$listingId")
                    },
                    onLogin = { navController.navigate("auth") },
                    onSellerProfile = { sellerId -> navController.navigate("member/$sellerId") },
                    onListing = { listingId -> navController.navigate("detail/$listingId") }
                )
            }
        }
    }
}
