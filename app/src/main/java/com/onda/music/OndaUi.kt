package com.onda.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// ────────────── PANTALLA PRINCIPAL ──────────────
@Composable
fun OndaApp(pc: PlayerConnection, reqPerm: () -> Unit) {
    val ctx = LocalContext.current
    val controller by pc.controller
    val songs by pc.songs
    val hasPerm by pc.hasPermission

    var query     by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var posMs     by remember { mutableStateOf(0L) }
    var durMs     by remember { mutableStateOf(0L) }
    var curIdx    by remember { mutableStateOf(-1) }
    var shuffle   by remember { mutableStateOf(false) }
    var repMode   by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    var drag      by remember { mutableStateOf(false) }
    var dragV     by remember { mutableStateOf(0f) }

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

    // El botón "atrás" cierra el buscador
    BackHandler(enabled = searching) {
        searching = false
        query = ""
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
            if (searching) {
                // Buscador desplegado (se abre al tocar la lupa)
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        placeholder = {
                            Text("Titulo, artista o album", color = CMuted, fontSize = 12.sp)
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
                    Spacer(Modifier.width(6.dp))
                    TapIcon(Icons.Rounded.Close, "Cerrar busqueda", CText, 44.dp, 24.dp) {
                        searching = false
                        query = ""
                    }
                }
            } else {
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
                    TapIcon(Icons.Rounded.Search, "Buscar", CText, 44.dp, 24.dp) {
                        searching = true
                    }
                }
            }

            when {
                // ── Estado: sin permiso ──
                !hasPerm -> PermCard(reqPerm)

                // ── Estado: biblioteca vacía ──
                songs.isEmpty() -> EmptyLib()

                // ── Estado: lista + mini reproductor abajo ──
                else -> {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f)
                            .padding(top = 6.dp, bottom = 8.dp)
                    ) {
                        items(filtered, key = { it.id }) { s ->
                            SongRow(s, curSong?.id == s.id) { playSong(s) }
                        }
                    }

                    if (curSong != null) {
                        MiniPlayer(
                            song = curSong,
                            isPlaying = isPlaying,
                            position = if (drag) dragV.toLong() else posMs,
                            duration = durMs,
                            shuffle = shuffle,
                            repeatMode = repMode,
                            onSeek = { drag = true; dragV = it },
                            onSeekDone = {
                                drag = false
                                controller?.seekTo(dragV.toLong())
                                posMs = dragV.toLong()
                            },
                            onPrev = {
                                val c = controller
                                if (c != null) {
                                    if (c.currentPosition > 4000) c.seekTo(0)
                                    else c.seekToPreviousMediaItem()
                                }
                            },
                            onToggle = { togglePlay() },
                            onNext = { controller?.seekToNextMediaItem() },
                            onShuffle = {
                                val c = controller
                                if (c != null) c.shuffleModeEnabled = !c.shuffleModeEnabled
                            },
                            onRepeat = {
                                val c = controller
                                if (c != null) {
                                    c.repeatMode = when (c.repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ────────────── MINI REPRODUCTOR (abajo) ──────────────
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    shuffle: Boolean,
    repeatMode: Int,
    onSeek: (Float) -> Unit,
    onSeekDone: () -> Unit,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E160F))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Carátula + título + controles
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(
                        listOf(Color(0xFF2C2117), Color(0xFF1B130D))
                    ))
            ) {
                if (song.albumArt != null) {
                    AsyncImage(song.albumArt, null, Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Rounded.MusicNote, null,
                        tint = CAmpber.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp).align(Alignment.Center))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = CText, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = CMuted, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TapIcon(Icons.Rounded.SkipPrevious, "Anterior", CText, 36.dp, 24.dp, onPrev)
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(CCream, CAmpber)))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    "Reproducir", tint = CDark, modifier = Modifier.size(24.dp)
                )
            }
            TapIcon(Icons.Rounded.SkipNext, "Siguiente", CText, 36.dp, 24.dp, onNext)
        }

        // Aleatorio · tiempo · barra delgada · tiempo · repetir
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TapIcon(Icons.Rounded.Shuffle, "Aleatorio",
                if (shuffle) CAmpber else CMuted, 28.dp, 18.dp, onShuffle)
            Text(fmtMs(position), color = CMuted, fontSize = 10.sp,
                modifier = Modifier.padding(start = 6.dp))
            ThinSeekBar(
                value = position.toFloat(),
                max = duration.toFloat(),
                onValueChange = onSeek,
                onValueChangeFinished = onSeekDone,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(fmtMs(duration), color = CMuted, fontSize = 10.sp,
                modifier = Modifier.padding(end = 6.dp))
            TapIcon(
                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                else Icons.Rounded.Repeat,
                "Repetir",
                if (repeatMode != Player.REPEAT_MODE_OFF) CAmpber else CMuted,
                28.dp, 18.dp, onRepeat
            )
        }
    }
}

// ────────────── BARRA DE PROGRESO DELGADA CON PUNTO ──────────────
@Composable
fun ThinSeekBar(
    value: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 3.dp,
    thumbRadius: Dp = 5.dp
) {
    val safeMax = if (max > 0f) max else 1f
    val frac = (value / safeMax).coerceIn(0f, 1f)
    val onChange by rememberUpdatedState(onValueChange)
    val onDone by rememberUpdatedState(onValueChangeFinished)
    val inactive = Color(0x33F4EFE6)

    Canvas(
        modifier
            .height(28.dp)
            .pointerInput(safeMax) {
                detectTapGestures { o ->
                    onChange((o.x / size.width).coerceIn(0f, 1f) * safeMax)
                    onDone()
                }
            }
            .pointerInput(safeMax) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        onChange((o.x / size.width).coerceIn(0f, 1f) * safeMax)
                    },
                    onDragEnd = { onDone() },
                    onDragCancel = { onDone() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onChange((change.position.x / size.width).coerceIn(0f, 1f) * safeMax)
                    }
                )
            }
    ) {
        val cy = size.height / 2f
        val th = trackHeight.toPx()
        val r = thumbRadius.toPx()
        val x = frac * size.width
        val corner = CornerRadius(th / 2f, th / 2f)

        // Pista (delgada)
        drawRoundRect(
            color = inactive,
            topLeft = Offset(0f, cy - th / 2f),
            size = Size(size.width, th),
            cornerRadius = corner
        )
        // Parte recorrida
        drawRoundRect(
            color = CAmpber,
            topLeft = Offset(0f, cy - th / 2f),
            size = Size(x, th),
            cornerRadius = corner
        )
        // Punto
        drawCircle(
            color = CCream,
            radius = r,
            center = Offset(x.coerceIn(r, size.width - r), cy)
        )
    }
}

// ────────────── BOTÓN DE ÍCONO COMPACTO ──────────────
@Composable
private fun TapIcon(
    icon: ImageVector,
    desc: String,
    tint: Color,
    boxSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(boxSize).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(iconSize))
    }
}
