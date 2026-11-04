package com.gaifd.carradio.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.gaifd.carradio.data.Favorites
import com.gaifd.carradio.data.History
import com.gaifd.carradio.data.Logos
import com.gaifd.carradio.data.RadioBrowser
import com.gaifd.carradio.data.Stats
import com.gaifd.carradio.data.UpdateState
import com.gaifd.carradio.data.Updater
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.Traffic
import com.gaifd.carradio.data.Station
import com.gaifd.carradio.data.StationRepo
import com.gaifd.carradio.playback.PlaybackService
import com.gaifd.carradio.playback.RadioBus
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTOPLAY = "autoplay"
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)
    private var autoplayRequested = false

    private var batteryOk by mutableStateOf(true)
    private var notificationsOk by mutableStateOf(true)
    private var bluetoothOk by mutableStateOf(true)
    private var carLinked by mutableStateOf(false)
    private var showCarPicker by mutableStateOf(false)
    private var editStreamsId by mutableStateOf<String?>(null)
    private var showDrive by mutableStateOf(false)
    private var songsSegment by mutableIntStateOf(0)
    private var searchQuery by mutableStateOf<String?>(null)
    private var audioOut by mutableStateOf(AudioOut("Динамик телефона", false))
    private var logoStatus by mutableStateOf<String?>(null)
    private var pickLogoFor: String? = null
    private lateinit var audioWatcher: AudioOutWatcher

    private val pickLogoLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = pickLogoFor ?: return@registerForActivityResult
        pickLogoFor = null
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            if (!Logos.fromUri(this@MainActivity, id, uri)) toast("Не удалось открыть картинку")
        }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(StationRepo.exportJson().toByteArray()) }
            toast("Станции сохранены: ${StationRepo.stations.value.size}")
        } catch (e: Exception) {
            toast("Не удалось сохранить: ${e.message}")
        }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val n = StationRepo.import(StationRepo.parseExport(text))
            toast(if (n == 0) "Новых станций в файле нет" else "Добавлено станций: $n")
        } catch (e: Exception) {
            toast("Это не файл станций Авторадио")
        }
    }

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    private val btLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refresh()
        if (ok) showCarPicker = true
        else toast("Без доступа к Bluetooth приложение не узнает, что подключилась машина")
    }
    private val cdmLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        refresh()
        if (carLinked) toast("Машина привязана")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(0xFF0D1215.toInt()),
        )
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        StationRepo.init(this)
        History.init(this)
        Traffic.init(this)
        Logos.init(this)
        Favorites.init(this)
        Stats.init(this)
        audioWatcher = AudioOutWatcher(this) { audioOut = it }
        RadioScreenActions.pickLogo = { id ->
            pickLogoFor = id
            pickLogoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        // Обновления проверяем не чаще раза в 12 часов, тихо.
        if (System.currentTimeMillis() - Prefs.lastUpdateCheck > 12 * 3600_000L) {
            Prefs.lastUpdateCheck = System.currentTimeMillis()
            lifecycleScope.launch { Updater.check() }
        }
        autoplayRequested = intent?.getBooleanExtra(EXTRA_AUTOPLAY, false) == true

        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS) && !Prefs.askedNotifications) {
            Prefs.askedNotifications = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by Prefs.settings.collectAsState()
            val light = isLight(settings.theme)
            LaunchedEffect(light) {
                // Цвет значков в строке состояния под тему.
                if (light) enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
                ) else enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.dark(0xFF0D1215.toInt()),
                )
            }
            CarRadioTheme(light, Accent.base(this, settings)) { AppRoot() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTOPLAY, false)) {
            val c = controller
            if (c != null) c.play() else autoplayRequested = true
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        controllerFuture = f
        audioWatcher.start()
        f.addListener({
            try {
                controller = f.get()
                if (autoplayRequested) {
                    autoplayRequested = false
                    controller?.play()
                }
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @Composable
    private fun isLight(theme: Int): Boolean {
        val systemDark = isSystemInDarkTheme()
        // «По времени»: пересчитываем раз в минуту.
        val hour by produceState(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            while (true) {
                value = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                kotlinx.coroutines.delay(60_000)
            }
        }
        return when (theme) {
            com.gaifd.carradio.data.Settings.THEME_LIGHT -> true
            com.gaifd.carradio.data.Settings.THEME_SYSTEM -> !systemDark
            com.gaifd.carradio.data.Settings.THEME_BY_TIME -> hour in 7..18
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onStop() {
        audioWatcher.stop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onStop()
    }

    // ---------- Управление плеером ----------

    private fun playStation(st: Station) {
        val c = controller ?: return
        val idx = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == st.id } ?: return
        c.seekTo(idx, 0)
        c.play()
    }

    private fun toggle() {
        val c = controller ?: return
        if (c.playWhenReady) c.pause() else c.play()
    }

    // ---------- Система ----------

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        batteryOk = getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: true
        notificationsOk = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        bluetoothOk = Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT)
        carLinked = isCarLinked()
    }

    @SuppressLint("BatteryLife")
    private fun fixBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                openAppSettings()
            }
        }
    }

    private fun fixNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS) &&
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS).not() &&
            !Prefs.askedNotifications
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
        }
    }

    private fun pickCar() {
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            showCarPicker = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun bondedDevices(): List<Pair<String, String>> = try {
        getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
            ?.map { (it.name ?: it.address) to it.address }
            ?.sortedBy { it.first.lowercase() }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun chooseCar(name: String, mac: String) {
        Prefs.update { it.copy(carName = name, carMac = mac) }
        showCarPicker = false
        refresh()
        if (!carLinked) linkCar()
    }

    /**
     * Привязка машины как «сопутствующего устройства». Android показывает системное окно,
     * а после согласия разрешает приложению запускать плеер из фона при подключении машины.
     */
    private fun linkCar() {
        val mac = Prefs.settings.value.carMac ?: return
        val cdm = try {
            getSystemService(CompanionDeviceManager::class.java)
        } catch (_: Exception) {
            null
        }
        if (cdm == null) {
            toast("Телефон не поддерживает привязку — хватит снятого ограничения батареи")
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(mac).build())
            .setSingleDevice(true)
            .build()
        try {
            @Suppress("DEPRECATION")
            cdm.associate(request, object : CompanionDeviceManager.Callback() {
                @Deprecated("Deprecated in Java")
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    cdmLauncher.launch(IntentSenderRequest.Builder(chooserLauncher).build())
                }

                override fun onFailure(error: CharSequence?) {
                    toast("Не получилось привязать: ${error ?: "машина не найдена"}. Включи Bluetooth в машине и попробуй рядом с ней.")
                }
            }, null)
        } catch (e: Exception) {
            toast("Не получилось привязать: ${e.message}")
        }
    }

    private fun isCarLinked(): Boolean {
        val mac = Prefs.settings.value.carMac ?: return false
        return try {
            val cdm = getSystemService(CompanionDeviceManager::class.java) ?: return false
            if (Build.VERSION.SDK_INT >= 33) {
                cdm.myAssociations.any { it.deviceMacAddress?.toString()?.equals(mac, ignoreCase = true) == true }
            } else {
                @Suppress("DEPRECATION")
                cdm.associations.any { it.equals(mac, ignoreCase = true) }
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Нажали на песню — спрашиваем, где искать. */
    private fun searchTrack(track: String) {
        searchQuery = track
    }

    private fun openSearch(service: String, q: String) {
        val enc = Uri.encode(q)
        val tries = when (service) {
            "ytmusic" -> listOf("https://music.youtube.com/search?q=$enc")
            "spotify" -> listOf("spotify:search:$enc", "https://open.spotify.com/search/$enc")
            else -> listOf("https://www.google.com/search?q=$enc")
        }
        for (u in tries) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        toast("Нечем открыть")
    }

    /** Логотипы для станций, у которых их нет: ищем в каталоге по названию. */
    private fun fetchLogos() {
        if (logoStatus?.startsWith("Ищу") == true) return
        lifecycleScope.launch {
            val list = StationRepo.stations.value.filter { !Logos.has(it.id) }
            if (list.isEmpty()) {
                logoStatus = "У всех станций уже есть логотипы"
                return@launch
            }
            var found = 0
            list.forEachIndexed { i, st ->
                logoStatus = "Ищу… ${i + 1} из ${list.size}"
                try {
                    val url = st.logo.ifBlank { RadioBrowser.findLogo(st) ?: "" }
                    if (url.isNotBlank() && Logos.download(st.id, url)) {
                        if (st.logo != url) StationRepo.setLogo(st.id, url)
                        found++
                    }
                } catch (_: Exception) {
                }
            }
            logoStatus = "Нашлось логотипов: $found из ${list.size}" +
                if (found < list.size) ". Остальным можно поставить свою картинку: удерживай плитку." else ""
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ---------- UI ----------

    @Composable
    private fun AppRoot() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val stations by StationRepo.stations.collectAsState()
        val ui by RadioBus.state.collectAsState()
        val settings by Prefs.settings.collectAsState()
        val history by History.items.collectAsState()

        val updateState by Updater.state.collectAsState()
        val updateVersion = (updateState as? UpdateState.Available)?.info?.version

        LaunchedEffect(stations) { Logos.ensure(stations) }
        LaunchedEffect(settings.accent, settings.accentSource) {
            kotlinx.coroutines.delay(400) // пока тянут ползунок — не дёргаем виджет на каждый шаг
            com.gaifd.carradio.playback.RadioWidget.refresh(this@MainActivity)
        }

        if (showDrive) {
            BackHandler { showDrive = false }
            Box(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
                DriveScreen(
                    st = stations.firstOrNull { it.id == ui.stationId } ?: stations.firstOrNull(),
                    ui = ui,
                    onToggle = ::toggle,
                    onPrev = { controller?.seekToPreviousMediaItem() },
                    onNext = { controller?.seekToNextMediaItem() },
                    onClose = { showDrive = false },
                )
            }
            return
        }

        Scaffold(
            containerColor = C.bg,
            bottomBar = { BottomNav(tab) { tab = it } },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> RadioScreen(
                        stations = stations,
                        ui = ui,
                        settings = settings,
                        history = history,
                        batteryOk = batteryOk,
                        onFixBattery = ::fixBattery,
                        onPlay = ::playStation,
                        onToggle = ::toggle,
                        onPrev = { controller?.seekToPreviousMediaItem() },
                        onNext = { controller?.seekToNextMediaItem() },
                        onGoAdd = { tab = 1 },
                        onEditStreams = { editStreamsId = it.id },
                        onOpenHistory = { songsSegment = 1; tab = 2 },
                        onSearchTrack = ::searchTrack,
                        output = audioOut,
                        update = updateVersion,
                        onUpdate = { tab = 3 },
                        onDrive = { showDrive = true },
                    )
                    1 -> SearchScreen(stations, settings)
                    2 -> SongsScreen(songsSegment, { songsSegment = it }, history, ::searchTrack)
                    else -> SettingsScreen(
                        s = settings,
                        sys = SystemStatus(batteryOk, notificationsOk, bluetoothOk, carLinked),
                        onPickCar = ::pickCar,
                        onLinkCar = ::linkCar,
                        onFixBattery = ::fixBattery,
                        onFixNotifications = ::fixNotifications,
                        onAppSettings = ::openAppSettings,
                        onExport = { exportLauncher.launch("avtoradio-stations.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        stations = stations,
                        logoStatus = logoStatus,
                        onFetchLogos = ::fetchLogos,
                        onCheckUpdate = { lifecycleScope.launch { Updater.check() } },
                        onDownloadUpdate = { info -> lifecycleScope.launch { Updater.download(this@MainActivity, info) } },
                        onInstallUpdate = { f -> Updater.install(this@MainActivity, f) },
                    )
                }
            }
        }

        editStreamsId?.let { id -> StreamsDialog(id, onDismiss = { editStreamsId = null }) }
        searchQuery?.let { q ->
            SearchChooser(q, onPick = { svc -> searchQuery = null; openSearch(svc, q) }, onDismiss = { searchQuery = null })
        }

        if (showCarPicker) {
            CarPickerDialog(
                devices = bondedDevices(),
                current = settings.carMac,
                onPick = ::chooseCar,
                onDismiss = { showCarPicker = false },
            )
        }
    }

    @Composable
    private fun BottomNav(tab: Int, onTab: (Int) -> Unit) {
        Column(Modifier.fillMaxWidth().background(C.bar)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(C.line))
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                NavItem("Радио", Ic.radio, tab == 0, Modifier.weight(1f)) { onTab(0) }
                NavItem("Добавить", Ic.search, tab == 1, Modifier.weight(1f)) { onTab(1) }
                NavItem("Песни", Ic.heart, tab == 2, Modifier.weight(1f)) { onTab(2) }
                NavItem("Настройки", Ic.sliders, tab == 3, Modifier.weight(1f)) { onTab(3) }
            }
        }
    }

    @Composable
    private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        Column(
            modifier
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val color = if (selected) C.accent else C.muted
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Text(label, fontSize = 12.sp, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}
