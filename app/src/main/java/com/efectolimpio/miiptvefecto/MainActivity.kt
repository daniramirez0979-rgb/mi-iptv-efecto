package com.efectolimpio.miiptvefecto

import android.net.Uri
import android.content.pm.ActivityInfo
import android.view.View
import android.os.Bundle
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.common.MimeTypes
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Navy = Color(0xFF071827)
private val Panel = Color(0xFF102A40)
private val Panel2 = Color(0xFF15364F)
private val Cyan = Color(0xFF27C4D9)
private val White = Color(0xFFF4F8FB)
private val Muted = Color(0xFF9BB2C2)

data class Channel(val name: String, val url: String, val group: String = "Canales")
data class CatalogItem(val id: String, val name: String, val category: String, val image: String = "", val extension: String = "mp4")
data class PlaylistProfile(
    val id: String, val name: String, val type: String,
    val address: String, val username: String = "", val password: String = ""
)
enum class Section(val label: String) { HOME("Inicio"), LIVE("TV en vivo"), MOVIES("Películas"), SERIES("Series"), FAVORITES("Favoritos"), SETTINGS("Configuración") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MiIptvApp() }
    }
}

@Composable
fun MiIptvApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var playlistUrl by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Agregá tu lista M3U/M3U8 o conectá tu cuenta.") }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(Section.HOME) }
    var loading by remember { mutableStateOf(false) }
    var showXtream by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var movies by remember { mutableStateOf(emptyList<CatalogItem>()) }
    var series by remember { mutableStateOf(emptyList<CatalogItem>()) }
    var episodes by remember { mutableStateOf(emptyList<Channel>()) }
    var selectedSeries by remember { mutableStateOf<CatalogItem?>(null) }
    var rememberAccess by remember { mutableStateOf(true) }
    var prefsLoaded by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(emptyList<PlaylistProfile>()) }
    var activeProfileId by remember { mutableStateOf("") }
    var autoBackup by remember { mutableStateOf(true) }
    var failoverBusy by remember { mutableStateOf(false) }

    val securePrefs = remember {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "mi_iptv_efecto_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    LaunchedEffect(Unit) {
        playlistUrl = securePrefs.getString("playlist_url", "") ?: ""
        host = securePrefs.getString("host", "") ?: ""
        username = securePrefs.getString("username", "") ?: ""
        password = securePrefs.getString("password", "") ?: ""
        favorites = securePrefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        profiles = try {
            val array = org.json.JSONArray(securePrefs.getString("profiles", "[]") ?: "[]")
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                PlaylistProfile(o.optString("id"), o.optString("name"), o.optString("type"),
                    o.optString("address"), o.optString("username"), o.optString("password"))
            }
        } catch (_: Exception) { emptyList() }
        activeProfileId = securePrefs.getString("active_profile", "") ?: ""
        autoBackup = securePrefs.getBoolean("auto_backup", true)
        prefsLoaded = true
    }
    LaunchedEffect(playlistUrl, host, username, password, favorites, rememberAccess, prefsLoaded, profiles, activeProfileId, autoBackup) {
        if (prefsLoaded && rememberAccess) {
            val array = org.json.JSONArray()
            profiles.forEach { profile ->
                array.put(org.json.JSONObject().put("id", profile.id).put("name", profile.name)
                    .put("type", profile.type).put("address", profile.address)
                    .put("username", profile.username).put("password", profile.password))
            }
            securePrefs.edit().putString("playlist_url", playlistUrl)
                .putString("host", host).putString("username", username).putString("password", password)
                .putStringSet("favorites", favorites).putString("profiles", array.toString())
                .putString("active_profile", activeProfileId).putBoolean("auto_backup", autoBackup).apply()
        }
    }

    val httpFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
    }
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
            .build()
    }
    LaunchedEffect(isFullscreen) {
        val activity = context as? android.app.Activity
        activity?.requestedOrientation = if (isFullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.window?.decorView?.systemUiVisibility = if (isFullscreen) (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        ) else View.SYSTEM_UI_FLAG_VISIBLE
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // Si falla un canal, intenta el mismo canal en la siguiente lista guardada.
    DisposableEffect(player, selected, activeProfileId, profiles, autoBackup) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val failedChannel = selected ?: return
                if (!autoBackup || failoverBusy || profiles.size < 2) {
                    status = "No se pudo reproducir. Revisá la conexión o elegí otra lista."
                    return
                }
                scope.launch {
                    failoverBusy = true
                    status = "La lista actual falló. Buscando una lista de respaldo…"
                    try {
                        val ordered = profiles.dropWhile { it.id != activeProfileId }.drop(1) + profiles.takeWhile { it.id != activeProfileId }
                        var replacement: Pair<PlaylistProfile, Channel>? = null
                        for (profile in ordered) {
                            try {
                                val loaded = withContext(Dispatchers.IO) { loadProfileChannels(profile) }
                                val match = loaded.firstOrNull { normalizeChannelName(it.name) == normalizeChannelName(failedChannel.name) }
                                if (match != null) { replacement = profile to match; break }
                            } catch (_: Exception) { /* probar la siguiente fuente */ }
                        }
                        if (replacement != null) {
                            val (profile, channel) = replacement!!
                            channels = withContext(Dispatchers.IO) { loadProfileChannels(profile) }
                            activeProfileId = profile.id
                            selected = channel
                            status = "Respaldo activado: ${profile.name}"
                        } else {
                            status = "No se encontró ese canal en las otras listas guardadas."
                        }
                    } finally { failoverBusy = false }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            loading = true; status = "Leyendo archivo…"
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("No se pudo abrir el archivo")
                }
                channels = parseM3u(text)
                status = "Se cargaron ${channels.size} canales."
                section = Section.LIVE
            } catch (e: Exception) { status = "No se pudo leer: ${e.message ?: "error"}" }
            loading = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, background = Navy, surface = Panel, onPrimary = Navy,
        onBackground = White, onSurface = White, secondary = Cyan
    )) {
        Row(Modifier.fillMaxSize().background(Navy)) {
            // Navigation rail works well on wide Android TV screens and tablets.
            if (!isFullscreen) Column(
                Modifier.width(76.dp).fillMaxHeight().background(Color(0xFF06131F)).padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.LiveTv, null, tint = Cyan, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(8.dp))
                Section.values().forEach { item ->
                    val active = section == item
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 7.dp)
                            .background(if (active) Panel2 else Color.Transparent, RoundedCornerShape(13.dp))
                            .clickable { section = item; selected = null }
                            .padding(vertical = 11.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(sectionIcon(item), null, tint = if (active) Cyan else Muted, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(item.label.substringBefore(" "), color = if (active) White else Muted, fontSize = 9.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("1.1", color = Muted, fontSize = 10.sp)
            }

            Column(Modifier.fillMaxSize().then(if (isFullscreen) Modifier else Modifier.padding(horizontal = 18.dp, vertical = 14.dp))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("MI IPTV", color = White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("EFECTO", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("CONTRATÁ LA MEJOR TV DIGITAL AL 3765149583", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(if (channels.isEmpty()) "SIN LISTA CARGADA" else "${channels.size} CANALES", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))

                if (selected != null) {
                    if (!isFullscreen) {
                        Text(selected!!.name, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                    }
                    val activePlayer = player
                    AndroidView(factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = activePlayer
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    }, modifier = Modifier.fillMaxWidth().weight(1f))
                    LaunchedEffect(selected) {
                        selected?.let { channel ->
                            val uri = Uri.parse(channel.url)
                            val builder = MediaItem.Builder().setUri(uri)
                            val path = uri.path.orEmpty().lowercase()
                            if (path.endsWith(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                            if (path.endsWith(".mpd")) builder.setMimeType(MimeTypes.APPLICATION_MPD)
                            player.setMediaItem(builder.build())
                            player.prepare()
                            player.playWhenReady = true
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { isFullscreen = false; player.stop(); selected = null }) { Text("← Volver") }
                        TextButton(onClick = { isFullscreen = !isFullscreen }) {
                            Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isFullscreen) "Salir de pantalla completa" else "Pantalla completa")
                        }
                    }
                } else {
                    when (section) {
                        Section.HOME -> HomeScreen(
                            channelCount = channels.size,
                            status = status,
                            onLive = { section = Section.LIVE },
                            onSettings = { section = Section.SETTINGS },
                            onMovies = { section = Section.MOVIES },
                            onSeries = { section = Section.SERIES }
                        )
                        Section.LIVE -> {
                            SectionHeader("TV EN VIVO", "Tus canales disponibles")
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(),
                                label = { Text("Buscar canal") }, singleLine = true)
                            Spacer(Modifier.height(8.dp))
                            val filtered = channels.filter { it.name.contains(search, true) || it.group.contains(search, true) }
                            if (filtered.isEmpty()) EmptyState("Todavía no hay canales", "Cargá una lista desde Configuración para comenzar.")
                            else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(filtered) { channel ->
                                    ChannelRow(channel, favorites.contains(channel.url),
                                        onClick = { selected = channel },
                                        onFavorite = { favorites = if (favorites.contains(channel.url)) favorites - channel.url else favorites + channel.url })
                                }
                            }
                        }
                        Section.MOVIES -> {
                            SectionHeader("PELÍCULAS", "Catálogo VOD")
                            if (movies.isEmpty()) EmptyState("Catálogo no disponible", "Tu lista M3U/M3U8 carga canales de TV en vivo.")
                            else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(movies) { item ->
                                    CatalogRow(item.name, item.category) {
                                        val ext = item.extension.ifBlank { "mp4" }
                                        selected = Channel(item.name, "$host/movie/${Uri.encode(username)}/${Uri.encode(password)}/${item.id}.$ext", item.category)
                                    }
                                }
                            }
                        }
                        Section.SERIES -> {
                            SectionHeader(selectedSeries?.name ?: "SERIES", if (selectedSeries == null) "Catálogo de series" else "Elegí un episodio")
                            if (selectedSeries == null) {
                                if (series.isEmpty()) EmptyState("Catálogo no disponible", "Tu lista M3U/M3U8 carga canales de TV en vivo.")
                                else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(series) { item -> CatalogRow(item.name, item.category) {
                                        selectedSeries = item
                                        scope.launch {
                                            loading = true
                                            try {
                                                val api = "$host/player_api.php?username=${Uri.encode(username)}&password=${Uri.encode(password)}&action=get_series_info&series_id=${Uri.encode(item.id)}"
                                                val json = withContext(Dispatchers.IO) { downloadText(api) }
                                                episodes = parseSeriesEpisodes(json, host, username, password)
                                                status = if (episodes.isEmpty()) "No se encontraron episodios para esta serie." else "Episodios cargados: ${episodes.size}"
                                            } catch (e: Exception) { status = "No se pudieron cargar episodios: ${e.message ?: "error"}" }
                                            loading = false
                                        }
                                    } }
                                }
                            } else {
                                TextButton(onClick = { selectedSeries = null; episodes = emptyList() }) { Text("← Volver a series") }
                                if (episodes.isEmpty()) EmptyState("Sin episodios disponibles", status)
                                else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(episodes) { ep -> CatalogRow(ep.name, ep.group) { selected = ep } }
                                }
                            }
                        }
                        Section.FAVORITES -> {
                            SectionHeader("FAVORITOS", "Tus canales guardados")
                            val favs = channels.filter { favorites.contains(it.url) }
                            if (favs.isEmpty()) EmptyState("Aún no tenés favoritos", "Tocá la estrella de un canal para guardarlo.")
                            else LazyColumn { items(favs) { ChannelRow(it, true, { selected = it }, { favorites = favorites - it.url }) } }
                        }
                        Section.SETTINGS -> SettingsScreen(
                            playlistUrl, { playlistUrl = it }, host, { host = it }, username, { username = it },
                            password, { password = it }, showXtream, { showXtream = it }, loading, status,
                            profiles = profiles, activeProfileId = activeProfileId, autoBackup = autoBackup,
                            onAutoBackup = { autoBackup = it },
                            onSaveProfile = { name ->
                                val address = playlistUrl.trim()
                                if (address.isBlank()) status = "Pegá primero la URL completa de tu lista M3U/M3U8."
                                else {
                                    val profile = PlaylistProfile(java.util.UUID.randomUUID().toString(), name.ifBlank { "Lista ${profiles.size + 1}" }, "M3U", address)
                                    profiles = profiles + profile
                                    activeProfileId = profile.id
                                    status = "Lista de respaldo guardada: ${profile.name}"
                                }
                            },
                            onActivateProfile = { profile ->
                                scope.launch {
                                    loading = true; status = "Cambiando a ${profile.name}…"
                                    try {
                                        val loaded = withContext(Dispatchers.IO) { loadProfileChannels(profile) }
                                        require(loaded.isNotEmpty()) { "La lista no contiene canales." }
                                        channels = loaded; activeProfileId = profile.id; selected = null
                                        playlistUrl = profile.address; host = ""; username = ""; password = ""
                                        status = "Lista activa: ${profile.name} (${loaded.size} canales)"; section = Section.LIVE
                                    } catch (e: Exception) { status = "No se pudo activar ${profile.name}: ${e.message ?: "error"}" }
                                    loading = false
                                }
                            },
                            onRemoveProfile = { profile -> profiles = profiles.filterNot { it.id == profile.id }; if (activeProfileId == profile.id) activeProfileId = "" },
                            onLoadUrl = {
                                if (playlistUrl.isBlank()) status = "Pegá primero el enlace de tu lista."
                                else scope.launch {
                                    loading = true; status = "Descargando lista…"
                                    try {
                                        val body = withContext(Dispatchers.IO) { downloadText(playlistUrl.trim()) }
                                        channels = parseM3u(body); status = "Se cargaron ${channels.size} canales."; section = Section.LIVE
                                    } catch (e: Exception) { status = "Error al cargar: ${e.message ?: "revisá el enlace"}" }
                                    loading = false
                                }
                            },
                            onPickFile = { filePicker.launch(arrayOf("*/*")) },
                            onLoadMovies = {
                                scope.launch {
                                    loading = true; status = "Cargando películas…"
                                    try {
                                        val api = "$host/player_api.php?username=${Uri.encode(username)}&password=${Uri.encode(password)}&action=get_vod_streams"
                                        val json = withContext(Dispatchers.IO) { downloadText(api) }
                                        movies = parseCatalog(json)
                                        status = "Películas cargadas: ${movies.size}"
                                    } catch (e: Exception) { status = "Error en películas: ${e.message ?: "verificá la cuenta"}" }
                                    loading = false
                                }
                            },
                            onLoadSeries = {
                                scope.launch {
                                    loading = true; status = "Cargando series…"
                                    try {
                                        val api = "$host/player_api.php?username=${Uri.encode(username)}&password=${Uri.encode(password)}&action=get_series"
                                        val json = withContext(Dispatchers.IO) { downloadText(api) }
                                        series = parseCatalog(json)
                                        status = "Series cargadas: ${series.size}"
                                    } catch (e: Exception) { status = "Error en series: ${e.message ?: "verificá la cuenta"}" }
                                    loading = false
                                }
                            },
                            onXtream = {
                                scope.launch {
                                    loading = true; status = "Conectando…"
                                    try {
                                        val base = host.trim().trimEnd('/')
                                        require(base.startsWith("https://")) { "Usá una dirección HTTPS segura." }
                                        require(username.isNotBlank() && password.isNotBlank()) { "Completá usuario y contraseña." }
                                        val api = "$base/player_api.php?username=${Uri.encode(username)}&password=${Uri.encode(password)}&action=get_live_streams"
                                        val body = withContext(Dispatchers.IO) { downloadText(api) }
                                        channels = parseXtream(body, base, username, password)
                                        status = "Se cargaron ${channels.size} canales."; section = Section.LIVE
                                    } catch (e: Exception) { status = "No se pudo conectar: ${e.message ?: "verificá los datos"}" }
                                    loading = false
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Usá únicamente listas y servicios que tengas autorización para reproducir.", color = Color.Gray, fontSize = 9.sp)
            }
        }
    }
}

@Composable private fun HomeScreen(channelCount: Int, status: String, onLive: () -> Unit, onSettings: () -> Unit, onMovies: () -> Unit, onSeries: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().background(
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF123C56), Color(0xFF071827))),
                RoundedCornerShape(20.dp)).padding(20.dp)) {
                Column {
                    Text("BIENVENIDO", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Todo tu entretenimiento.", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text("En un solo lugar.", color = White, fontSize = 23.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(12.dp))
                    Text(if (channelCount > 0) "$channelCount canales cargados" else "Conectá tu lista para comenzar", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = if (channelCount > 0) onLive else onSettings) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp))
                        Text(if (channelCount > 0) "Ver TV en vivo" else "Agregar mi lista")
                    }
                }
            }
        }
        item { Text("EXPLORAR", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeTile("TV EN VIVO", "Canales y categorías", Icons.Default.LiveTv, Modifier.weight(1f), onLive)
                HomeTile("PELÍCULAS", "Tu catálogo VOD", Icons.Default.Movie, Modifier.weight(1f), onMovies)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeTile("SERIES", "Temporadas y episodios", Icons.Default.VideoLibrary, Modifier.weight(1f), onSeries)
                HomeTile("MI LISTA", "Configurar acceso", Icons.Default.Settings, Modifier.weight(1f), onSettings)
            }
        }
        item { Text(status, color = Muted, fontSize = 11.sp) }
    }
}

@Composable private fun HomeTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(27.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, color = White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.LiveTv, null, tint = Cyan, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = White, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable private fun CatalogPlaceholder(title: String, message: String) {
    Column(Modifier.fillMaxSize()) {
        SectionHeader(title, "Catálogo multimedia")
        Spacer(Modifier.height(20.dp))
        EmptyState("Sección preparada", message)
    }
}

@Composable private fun ChannelRow(channel: Channel, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircle, null, tint = Cyan, modifier = Modifier.size(27.dp).clickable { onClick() })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { onClick() }) {
                Text(channel.name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(channel.group, color = Muted, fontSize = 10.sp)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (favorite) Cyan else Muted)
            }
        }
    }
}

@Composable private fun CatalogRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircle, null, tint = Cyan, modifier = Modifier.size(27.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = Muted, fontSize = 10.sp)
            }
            Text("ABRIR", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
    }
}

@Composable private fun SettingsScreen(
    playlistUrl: String, setPlaylistUrl: (String) -> Unit, host: String, setHost: (String) -> Unit,
    username: String, setUsername: (String) -> Unit, password: String, setPassword: (String) -> Unit,
    showXtream: Boolean, setShowXtream: (Boolean) -> Unit, loading: Boolean, status: String,
    profiles: List<PlaylistProfile>, activeProfileId: String, autoBackup: Boolean,
    onAutoBackup: (Boolean) -> Unit, onSaveProfile: (String) -> Unit,
    onActivateProfile: (PlaylistProfile) -> Unit, onRemoveProfile: (PlaylistProfile) -> Unit,
    onLoadUrl: () -> Unit, onPickFile: () -> Unit,
    onLoadMovies: () -> Unit, onLoadSeries: () -> Unit, onXtream: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { SectionHeader("CONFIGURACIÓN", "Administrá tu acceso IPTV") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("LISTA M3U / M3U8", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    OutlinedTextField(playlistUrl, setPlaylistUrl, label = { Text("Pegá el enlace de tu lista") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onLoadUrl, enabled = !loading, modifier = Modifier.weight(1f)) { Text("Cargar enlace") }
                        OutlinedButton(onClick = onPickFile) { Text("Abrir archivo") }
                    }
                }
            }
        }
        item {
            var profileName by remember { mutableStateOf("") }
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LISTAS DE RESPALDO", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Guardá varias fuentes. Si un canal falla, la app probará encontrarlo en otra lista.", color = Muted, fontSize = 10.sp)
                    OutlinedTextField(profileName, { profileName = it }, label = { Text("Nombre de esta lista") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { onSaveProfile(profileName); profileName = "" }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Guardar lista actual como respaldo") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Cambio automático", color = White, fontWeight = FontWeight.SemiBold)
                            Text("Buscar el mismo canal en otra lista si falla", color = Muted, fontSize = 10.sp)
                        }
                        Switch(checked = autoBackup, onCheckedChange = onAutoBackup)
                    }
                    if (profiles.isEmpty()) Text("Todavía no guardaste listas de respaldo.", color = Muted, fontSize = 11.sp)
                    profiles.forEach { profile ->
                        Row(Modifier.fillMaxWidth().background(Panel2, RoundedCornerShape(10.dp)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(profile.name, color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Lista M3U/M3U8", color = Muted, fontSize = 10.sp)
                                if (profile.id == activeProfileId) Text("ACTIVA", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { onActivateProfile(profile) }, enabled = !loading) { Text("Usar") }
                            IconButton(onClick = { onRemoveProfile(profile) }) { Icon(Icons.Default.Delete, null, tint = Muted) }
                        }
                    }
                }
            }
        }
        item {
            Text(status, color = Muted, fontSize = 11.sp)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

private fun sectionIcon(section: Section) = when (section) {
    Section.HOME -> Icons.Default.Home
    Section.LIVE -> Icons.Default.LiveTv
    Section.MOVIES -> Icons.Default.Movie
    Section.SERIES -> Icons.Default.VideoLibrary
    Section.FAVORITES -> Icons.Default.Star
    Section.SETTINGS -> Icons.Default.Settings
}

private fun downloadText(address: String): String {
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 20000
    connection.setRequestProperty("User-Agent", "MiIPTVEfecto/1.1")
    return try {
        if (connection.responseCode !in 200..299) error("Servidor respondió ${connection.responseCode}")
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally { connection.disconnect() }
}

private fun normalizeChannelName(name: String): String = name.lowercase().replace(Regex("[^a-z0-9áéíóúüñ]"), "").trim()

private fun loadProfileChannels(profile: PlaylistProfile): List<Channel> {
    return if (profile.type == "XTREAM") {
        val base = profile.address.trim().trimEnd('/')
        require(base.startsWith("https://")) { "El servidor debe usar HTTPS." }
        val api = "$base/player_api.php?username=${Uri.encode(profile.username)}&password=${Uri.encode(profile.password)}&action=get_live_streams"
        parseXtream(downloadText(api), base, profile.username, profile.password)
    } else {
        val body = downloadText(profile.address)
        parseM3u(body)
    }
}

private fun parseM3u(text: String): List<Channel> {
    val result = mutableListOf<Channel>()
    var name = "Canal"; var group = "Canales"
    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.startsWith("#EXTINF", true)) {
            name = line.substringAfterLast(',').trim().ifBlank { "Canal" }
            group = Regex("""group-title="([^"]*)"""").find(line)?.groupValues?.get(1) ?: "Canales"
        } else if (line.isNotBlank() && !line.startsWith("#") &&
            (line.startsWith("http://") || line.startsWith("https://"))) {
            result += Channel(name, line, group); name = "Canal"; group = "Canales"
        }
    }
    return result
}

private fun parseXtream(json: String, base: String, user: String, pass: String): List<Channel> {
    val array = org.json.JSONArray(json)
    val result = mutableListOf<Channel>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val id = item.optString("stream_id")
        val name = item.optString("name", "Canal $id")
        val ext = item.optString("container_extension", "m3u8").ifBlank { "m3u8" }
        val category = item.optString("category_name", "Canales")
        if (id.isNotBlank()) result += Channel(name, "$base/live/${Uri.encode(user)}/${Uri.encode(pass)}/$id.$ext", category)
    }
    return result
}


private fun parseCatalog(json: String): List<CatalogItem> {
    val array = org.json.JSONArray(json)
    val result = mutableListOf<CatalogItem>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val id = item.optString("stream_id", item.optString("series_id"))
        if (id.isBlank()) continue
        result += CatalogItem(
            id = id,
            name = item.optString("name", "Contenido $id"),
            category = item.optString("category_name", "Sin categoría"),
            image = item.optString("stream_icon", item.optString("cover", "")),
            extension = item.optString("container_extension", "mp4").ifBlank { "mp4" }
        )
    }
    return result
}

private fun parseSeriesEpisodes(json: String, host: String, user: String, pass: String): List<Channel> {
    val root = org.json.JSONObject(json)
    val info = root.optJSONObject("info")
    val seriesTitle = info?.optString("name", "Serie") ?: "Serie"
    val seasons = root.optJSONObject("episodes") ?: return emptyList()
    val result = mutableListOf<Channel>()
    val keys = seasons.keys()
    while (keys.hasNext()) {
        val seasonKey = keys.next()
        val seasonEpisodes = seasons.optJSONArray(seasonKey) ?: continue
        for (i in 0 until seasonEpisodes.length()) {
            val ep = seasonEpisodes.optJSONObject(i) ?: continue
            val id = ep.optString("id")
            if (id.isBlank()) continue
            val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
            val title = ep.optString("title", "Episodio ${i + 1}")
            val season = ep.optString("season", seasonKey)
            val url = "$host/series/${Uri.encode(user)}/${Uri.encode(pass)}/$id.$ext"
            result += Channel("$seriesTitle - $title", "https://placeholder.invalid", "Temporada $season")
                .copy(url = url)
        }
    }
    return result
}
