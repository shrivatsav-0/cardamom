package com.shrivatsav.cardamom

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = when (vm.theme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.DARK -> true
                AppTheme.LIGHT -> false
            }

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                    navigationBarStyle = if (isDark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                )
                onDispose {}
            }

            val secure = vm.screenSecurity || vm.locked
            DisposableEffect(secure) {
                if (secure) {
                    window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose {}
            }

            CardamomTheme(isDark = isDark) {
                Surface(Modifier.fillMaxSize(), color = C.Bg, contentColor = C.Cream) {
                    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) { AppRoot(vm) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        vm.onBackground()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val start = remember { if (!vm.onboarded) "w1" else "wallet" }
    val idArg = listOf(navArgument("id") { type = NavType.StringType })
    val ctx = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(vm.locked) {
        if (!vm.locked && vm.onboarded) {
            val act = ctx.findActivity()
            val cardId = act?.intent?.getStringExtra("open_card_id")
            if (!cardId.isNullOrBlank()) {
                act.intent.removeExtra("open_card_id")
                nav.navigate("detail/$cardId")
            } else if (act?.intent?.getStringExtra("shortcut") == "scan") {
                act.intent.removeExtra("shortcut")
                nav.navigate("scan")
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (vm.locked) {
            UnlockScreen(vm)
        } else {
            NavHost(nav, startDestination = start, modifier = Modifier.fillMaxSize()) {
                composable("w1") { WelcomeScreen(onNext = { nav.navigate("w2") }) }
                composable("w2") { ScanIntroScreen(onBack = { nav.popBackStack() }, onNext = { nav.navigate("w3") }) }
                composable("w3") {
                    UsernameScreen(vm, onBack = { nav.popBackStack() }, onDone = {
                        nav.navigate("wallet") { popUpTo("w1") { inclusive = true } }
                    })
                }
                composable("wallet") {
                    WalletScreen(
                        vm = vm,
                        onAddCard = { nav.navigate("scan") },
                        onAddDocument = { nav.navigate("scan_doc") },
                        onOpenCard = { nav.navigate("detail/$it") },
                        onOpenDoc = { nav.navigate("doc_detail/$it") },
                        onSettings = { nav.navigate("settings") },
                    )
                }
                composable("scan") {
                    ScanScreen(vm, isDocument = false, onBack = { nav.popBackStack() }, onCode = { nav.navigate("edit/new") }, onImported = { nav.navigate("review") })
                }
                composable(
                    "scan?doc={doc}",
                    arguments = listOf(navArgument("doc") { type = NavType.BoolType; defaultValue = false }),
                ) { e ->
                    val isDoc = e.arguments?.getBoolean("doc") ?: false
                    ScanScreen(
                        vm = vm,
                        isDocument = isDoc,
                        onBack = { nav.popBackStack() },
                        onCode = { nav.navigate(if (isDoc) "doc_upload" else "edit/new") },
                        onImported = { nav.navigate(if (isDoc) "doc_upload" else "review") },
                    )
                }
                composable("review") { ReviewScreen(vm, onBack = { nav.popBackStack() }, onNext = { nav.navigate("edit/new") }) }
                composable("edit/{id}", arguments = idArg) { e ->
                    val routeId = e.arguments?.getString("id") ?: "new"
                    EditScreen(
                        vm = vm,
                        id = routeId,
                        onBack = { nav.popBackStack() },
                        onSaved = { savedId ->
                            if (routeId == "new") {
                                nav.navigate("detail/$savedId") {
                                    popUpTo("wallet")
                                }
                            } else {
                                nav.popBackStack()
                            }
                        },
                    )
                }
                composable("detail/{id}", arguments = idArg) { e ->
                    val id = e.arguments?.getString("id") ?: ""
                    DetailScreen(vm, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("edit/$id") }, onShare = { nav.navigate("share/$id") })
                }
                composable("share/{id}", arguments = idArg) { e ->
                    ShareScreen(vm, e.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
                }
                composable("scan_doc") {
                    DocumentScanScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onScanWithCamera = { nav.navigate("scan?doc=true") },
                        onScanned = {
                            nav.navigate("doc_upload")
                        },
                    )
                }
                composable("doc_upload") {
                    DocumentUploadScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onUploaded = { id ->
                            nav.navigate("doc_detail/$id") {
                                popUpTo("wallet")
                            }
                        },
                    )
                }
                composable("doc_detail/{id}", arguments = idArg) { e ->
                    val id = e.arguments?.getString("id") ?: ""
                    DocumentDetailScreen(
                        vm = vm,
                        id = id,
                        onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate("doc_edit/$id") },
                    )
                }
                composable("doc_edit/{id}", arguments = idArg) { e ->
                    val id = e.arguments?.getString("id") ?: ""
                    DocumentEditScreen(
                        vm = vm,
                        id = id,
                        onBack = { nav.popBackStack() },
                        onSaved = { nav.popBackStack() },
                    )
                }
                composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }, onBackup = { nav.navigate("backup") }) }
                composable("backup") { BackupScreen(vm, onBack = { nav.popBackStack() }) }
            }
        }
        vm.busyText?.let { BusyOverlay(it) }
    }
}
