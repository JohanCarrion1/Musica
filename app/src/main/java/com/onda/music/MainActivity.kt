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
internal val CBg = Color(0xFF100D0B)
internal val CText = Color(0xFFF4EFE6)
internal val CMuted = Color(0xFF9B8D7F)
internal val CAmpber = Color(0xFFFFB443)
internal val CEmber = Color(0xFFE4572E)
internal val CCream = Color(0xFFF7E8C8)
internal val CDark = Color(0xFF22150A)

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
