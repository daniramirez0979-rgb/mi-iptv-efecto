package com.efectolimpio.miiptvefecto

import android.net.Uri
import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Navy = Color(0xFF071827)
private val Panel = Color(0xFF102A40)
private val Panel2 = Color(0xFF15364F)
private val Cyan = Color(0xFF27C4D9)
private val White = Color(0xFFF4F8FB)
private val Muted = Color(0xFF9BB2C2)

// Limita el consumo de memoria al iniciar sesión en TV Box con poca RAM.

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
    var moviesLoaded by remember { mutableStateOf(false) }
    var catalogLoading by remember { mutableStateOf(false) }
    var series by remember { mutableStateOf(emptyList<CatalogItem>()) }
    var episodes by remember { mutableStateOf(emptyList<Channel>()) }
    var selectedSeries by remember { mutableStateOf<CatalogItem?>(null) }
    var rememberAccess by remember { mutableStateOf(true) }
    var prefsLoaded by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(emptyList<PlaylistProfile>()) }
    var activeProfileId by remember { mutableStateOf("") }
    var autoBackup by remember { mutableStateOf(true) }
    var failoverBusy by remember { mutableStateOf(false) }
    var authenticated by remember { mutableStateOf(false) }
    var loginBusy by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf("") }
    var customerUsername by remember { mutableStateOf("") }
    var customerPassword by remember { mutableStateOf("") }
    var rememberCustomerLogin by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    // El catálogo VOD usa su propio estado de carga: no queda bloqueado
    // si el usuario entra a Películas mientras otra operación está ocupada.
    LaunchedEffect(section, authenticated, customerUsername, customerPassword, moviesLoaded) {
        if (authenticated && section == Section.MOVIES && !moviesLoaded && !catalogLoading) {
            moviesLoaded = true
            catalogLoading = true
            status = "Cargando catálogo de películas…"
            try {
                val json = withContext(Dispatchers.IO) {
                    customerCatalog(customerUsername, customerPassword, "get_vod_streams")
                }
                movies = parseCatalog(json)
                status = if (movies.isEmpty()) "El proveedor respondió, pero no se pudieron interpretar las películas." else "Películas cargadas: ${movies.size}"
            } catch (e: Exception) {
                status = "Error al cargar películas: ${e.message ?: "intentá nuevamente"}"
            } finally { catalogLoading = false }
        }
    }


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
            }.take(1)
        } catch (_: Exception) { emptyList() }
        activeProfileId = securePrefs.getString("active_profile", "") ?: ""
        autoBackup = securePrefs.getBoolean("auto_backup", true)
        rememberCustomerLogin = securePrefs.getBoolean("remember_customer_login", false)
        if (rememberCustomerLogin) {
            customerUsername = securePrefs.getString("customer_username", "") ?: ""
            customerPassword = securePrefs.getString("customer_password", "") ?: ""
        }
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


    // Consulta silenciosa: el panel solo habilita el aviso cuando el administrador lo autoriza.
    LaunchedEffect(Unit) {
        try {
            val info = withContext(Dispatchers.IO) { fetchAuthorizedAppUpdate() }
            if (info != null && info.first > BuildConfig.VERSION_CODE) {
                updateDialog = Triple(info.second, info.third, info.fourth)
            }
        } catch (_: Exception) { /* sin aviso si no hay conexión */ }
    }

    val httpFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
    }
    val player = remember {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 60000, 2500, 6000)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
    }
    // La Activity declara configChanges para conservar sesión y reproductor al girar.
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

    // Revalida el acceso periódicamente. Un rechazo explícito del servidor corta el acceso
    // y vuelve a la pantalla de ingreso, sin cerrar la aplicación. Los errores de red no expulsan.
    LaunchedEffect(authenticated, customerUsername, customerPassword) {
        if (authenticated && customerUsername.isNotBlank()) {
            while (true) {
                delay(15_000)
                try {
                    val refreshed = withContext(Dispatchers.IO) { customerLogin(customerUsername, customerPassword) }
                    val updatedProfiles = refreshed.playlists.take(1).map { address ->
                        PlaylistProfile("server_0", "Lista principal", "M3U", address)
                    }
                    if (updatedProfiles.isNotEmpty() && profiles.map { it.address } != updatedProfiles.map { it.address }) {
                        val target = updatedProfiles.first()
                        val parsed = withContext(Dispatchers.IO) { loadProfileChannels(target) }
                        if (parsed.isNotEmpty()) {
                            profiles = updatedProfiles
                            activeProfileId = target.id
                            playlistUrl = target.address
                            channels = parsed
                            if (selected == null) status = "Lista actualizada · ${channels.size} canales"
                        }
                    }
                } catch (e: CustomerAuthException) {
                    if (e.httpCode == 401 || e.httpCode == 403) {
                        player.stop()
                        selected = null
                        channels = emptyList()
                        authenticated = false
                        loginError = "Tu acceso fue bloqueado o venció. Contactá al administrador."
                        status = loginError
                        break
                    }
                } catch (_: Exception) {
                    // Cortes de internet/errores temporales: mantener reproducción y reintentar.
                }
            }
        }
    }

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

        if (updateDialog != null) {
            AlertDialog(
                onDismissRequest = { updateDialog = null },
                title = { Text("Nueva actualización de EFECTO TV") },
                text = { Text(updateDialog!!.second.ifBlank { "El administrador autorizó una nueva versión." }) },
                confirmButton = {
                    TextButton(onClick = {
                        val link = updateDialog!!.third
                        updateDialog = null
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } catch (_: Exception) { }
                    }) { Text("ACTUALIZAR APK") }
                },
                dismissButton = { TextButton(onClick = { updateDialog = null }) { Text("Ahora no") } }
            )
        }
        Row(Modifier.fillMaxSize().background(Navy)) {
            if (authenticated) {

            // Navigation rail works well on wide Android TV screens and tablets.
            if (!isFullscreen) Column(
                Modifier.width(72.dp).fillMaxHeight().background(Color(0xFF06131F)).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.LiveTv, null, tint = Cyan, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(8.dp))
                Section.values().filter { it != Section.SETTINGS }.forEach { item ->
                    val active = section == item
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 7.dp)
                            .background(if (active) Panel2 else Color.Transparent, RoundedCornerShape(13.dp))
                            .clickable { section = item; selected = null }
                            .padding(vertical = 13.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(sectionIcon(item), null, tint = if (active) Cyan else Muted, modifier = Modifier.size(27.dp))
                        Spacer(Modifier.height(5.dp))
                        Text(item.label.substringBefore(" "), color = if (active) White else Muted, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("1.1", color = Muted, fontSize = 10.sp)
            }

            Column(Modifier.fillMaxSize().then(if (isFullscreen) Modifier else Modifier.padding(horizontal = 8.dp, vertical = 8.dp))) {
                if (!isFullscreen) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("MI IPTV", color = White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("EFECTO", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("CONTRATÁ LA MEJOR TV DIGITAL", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        Text("WHATSAPP 3765-149583", color = White, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (channels.isEmpty()) "SIN LISTA CARGADA" else "${channels.size} CANALES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = {
                            scope.launch {
                                loading = true
                                status = "Actualizando canales desde el panel…"
                                try {
                                    val session = withContext(Dispatchers.IO) { customerLogin(customerUsername, customerPassword) }
                                    val freshProfiles = session.playlists.mapIndexed { index, address ->
                                        PlaylistProfile("server_$index", if (index == 0) "Lista principal" else "Lista secundaria", "M3U", address)
                                    }
                                    val activeIndex = activeProfileId.substringAfterLast("_", "0").toIntOrNull() ?: 0
                                    val target = freshProfiles.getOrNull(activeIndex) ?: freshProfiles.first()
                                    val freshChannels = withContext(Dispatchers.IO) { loadProfileChannels(target) }
                                    require(freshChannels.isNotEmpty()) { "La lista está vacía o no respondió." }
                                    player.stop()
                                    selected = null
                                    profiles = freshProfiles
                                    activeProfileId = target.id
                                    playlistUrl = target.address
                                    channels = freshChannels
                                    status = "Canales actualizados · ${freshChannels.size} disponibles"
                                } catch (e: Exception) {
                                    status = "No se pudieron actualizar los canales: ${e.message ?: "revisá internet"}"
                                } finally { loading = false }
                            }
                        }, enabled = !loading && customerUsername.isNotBlank() && customerPassword.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) { Text(if (loading) "ACTUALIZANDO…" else "↻ ACTUALIZAR CANALES", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (selected != null) {
                    if (!isFullscreen) {
                        Text(selected!!.name, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                    }
                    val activePlayer = player
                    Box(
                        modifier = if (isFullscreen) Modifier.fillMaxSize()
                            else Modifier.fillMaxWidth().weight(if (channels.isNotEmpty()) 0.68f else 1f)
                    ) {
                        AndroidView(factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = activePlayer
                                useController = true
                                resizeMode = if (isFullscreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        }, update = { view ->
                            view.resizeMode = if (isFullscreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                            view.useController = true
                        }, modifier = Modifier.fillMaxSize())
                        if (isFullscreen) {
                            IconButton(
                                onClick = { isFullscreen = false },
                                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                                    .background(Color(0x99000000), CircleShape).size(52.dp)
                            ) {
                                Icon(Icons.Default.FullscreenExit, contentDescription = "Salir de pantalla completa",
                                    tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
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
                    if (!isFullscreen) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { isFullscreen = false; player.stop(); selected = null }, modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)) { Text("← CANALES", fontSize = 12.sp, maxLines = 1) }
                                TextButton(onClick = {
                                    val index = channels.indexOfFirst { it.url == selected?.url }
                                    if (index > 0) selected = channels[index - 1]
                                }, enabled = channels.indexOfFirst { it.url == selected?.url } > 0, modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)) { Text("ANTERIOR", fontSize = 12.sp, maxLines = 1) }
                                TextButton(onClick = {
                                    val index = channels.indexOfFirst { it.url == selected?.url }
                                    if (index >= 0 && index < channels.lastIndex) selected = channels[index + 1]
                                }, enabled = channels.indexOfFirst { it.url == selected?.url } in 0 until channels.lastIndex, modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)) { Text("SIGUIENTE", fontSize = 12.sp, maxLines = 1) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = {
                                    player.stop()
                                    selected?.let { channel ->
                                        val uri = Uri.parse(channel.url)
                                        val item = MediaItem.Builder().setUri(uri).build()
                                        player.setMediaItem(item); player.prepare(); player.playWhenReady = true
                                    }
                                }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("REINTENTAR", fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { isFullscreen = !isFullscreen }, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isFullscreen) "Salir de pantalla completa" else "Pantalla completa",
                                        tint = Cyan,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                        if (!isFullscreen && channels.isNotEmpty()) {
                            Text("CANALES DISPONIBLES  •  ${channels.size}", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            LazyColumn(Modifier.fillMaxWidth().weight(0.32f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(channels) { channel ->
                                    ChannelRow(channel, favorites.contains(channel.url),
                                        onClick = { selected = channel },
                                        onFavorite = { favorites = if (favorites.contains(channel.url)) favorites - channel.url else favorites + channel.url })
                                }
                            }
                        }
                    }
                } else {
                    when (section) {
                        Section.HOME -> HomeScreen(
                            channelCount = channels.size,
                            status = status,
                            onLive = { section = Section.LIVE },
                            onSettings = { status = "Tu acceso y tus listas se administran desde EFECTO TV." },
                            onMovies = { section = Section.MOVIES },
                            onSeries = { section = Section.SERIES }
                        )
                        Section.LIVE -> {
                            SectionHeader("TV EN VIVO", "Tus canales disponibles")
                            if (profiles.size > 1) {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    profiles.forEach { profile ->
                                        if (profile.id == activeProfileId) {
                                            Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                                                Text(if (profile.name.contains("secund", true)) "SECUNDARIA ✓" else "PRIMARIA ✓")
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        loading = true
                                                        status = "Cambiando a ${profile.name}…"
                                                        try {
                                                            val loaded = withContext(Dispatchers.IO) { loadProfileChannels(profile) }
                                                            require(loaded.isNotEmpty()) { "La lista no contiene canales." }
                                                            player.stop()
                                                            selected = null
                                                            channels = loaded
                                                            activeProfileId = profile.id
                                                            playlistUrl = profile.address
                                                            status = "Lista activa: ${profile.name} (${loaded.size} canales)"
                                                        } catch (e: Exception) {
                                                            status = "No se pudo activar ${profile.name}: ${e.message ?: "revisá la conexión"}"
                                                        } finally { loading = false }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                enabled = !loading
                                            ) {
                                                Text(if (profile.name.contains("secund", true)) "USAR SECUNDARIA" else "USAR PRIMARIA")
                                            }
                                        }
                                    }
                                }
                            }
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
                            SectionHeader("PELÍCULAS", "Catálogo de películas")
                            if (movies.isEmpty()) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    EmptyState("Catálogo no disponible", if (catalogLoading) "Buscando películas del proveedor…" else status)
                                    if (authenticated && !catalogLoading) {
                                        TextButton(onClick = { moviesLoaded = false }) { Text("REINTENTAR CARGA") }
                                    }
                                }
                            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(movies) { item ->
                                    CatalogRow(item.name, item.category) {
                                        val ext = item.extension.ifBlank { "mp4" }
                                        val access = providerAccess(playlistUrl)
                                        if (access == null) status = "No se pudo leer el acceso de la lista principal."
                                        else selected = Channel(item.name, access.movieUrl(item.id, ext), item.category)
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
                                                val access = providerAccess(playlistUrl) ?: error("No se pudo leer el acceso de la lista principal.")
                                                val json = withContext(Dispatchers.IO) { downloadText(access.seriesInfoUrl(item.id)) }
                                                episodes = parseSeriesEpisodes(json, access.host, access.username, access.password)
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
                                        val json = withContext(Dispatchers.IO) {
                                            if (authenticated) customerCatalog(customerUsername, customerPassword, "get_vod_streams")
                                            else downloadText("$host/player_api.php?username=${Uri.encode(username)}&password=${Uri.encode(password)}&action=get_vod_streams")
                                        }
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
                                        val json = withContext(Dispatchers.IO) {
                                            if (authenticated) providerCatalog(playlistUrl, "get_series")
                                            else downloadText("$host/player_api.php?username=${Uri.encode(username)}&password=${Uri.encode(password)}&action=get_series")
                                        }
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
            } else {
                LoginScreen(
                    busy = loginBusy,
                    error = loginError,
                    savedUsername = customerUsername,
                    savedPassword = customerPassword,
                    rememberLogin = rememberCustomerLogin,
                    onRememberLoginChange = { enabled ->
                        rememberCustomerLogin = enabled
                        if (enabled) {
                            securePrefs.edit().putBoolean("remember_customer_login", true).apply()
                        } else {
                            securePrefs.edit().putBoolean("remember_customer_login", false)
                                .remove("customer_username").remove("customer_password").apply()
                            customerUsername = ""
                            customerPassword = ""
                        }
                    },
                    onLogin = { user, pass ->
                        scope.launch {
                            loginBusy = true
                            loginError = ""
                            try {
                                val session = withContext(Dispatchers.IO) { customerLogin(user, pass) }
                                val loaded = session.playlists.mapIndexed { index, address ->
                                    PlaylistProfile("server_$index", if (index == 0) "Lista principal" else "Lista secundaria", "M3U", address)
                                }
                                require(loaded.isNotEmpty()) { "El servicio no tiene listas configuradas." }
                                var loadedChannels = emptyList<Channel>()
                                var active: PlaylistProfile? = null
                                for (profile in loaded) {
                                    try {
                                        val candidate = withContext(Dispatchers.IO) { loadProfileChannels(profile) }
                                        if (candidate.isNotEmpty()) {
                                            loadedChannels = candidate
                                            active = profile
                                            break
                                        }
                                    } catch (_: Exception) { }
                                }
                                require(active != null && loadedChannels.isNotEmpty()) { "No se pudo cargar ninguna lista. Probá más tarde o contactá al administrador." }
                                customerUsername = user
                                customerPassword = pass
                                if (rememberCustomerLogin) {
                                    securePrefs.edit().putBoolean("remember_customer_login", true)
                                        .putString("customer_username", user)
                                        .putString("customer_password", pass).apply()
                                }
                                profiles = loaded
                                activeProfileId = active!!.id
                                playlistUrl = active!!.address
                                channels = loadedChannels
                                section = Section.HOME
                                status = "Bienvenido ${user} · ${loadedChannels.size} canales"
                                authenticated = true
                            } catch (e: Exception) {
                                loginError = e.message ?: "No se pudo iniciar sesión."
                            } finally { loginBusy = false }
                        }
                    }
                )
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
                HomeTile("MI CUENTA", "Acceso administrado", Icons.Default.AccountCircle, Modifier.weight(1f), onSettings)
                HomeTile("MI CUENTA", "Acceso administrado", Icons.Default.AccountCircle, Modifier.weight(1f), onSettings)
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircle, null, tint = Cyan, modifier = Modifier.size(32.dp).clickable { onClick() })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { onClick() }) {
                Text(channel.name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(channel.group, color = Muted, fontSize = 12.sp)
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


private data class CustomerSession(val playlists: List<String>, val expiresAt: String)

private class CustomerAuthException(val httpCode: Int, message: String) : Exception(message)

private fun customerCatalog(username: String, password: String, action: String): String {
    require(action == "get_vod_streams" || action == "get_series") { "Catálogo inválido." }
    val endpoint = "https://zumorvkalqehzztvosce.supabase.co/functions/v1/customer-catalog"
    val apiKey = "sb_publishable_feT-R_JGExnqwyIrJgw4TQ_H3I0PVXS"
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 20000
    connection.readTimeout = 35000
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("apikey", apiKey)
    connection.setRequestProperty("Accept", "application/json")
    val body = org.json.JSONObject().put("username", username).put("password", password).put("action", action).toString()
    return try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (code !in 200..299) {
            val message = try { org.json.JSONObject(response).optString("error") } catch (_: Exception) { "" }
            throw IllegalStateException(message.ifBlank { "No se pudo cargar el catálogo ($code)." })
        }
        response
    } finally { connection.disconnect() }
}

private fun customerLogin(username: String, password: String): CustomerSession {
    val endpoint = "https://zumorvkalqehzztvosce.supabase.co/functions/v1/customer-auth"
    val apiKey = "sb_publishable_feT-R_JGExnqwyIrJgw4TQ_H3I0PVXS"
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 20000
    connection.readTimeout = 30000
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("apikey", apiKey)
    connection.setRequestProperty("Accept", "application/json")
    val body = org.json.JSONObject().put("username", username).put("password", password).toString()
    return try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = org.json.JSONObject(response)
        if (code !in 200..299 || !json.optBoolean("ok")) {
            throw CustomerAuthException(code, json.optString("error").ifBlank { "Usuario o contraseña incorrectos." })
        }
        val array = json.optJSONArray("playlists") ?: org.json.JSONArray()
        val urls = (0 until array.length()).mapNotNull { i ->
            array.optString(i).takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }
        require(urls.isNotEmpty()) { "No hay listas disponibles para tu cuenta." }
        CustomerSession(urls, json.optString("expires_at"))
    } finally { connection.disconnect() }
}

@Composable private fun LoginScreen(
    busy: Boolean,
    error: String,
    savedUsername: String,
    savedPassword: String,
    rememberLogin: Boolean,
    onRememberLoginChange: (Boolean) -> Unit,
    onLogin: (String, String) -> Unit
) {
    var username by remember(savedUsername) { mutableStateOf(savedUsername) }
    var password by remember(savedPassword) { mutableStateOf(savedPassword) }
    Box(Modifier.fillMaxSize().background(Navy).padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().widthIn(max = 420.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.LiveTv, null, tint = Cyan, modifier = Modifier.size(54.dp))
                Text("EFECTO TV", color = White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("Presentado por Efecto Limpio", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(username, { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Usuario") }, singleLine = true)
                OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Contraseña") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Row(Modifier.fillMaxWidth().clickable { onRememberLoginChange(!rememberLogin) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberLogin, onCheckedChange = onRememberLoginChange, colors = CheckboxDefaults.colors(checkedColor = Cyan, checkmarkColor = Navy))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("Recordar usuario y contraseña", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Se guardan cifrados en este dispositivo", color = Muted, fontSize = 10.sp)
                    }
                }
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
                Button(onClick = { onLogin(username.trim(), password) }, enabled = !busy && username.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Navy, strokeWidth = 2.dp)
                    else Text("INGRESAR", fontWeight = FontWeight.Bold)
                }
                Text("Contratá la mejor TV digital · WhatsApp 3765-149583", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

private fun downloadText(address: String): String {
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 20000
    connection.readTimeout = 30000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
    connection.setRequestProperty("Accept", "*/*")
    return try {
        val code = connection.responseCode
        if (code !in 200..299) error("El servidor respondió HTTP $code")
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().removePrefix("\uFEFF") }
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
        downloadM3uChannels(profile.address)
    }
}

// Lee la lista M3U línea por línea para evitar cargar archivos gigantes completos
// en memoria, algo que puede cerrar la app en TV Box con poca RAM.
private fun downloadM3uChannels(address: String): List<Channel> {
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 20000
    connection.readTimeout = 30000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
    connection.setRequestProperty("Accept", "*/*")
    return try {
        val code = connection.responseCode
        if (code !in 200..299) error("El servidor respondió HTTP $code")
        val result = mutableListOf<Channel>()
        var name = "Canal"
        var group = "Canales"
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val raw = reader.readLine() ?: break
                val line = raw.trim()
                if (line.startsWith("#EXTINF", true)) {
                    name = line.substringAfterLast(',').trim().ifBlank { "Canal" }
                    group = Regex("""group-title="([^"]*)"""").find(line)?.groupValues?.get(1) ?: "Canales"
                } else if (line.isNotBlank() && !line.startsWith("#") &&
                    (line.startsWith("http://") || line.startsWith("https://"))) {
                    if (isLivePlaylistEntry(group, line)) result.add(Channel(name, line, group))
                    name = "Canal"
                    group = "Canales"
                }
            }
        }
        result
    } finally {
        connection.disconnect()
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
            if (isLivePlaylistEntry(group, line)) result += Channel(name, line, group); name = "Canal"; group = "Canales"
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


private data class ProviderAccess(val host: String, val username: String, val password: String) {
    fun movieUrl(id: String, ext: String) = "$host/movie/${Uri.encode(username)}/${Uri.encode(password)}/$id.$ext"
    fun seriesInfoUrl(id: String) = "$host/player_api.php?username=${Uri.encode(username)}&password=${Uri.encode(password)}&action=get_series_info&series_id=${Uri.encode(id)}"
}
private fun providerCatalog(address: String, action: String): String {
    require(action == "get_vod_streams" || action == "get_series") { "Catálogo inválido." }
    val access = providerAccess(address) ?: error("No se pudieron leer las credenciales de la lista principal.")
    val api = "${access.host}/player_api.php?username=${Uri.encode(access.username)}&password=${Uri.encode(access.password)}&action=${action}"
    return downloadText(api)
}

private fun providerAccess(address: String): ProviderAccess? {
    return try {
        val uri = Uri.parse(address)
        val scheme = uri.scheme ?: return null
        val authority = uri.encodedAuthority ?: return null
        val user = uri.getQueryParameter("username") ?: return null
        val pass = uri.getQueryParameter("password") ?: return null
        if (user.isBlank() || pass.isBlank()) return null
        ProviderAccess("$scheme://$authority", user, pass)
    } catch (_: Exception) { null }
}

private fun isLivePlaylistEntry(group: String, url: String): Boolean {
    val g = group.lowercase()
    val u = url.lowercase()
    val vodGroup = listOf("pelicul", "movie", "film", "vod", "serie", "series", "temporada", "episodio", "episode", "anime", "cine")
        .any { g.contains(it) }
    val vodPath = listOf("/movie/", "/series/", "/vod/").any { u.contains(it) }
    val vodFile = listOf(".mkv", ".avi", ".mp4", ".mov", ".wmv").any { u.substringBefore('?').endsWith(it) }
    return !vodGroup && !vodPath && !vodFile
}

private fun parseCatalog(json: String): List<CatalogItem> {
    val root = org.json.JSONTokener(json.trim().removePrefix("\uFEFF")).nextValue()
    val arrays = mutableListOf<org.json.JSONArray>()
    when (root) {
        is org.json.JSONArray -> arrays.add(root)
        is org.json.JSONObject -> {
            val keys = listOf("data", "items", "results", "streams", "series", "movie_data", "vod", "list", "response")
            keys.forEach { key -> root.optJSONArray(key)?.let { arrays.add(it) } }
            if (arrays.isEmpty()) {
                val values = root.keys()
                val collected = org.json.JSONArray()
                while (values.hasNext()) {
                    val value = root.opt(values.next())
                    if (value is org.json.JSONObject) collected.put(value)
                    else if (value is org.json.JSONArray) for (i in 0 until value.length()) collected.put(value.opt(i))
                }
                if (collected.length() > 0) arrays.add(collected)
            }
        }
    }
    val result = mutableListOf<CatalogItem>()
    val seen = mutableSetOf<String>()
    arrays.forEach { array ->
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val rawId = item.optString("stream_id").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("series_id").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("vod_id").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("id").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("streamId").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("seriesId").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("num").takeIf { it.isNotBlank() && it != "null" }
            val name = item.optString("name").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("title").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("series_name").takeIf { it.isNotBlank() && it != "null" }
                ?: "Contenido ${result.size + 1}"
            val id = rawId ?: "item_${name.lowercase().hashCode()}_${result.size}"
            if (!seen.add(id)) continue
            val category = item.optString("category_name").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("category").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("category_id").takeIf { it.isNotBlank() && it != "null" }
                ?: "Sin categoría"
            val image = item.optString("stream_icon").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("cover").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("cover_big").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("movie_image").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("poster").takeIf { it.isNotBlank() && it != "null" }
                ?: ""
            val extension = item.optString("container_extension").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("extension").takeIf { it.isNotBlank() && it != "null" }
                ?: "mp4"
            result += CatalogItem(id, name, category, image, extension)
        }
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


private fun checkLatestAppRelease(): Pair<Int, String>? {
    val connection = URL("https://api.github.com/repos/daniramirez0979-rgb/mi-iptv-efecto/releases/latest").openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("User-Agent", "EFECTO-TV-Android")
    return try {
        if (connection.responseCode !in 200..299) return null
        val json = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val tag = json.optString("tag_name")
        val version = Regex("""(\d+)""").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", true)) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }
        if (apkUrl.isBlank()) null else version to apkUrl
    } catch (_: Exception) { null } finally { connection.disconnect() }
}

private fun fetchAuthorizedAppUpdate(): QuadUpdate? {
    val connection = (URL("https://zumorvkalqehzztvosce.supabase.co/functions/v1/app-update-check").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8000
        readTimeout = 8000
        setRequestProperty("apikey", "sb_publishable_feT-R_JGExnqwyIrJgw4TQ_H3I0PVXS")
    }
    return try {
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = org.json.JSONObject(body)
        if (!json.optBoolean("authorized", false)) null else {
            val code = json.optInt("version_code", 0)
            val name = json.optString("version_name", "")
            val url = json.optString("apk_url", "")
            val message = json.optString("message", "")
            if (code <= 0 || !url.startsWith("https://")) null else QuadUpdate(code, name, message, url)
        }
    } finally { connection.disconnect() }
}
private data class QuadUpdate(val first: Int, val second: String, val third: String, val fourth: String)
