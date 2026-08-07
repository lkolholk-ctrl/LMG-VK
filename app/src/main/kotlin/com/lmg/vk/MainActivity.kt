package com.lmg.vk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.NotificationRouter
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.PlayerSettings
import com.lmg.vk.engine.SecurityUtils
import com.lmg.vk.engine.VkLinkResolver
import com.lmg.vk.engine.automix.JuceContextHolder
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.logging.CrashHandler
import com.lmg.vk.ui.AppRoot
import com.lmg.vk.ui.PerfMonitor
import com.lmg.vk.ui.crash.CrashActivity
import com.lmg.vk.ui.theme.LiquidMusicGlassTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Точка входа UI. Восстановленная точка входа UI:
 * security-блок сохранён, но отключён флагом [PROTECTION_ENABLED]
 * (наши проверки целостности живут в liblmg — см. lmg_native.cpp).
 */
class MainActivity : ComponentActivity() {

    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // POST_NOTIFICATIONS (Android 13+) — рантайм-разрешение.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // JUCE инициализируется через Activity-контекст.
        JuceContextHolder.set(this)

        // Краш-лог предыдущей сессии (java_crash/native Fishnet-дамп) — показать
        // экран краша ДО остальной инициализации.
        if (CrashHandler.hasCrashLog(this)) {
            startActivity(Intent(this, CrashActivity::class.java))
            finish()
            return
        }

        // Разрешение на уведомления — на первом запуске (иначе не видно медиа-уведомление).
        maybeRequestNotificationPermission()

        enableEdgeToEdge()

        // Детектор просадки FPS → деградация тяжёлых эффектов на слабом GPU.
        PerfMonitor.start()

        // Сессия VK: прогрев в фоне (таймаут 5с — старт не висит на сети).
        authScope.launch {
            if (MusicAuth.isLoggedIn.value) {
                kotlinx.coroutines.withTimeoutOrNull(5_000) {
                    MusicAuth.fetchUserData()
                }
            }
        }

        // Тап по уведомлению плеера.
        handleNotificationTap(intent)

        // Ссылка ВКонтакте из браузера/мессенджера (ACTION_VIEW).
        handleVkLink(intent)

        val isSecurityCompromised = mutableStateOf(false)
        val compromiseReason = mutableStateOf("")

        // Security checks: Root/Emulator. Нативные проверки (Frida/Xposed)
        // — на стороне liblmg; здесь оставлен только лёгкий Java-уровень.
        if (PROTECTION_ENABLED) authScope.launch {
            val isRooted = SecurityUtils.isDeviceRooted()
            val isEmulator = SecurityUtils.isEmulator()
            if (isRooted || isEmulator) {
                val reasons = mutableListOf<String>()
                if (isRooted) reasons.add("Root Check Triggered")
                if (isEmulator) reasons.add("Emulator Check Triggered")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    compromiseReason.value = reasons.joinToString("\n")
                    isSecurityCompromised.value = true
                }
            }
        }

        setContent {
            val themeMode by PlayerController.themeMode.collectAsState()
            val highContrast by PlayerSettings.increaseContrast.collectAsState()
            LiquidMusicGlassTheme(themeMode = themeMode, highContrast = highContrast) {
                val compromised by remember { isSecurityCompromised }
                val reasons by remember { compromiseReason }
                if (compromised) {
                    SecurityBlockScreen(reasons) { finishAffinity() }
                } else {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Activity объявлена singleTask: при живой задаче ссылка приходит СЮДА,
        // а не в onCreate — без этой ветки VK-ссылки работали бы только на
        // холодном старте.
        setIntent(intent)
        handleNotificationTap(intent)
        handleVkLink(intent)
    }

    /** Тап по медиа-уведомлению → раскрыть большой плеер. */
    private fun handleNotificationTap(intent: Intent?) {
        if (intent?.getStringExtra("NAVIGATE_TO") == "LARGE_PLAYER") {
            PlayerController.audioServiceRef?.let {
                NotificationRouter.emitOpenLargePlayer()
            }
        }
    }

    /**
     * Входящая ссылка ВКонтакте. Разбор и `utils.resolveScreenName` — сетевые,
     * поэтому уходим в [authScope] (IO): держать на них главный поток нельзя.
     *
     * Intent помечаем обработанным: при повороте/пересоздании Activity система
     * отдаёт ТОТ ЖЕ intent в onCreate, и без флага ссылка отрабатывала бы заново,
     * уводя пользователя с текущего экрана.
     */
    private fun handleVkLink(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return
        if (intent.getBooleanExtra(EXTRA_VK_LINK_HANDLED, false)) return
        val uri = intent.data ?: return
        if (!VkLinkResolver.isVkLink(uri)) return
        intent.putExtra(EXTRA_VK_LINK_HANDLED, true)
        authScope.launch { VkLinkResolver.handle(this@MainActivity, uri) }
    }

    /**
     * Ровно один запрос POST_NOTIFICATIONS (флаг в prefs): без него холодный старт
     * либо спамил бы диалогом, либо впустую дёргал систему на «навсегда отклонено».
     */
    private fun maybeRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val prefs = getSharedPreferences("permissions", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("post_notif_requested", false)) return
        try {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            prefs.edit().putBoolean("post_notif_requested", true).apply()
        } catch (_: Throwable) {
            // Отдельные прошивки могут кинуть на launch — не роняем старт.
        }
    }

    companion object {
        /** Метка «эта ссылка уже отработана» — живёт внутри самого Intent. */
        private const val EXTRA_VK_LINK_HANDLED = "com.lmg.vk.VK_LINK_HANDLED"

        /**
         * Java-уровень защиты (Root/Emulator). Проверка подписи и целостности APK
         * отключена для восстановленных сборок, подписанных владельцем проекта.
         * При необходимости отдельно включается только Root/Emulator-защита.
         */
        private const val PROTECTION_ENABLED = false
    }
}

/** Экран блокировки при сработке security-проверки. */
@Composable
private fun SecurityBlockScreen(reasons: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D0B0F), Color(0xFF15121B), Color(0xFF09070A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0x1F2C243B))
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        colors = listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SECURITY INTEGRITY BLOCK",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF453A),
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "This application is restricted from running in debugged, rooted, or unsafe environments.",
                fontSize = 13.sp,
                color = Color(0xFFD1D1D6),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x2B000000))
                    .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "Integrity violations detected:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFF9500),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    reasons.split("\n").forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFFFF453A))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = reason,
                                fontSize = 12.sp,
                                color = Color(0xFFE5E5EA)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF3B30),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
