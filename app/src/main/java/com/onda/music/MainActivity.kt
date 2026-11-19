package com.onda.music

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay

// ────────────── COLORES ONDA ──────────────
private val CBg = Color(0xFF100D0B)
private val CText = Color(0xFFF4EFE6)
private val CMuted = Color(0xFF9B8D7F)
private val CAmpber = Color(0xFFFFB443)
private val CEmber = Color(0xFFE4572E)
private val CCream = Color(0xFFF7E8C8)
private val CDark = Color(0xFF22150A)

// ────────────── MODELO DE DATOS ──────────────
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,
    val albumArt: String?
)

private fun String?.orFallback(f: String): String =
    if (this.isNullOrBlank()) f else this.trim()

// Lee la biblioteca musical del dispositivo con MediaStore
fun querySongs(context: Context): List<Song> {
    val result = mutableListOf<Song>()
    val proj = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID
    )
    val sel = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
        MediaStore.Audio.Media.DURATION + " > 0"

    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        proj, sel, null,
        MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
    )?.use { c ->
        val iId   = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val iT    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val iA    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val iAl   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val iD    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val iAlb  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

        while (c.moveToNext()) {
            val id = c.getLong(iId)
            val albId = c.getLong(iAlb)
            val rawArtist = c.getString(iA).orFallback("Artista desconocido")
            val artist = if (rawArtist == "<unknown>") "Artista desconocido" else rawArtist

            result.add(Song(
                id = id,
                title = c.getString(iT).orFallback("Sin titulo"),
                artist = artist,
                album = c.getString(iAl).orFallback("Sin album"),
                duration = c.getLong(iD),
                uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                ).toString(),
                albumArt = if (albId > 0)
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albId
                    ).toString() else null
            ))
        }
    }
    return result
}

fun fmtMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

// ────────────── CONEXIÓN CON EL SERVICIO DE REPRODUCCIÓN ──────────────
class PlayerConnection {
    val hasPermission = mutableStateOf(false)
    val songs = mutableStateOf<List<Song>>(emptyList())
    val controller = mutableStateOf<MediaController?>(null)
    private var future: ListenableFuture<MediaController>? = null

    fun connect(context: Context) {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener(
            { runCatching { controller.value = f.get() } },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun loadSongs(context: Context) {
        songs.value = querySongs(context)
    }

    fun disconnect() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller.value = null
    }
}

// ────────────── ACTIVIDAD PRINCIPAL ──────────────
class MainActivity : ComponentActivity() {

    private val pc = PlayerConnection()

    private val reqPerm = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val ap = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        if (res[ap] == true) {
            pc.hasPermission.value = true
            pc.connect(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ap = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        val granted = ContextCompat.checkSelfPermission(this, ap) == PackageManager.PERMISSION_GRANTED
        pc.hasPermission.value = granted
        if (granted) pc.connect(this)
        else reqPerm.launch(arrayOf(ap, Manifest.permission.POST_NOTIFICATIONS))

        setContent {
            MaterialTheme(darkColorScheme(
                primary = CAmpber, onPrimary = CDark,
                background = CBg, surface = Color(0xFF1A1410),
                onSurface = CText, surfaceVariant = Color(0xFF231B15),
                onSurfaceVariant = CMuted
            )) {
                OndaApp(pc) {
                    reqPerm.launch(arrayOf(ap, Manifest.permission.POST_NOTIFICATIONS))
                }
            }
        }
    }

    override fun onDestroy() {
        pc.disconnect()
        super.onDestroy()
    }
}

// ────────────── INTERFAZ DE USUARIO ──────────────
@Composable
fun OndaApp(pc: PlayerConnection, reqPerm: () -> Unit) {
    val ctx = LocalContext.current
    val controller by pc.controller
    val songs by pc.songs
    val hasPerm by pc.hasPermission

    var query   by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var posMs   by remember { mutableStateOf(0L) }
    var durMs   by remember { mutableStateOf(0L) }
    var curIdx  by remember { mutableStateOf(-1) }
    var shuffle by remember { mutableStateOf(false) }
    var repMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    var drag    by remember { mutableStateOf(false) }
    var dragV   by remember { mutableStateOf(0f) }

    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val maxVol = remember { (am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1).toFloat() }
    var vol by remember {
        mutableStateOf((am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat())
    }

    LaunchedEffect(hasPerm) {
        if (hasPerm) pc.loadSongs(ctx)
    }

    // Sondeo del estado del reproductor cada 350 ms
    LaunchedEffect(controller) {
        while (true) {
            val c = controller
            if (c != null) {
                isPlaying = c.isPlaying
                curIdx = if (c.mediaItemCount > 0) c.currentMediaItemIndex else -1
                val d = c.duration
                durMs = if (d > 0) d else 0L
                if (!drag) posMs = c.currentPosition.coerceAtLeast(0L)
                shuffle = c.shuffleModeEnabled
                repMode = c.repeatMode
            }
            delay(350)
        }
    }

    val curSong = songs.getOrNull(curIdx)
    val filtered = if (query.isBlank()) songs else songs.filter {
        val q = query.trim()
        it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
    }

    fun playSong(song: Song) {
        val c = controller ?: return
        val idx = songs.indexOfFirst { it.id == song.id }
        if (idx < 0) return
        c.setMediaItems(songs.map { MediaItem.fromUri(it.uri) }, idx, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            songs.firstOrNull()?.let { playSong(it) }
            return
        }
        if (c.isPlaying) c.pause() else c.play()
    }

    // ── Fondo con brillos ──
    Box(Modifier.fillMaxSize().background(CBg)) {
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0x30E4572E), Color(0x00E4572E)), Offset(60f, 60f), 950f
            )
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0x22FFB443), Color(0x00FFB443)), Offset(1150f, 220f), 850f
            )
        ))

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // ── Cabecera ──
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(CEmber, CAmpber))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = CDark, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("ONDA", color = CText, fontSize = 20.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                    Text("REPRODUCTOR LOCAL", color = CMuted, fontSize = 8.sp, letterSpacing = 3.sp)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(
                        if (isPlaying && curSong != null) CAmpber else CMuted
                    ))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isPlaying && curSong != null -> "REPRODUCIENDO"
                            curSong != null -> "EN PAUSA"
                            else -> "SIN REPRODUCCION"
                        },
                        color = CMuted, fontSize = 9.sp, letterSpacing = 2.sp
                    )
                }
            }

            when {
                // ── Estado: sin permiso ──
                !hasPerm -> PermCard(reqPerm)

                // ── Estado: biblioteca vacía ──
                songs.isEmpty() -> EmptyLib()

                // ── Estado: lista de canciones ──
                else -> {
                    // Carátula + info de la canción actual
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val art = curSong?.albumArt
                        Box(
                            Modifier
                                .size(116.dp)
                                .shadow(20.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(
                                    listOf(Color(0xFF2C2117), Color(0xFF1B130D))
                                ))
                        ) {
                            if (art != null) {
                                AsyncImage(art, null, Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Rounded.MusicNote, null,
                                    tint = CAmpber.copy(alpha = 0.5f),
                                    modifier = Modifier.size(46.dp).align(Alignment.Center))
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                curSong?.title ?: "Elige una pista",
                                color = CText, fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                lineHeight = 26.sp
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                curSong?.artist ?: "Tu biblioteca aparecera aqui",
                                color = CMuted, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                curSong?.album ?: "",
                                color = CAmpber.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Barra de progreso
                    Spacer(Modifier.height(14.dp))
                    Slider(
                        value = if (drag) dragV else posMs.toFloat(),
                        onValueChange = { drag = true; dragV = it },
                        onValueChangeFinished = {
                            drag = false
                            controller?.seekTo(dragV.toLong())
                            posMs = dragV.toLong()
                        },
                        valueRange = 0f..(if (durMs > 0) durMs.toFloat() else 1f),
                        colors = SliderDefaults.colors(
                            thumbColor = CCream,
                            activeTrackColor = CAmpber,
                            inactiveTrackColor = Color(0x33F4EFE6)
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(fmtMs(if (drag) dragV.toLong() else posMs), color = CMuted, fontSize = 11.sp)
                        Text(fmtMs(durMs), color = CMuted, fontSize = 11.sp)
                    }

                    // Controles principales
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Aleatorio
                        IconButton(onClick = {
                            controller?.shuffleModeEnabled =
                                !(controller?.shuffleModeEnabled ?: false)
                        }) {
                            Icon(Icons.Rounded.Shuffle, "Aleatorio",
                                tint = if (shuffle) CAmpber else CMuted)
                        }
                        // Anterior
                        IconButton(onClick = {
                            val c = controller ?: return@IconButton
                            if (c.currentPosition > 4000) c.seekTo(0)
                            else c.seekToPreviousMediaItem()
                        }, enabled = curSong != null) {
                            Icon(Icons.Rounded.SkipPrevious, "Anterior",
                                tint = CText, modifier = Modifier.size(32.dp))
                        }
                        // Play / Pausa
                        Box(
                            Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(CCream, CAmpber)))
                                .clickable(enabled = curSong != null || songs.isNotEmpty()) {
                                    togglePlay()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                "Reproducir", tint = CDark,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        // Siguiente
                        IconButton(onClick = { controller?.seekToNextMediaItem() },
                            enabled = curSong != null) {
                            Icon(Icons.Rounded.SkipNext, "Siguiente",
                                tint = CText, modifier = Modifier.size(32.dp))
                        }
                        // Repetir
                        IconButton(onClick = {
                            val c = controller ?: return@IconButton
                            c.repeatMode = when (c.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }) {
                            Icon(
                                if (repMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                                else Icons.Rounded.Repeat,
                                "Repetir",
                                tint = if (repMode != Player.REPEAT_MODE_OFF) CAmpber else CMuted
                            )
                        }
                    }

                    // Volumen
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.VolumeUp, null,
                            tint = CMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Slider(
                            vol,
                            onValueChange = {
                                vol = it
                                am?.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    it.toInt().coerceIn(0, maxVol.toInt()),
                                    0
                                )
                            },
                            valueRange = 0f..maxVol,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = CCream,
                                activeTrackColor = CAmpber.copy(alpha = 0.75f),
                                inactiveTrackColor = Color(0x22F4EFE6)
                            )
                        )
                    }

                    // Buscador
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        query,
                        onValueChange = { query = it },
                        Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("Filtrar por titulo, artista o album",
                                color = CMuted, fontSize = 12.sp)
                        },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = CMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CAmpber,
                            unfocusedBorderColor = Color(0x22F4EFE6),
                            focusedTextColor = CText,
                            unfocusedTextColor = CText,
                            cursorColor = CAmpber
                        )
                    )

                    // Lista de canciones
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f)
                            .padding(top = 10.dp, bottom = 8.dp)
                    ) {
                        items(filtered, key = { it.id }) { s ->
                            SongRow(s, curSong?.id == s.id) { playSong(s) }
                        }
                    }
                }
            }
        }
    }
}

// ────────────── FILA DE CANCIÓN ──────────────
@Composable
fun SongRow(song: Song, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color(0x1AFFB443) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(
                    listOf(Color(0xFF2C2117), Color(0xFF1B130D))
                ))
        ) {
            if (song.albumArt != null) {
                AsyncImage(song.albumArt, null,
                    Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.MusicNote, null,
                    tint = if (active) CAmpber else CMuted,
                    modifier = Modifier.size(18.dp).align(Alignment.Center))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title,
                color = if (active) CCream else CText,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist,
                color = CMuted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(fmtMs(song.duration), color = CMuted, fontSize = 11.sp)
    }
}

// ────────────── TARJETA DE PERMISO ──────────────
@Composable
fun PermCard(onReq: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.MusicNote, null,
            tint = CAmpber, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(18.dp))
        Text("Permiso de musica",
            color = CText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "ONDA necesita acceso a tu almacenamiento para encontrar los archivos de audio. Todo se reproduce localmente.",
            color = CMuted, fontSize = 13.sp,
            textAlign = TextAlign.Center, lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 26.dp)
        )
        Spacer(Modifier.height(22.dp))
        Button(onReq,
            colors = ButtonDefaults.buttonColors(
                containerColor = CAmpber, contentColor = CDark
            ),
            shape = RoundedCornerShape(50)
        ) {
            Text("CONCEDER PERMISO",
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

// ────────────── BIBLIOTECA VACÍA ──────────────
@Composable
fun EmptyLib() {
    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.MusicNote, null,
            tint = CAmpber.copy(alpha = 0.7f),
            modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(18.dp))
        Text("Biblioteca vacia",
            color = CText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "No se encontraron archivos de musica.\nAnade canciones a tu telefono y vuelve a abrir la app.",
            color = CMuted, fontSize = 13.sp,
            textAlign = TextAlign.Center, lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 26.dp)
        )
    }
}
