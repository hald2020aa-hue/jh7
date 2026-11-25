package eu.kanade.tachiyomi.ui.download

import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.ui.enhanced.estimatedTransferSeconds
import eu.kanade.tachiyomi.ui.enhanced.neonChapterPaletteIndex
import java.util.Locale

/** Read-only snapshot of queue state. Transfer bytes are observed separately via StateFlow. */
data class NeonDownloadRow(
    val download: Download,
    val name: String,
    val chapterName: String,
    val percentage: Int,
    val downloadedPages: Int,
    val totalPages: Int?,
    val paused: Boolean,
    val status: Download.State,
    val completedInCache: Int,
)

private val background = Color(0xFF050613)
private val panel = Color(0xFF12142C)
private val foreground = Color(0xFFFBF7FF)
private val muted = Color(0xFFB9B2D0)
private val cyan = Color(0xFF36E6FF)
private val pink = Color(0xFFFC39ED)
private val palettes = listOf(
    listOf(Color(0xFF04D5E6), Color(0xFF2275FC), Color(0xFF983BEC), Color(0xFFF11DBF)),
    listOf(Color(0xFFFF4265), Color(0xFFE11DA6), Color(0xFF9655F2)),
    listOf(Color(0xFFFF843A), Color(0xFFFFC741), Color(0xFFFF4B9B)),
    listOf(Color(0xFFFFDA51), Color(0xFFF3B229), Color(0xFFFF7633)),
    listOf(Color(0xFF13DFF3), Color(0xFF04A9D8), Color(0xFF3466FF)),
    listOf(Color(0xFFFFF463), Color(0xFFEACA2F), Color(0xFFED9E24)),
    listOf(Color(0xFF34ED98), Color(0xFF19CACA), Color(0xFF268DE6)),
)

private fun colorsFor(row: NeonDownloadRow): List<Color> =
    palettes[neonChapterPaletteIndex(row.download.chapter.chapter_number, palettes.size)]

private fun bytesString(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
private fun NeonProgressTrack(value: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    val fraction = value.coerceIn(0f, 1f)
    Box(
        modifier = modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF3E2B5E)),
    ) {
        Box(
            Modifier.fillMaxWidth(fraction).height(14.dp)
                .background(Brush.horizontalGradient(colors), RoundedCornerShape(20.dp)),
        )
    }
}

@Composable
private fun DownloadSummaryCard(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.8f)),
        colors = CardDefaults.cardColors(containerColor = panel),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value.toString(), color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Dev-only Compose download center. Uses real queue items and byte telemetry; it never invents
 * sizes, speed or ETA. Existing download database, cache and WorkManager remain untouched.
 */
@Composable
fun NeonDownloadPanel(
    rows: List<NeonDownloadRow>,
    isRunning: Boolean,
    onToggleAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onPause: (Download) -> Unit,
    onResume: (Download) -> Unit,
    onDelete: (Download) -> Unit,
    onPrioritize: (Download) -> Unit,
    onConcurrentSources: (Int) -> Unit,
    onClose: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    var showDeleteAll by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("all") }
    val downloadingCount = rows.count { it.status == Download.State.DOWNLOADING && !it.paused }
    val waitingCount = rows.count { it.status == Download.State.QUEUE }
    val pausedCount = rows.count { it.paused }
    val failedCount = rows.count { it.status == Download.State.ERROR }
    val completedCount = rows.count { it.status == Download.State.DOWNLOADED }
    val visibleRows = when (selectedFilter) {
        "downloading" -> rows.filter { it.status == Download.State.DOWNLOADING && !it.paused }
        "waiting" -> rows.filter { it.status == Download.State.QUEUE }
        "paused" -> rows.filter { it.paused }
        "failed" -> rows.filter { it.status == Download.State.ERROR }
        "completed" -> rows.filter { it.status == Download.State.DOWNLOADED }
        else -> rows
    }
    Column(
        Modifier.fillMaxSize().background(background).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // V23 visual pass: reference-inspired art is decorative only; every manga cover,
        // speed, transfer byte count and action continues to come from the live download rows.
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            shape = RoundedCornerShape(23.dp),
            border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(cyan, Color(0xFF8956FF), pink))),
            colors = CardDefaults.cardColors(containerColor = panel),
        ) {
            Box(Modifier.fillMaxWidth().height(154.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.v23_header_art),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            listOf(Color(0xF4070B20), Color(0xCE101027), Color(0x5A160D35)),
                        ),
                    ),
                )
                Column(
                    Modifier.align(Alignment.CenterStart).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = cyan, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("التنزيلات", color = foreground, fontWeight = FontWeight.Black, fontSize = 26.sp)
                    }
                    Text("إدارة فصول المانجا المحفوظة وقائمة الانتظار", color = foreground, fontSize = 12.sp)
                    Text("قائمة التنزيل", color = pink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("${rows.size} فصلًا في قائمة التنزيل", color = cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (onClose != null) {
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = foreground)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            DownloadSummaryCard("قيد التنزيل", downloadingCount, cyan, Modifier.weight(1f))
            DownloadSummaryCard("في الانتظار", waitingCount, pink, Modifier.weight(1f))
            DownloadSummaryCard("مكتمل", completedCount, Color(0xFF36E2B0), Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "all" to "الكل (${rows.size})",
                "downloading" to "قيد التنزيل ($downloadingCount)",
                "waiting" to "في الانتظار ($waitingCount)",
                "paused" to "متوقف ($pausedCount)",
                "failed" to "متعثر ($failedCount)",
                "completed" to "مكتمل ($completedCount)",
            ).forEach { (key, label) ->
                FilterChip(
                    selected = selectedFilter == key,
                    onClick = { selectedFilter = key },
                    label = { Text(label, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF1A1A38),
                        labelColor = foreground,
                        selectedContainerColor = Color(0xFF5E2184),
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        if (rows.isEmpty() || visibleRows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (rows.isEmpty()) "لا توجد فصول قيد التنزيل" else "لا توجد فصول ضمن هذا التصنيف",
                    color = muted,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                // Stable status order. The rows are actual manager queue records, not samples.
                val groups = listOf(
                    "قيد التنزيل" to visibleRows.filter { it.status == Download.State.DOWNLOADING && !it.paused },
                    "في الانتظار" to visibleRows.filter { it.status == Download.State.QUEUE && !it.paused },
                    "متوقف" to visibleRows.filter { it.paused },
                    "مكتمل" to visibleRows.filter { it.status == Download.State.DOWNLOADED && !it.paused },
                    "متعثر" to visibleRows.filter { it.status == Download.State.ERROR && !it.paused },
                )
                groups.forEach { (label, chapters) ->
                    if (chapters.isNotEmpty()) {
                        item(key = "status_$label") {
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("$label (${chapters.size})", color = if (label == "مكتمل") Color(0xFF36E2B0) else pink,
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF654189)))
                            }
                        }
                        items(chapters, key = { "${label}_${it.download.chapter.id ?: it.hashCode()}" }) { row ->
                            NeonChapterDownloadRow(row, onPause, onResume, onDelete, onPrioritize)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onToggleAll,
                colors = ButtonDefaults.buttonColors(containerColor = pink),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (isRunning) "إيقاف مؤقت" else "استئناف")
            }
            IconButton(onClick = { showDeleteAll = true }) {
                Icon(Icons.Default.Delete, contentDescription = "إلغاء التنزيلات", tint = pink)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "إعدادات التنزيل", tint = cyan)
            }
        }
    }
    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("إلغاء قائمة التنزيل؟") },
            text = { Text("سيُلغى طابور التنزيل. لا تُحذف الفصول التي اكتمل تنزيلها.") },
            confirmButton = { TextButton(onClick = { showDeleteAll = false; onDeleteAll() }) { Text("إلغاء القائمة") } },
            dismissButton = { TextButton(onClick = { showDeleteAll = false }) { Text("رجوع") } },
        )
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("إعدادات التنزيل") },
            text = {
                Column {
                    Text("عدد المصادر التي تنزل بالتزامن:")
                    listOf(1, 2, 3).forEach { number ->
                        TextButton(onClick = { onConcurrentSources(number); showSettings = false }) {
                            Text("$number ${if (number == 1) "مصدر" else "مصادر"}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("إغلاق") } },
        )
    }
    }
}

@Composable
private fun MangaNeonDownloadCard(
    current: NeonDownloadRow,
    completed: Int,
    queued: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val stats by current.download.transferStats.collectAsState()
    val colors = colorsFor(current)
    Card(
        border = BorderStroke(1.5.dp, Brush.horizontalGradient(colors)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = panel),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(current.name, color = foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val statusLabel = when {
                    current.paused -> "متوقف"
                    current.status == Download.State.DOWNLOADING -> "جاري التنزيل"
                    current.status == Download.State.QUEUE -> "في الانتظار"
                    current.status == Download.State.ERROR -> "متعثر"
                    current.status == Download.State.DOWNLOADED -> "مكتمل"
                    else -> "قيد الفحص"
                }
                Text(statusLabel, color = colors.last(), fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$completed / ${completed + queued} فصول", color = cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("${current.percentage}%", color = foreground, fontWeight = FontWeight.Bold, fontSize = 23.sp)
            }
            NeonProgressTrack(current.percentage / 100f, colors)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${current.downloadedPages} / ${current.totalPages?.toString() ?: "؟"} صفحات", color = muted, fontSize = 11.sp)
                Text("${bytesString(stats.receivedBytes)} / ${stats.knownChapterBytes?.let(::bytesString) ?: "غير معروف"}", color = muted, fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${bytesString(stats.speedBytesPerSecond)}/s · المتبقي: ${estimatedTransferSeconds(stats.knownChapterBytes, stats.receivedBytes, stats.speedBytesPerSecond)
                        ?.let { "$it ث" } ?: "غير معروف"}", color = muted, fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = if (current.paused || current.status != Download.State.DOWNLOADING) onResume else onPause) {
                    Icon(if (current.paused || current.status != Download.State.DOWNLOADING) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (current.paused || current.status != Download.State.DOWNLOADING) "استئناف الفصل" else "إيقاف الفصل", tint = cyan)
                }
            }
        }
    }
}

@Composable
private fun NeonChapterDownloadRow(
    row: NeonDownloadRow,
    onPause: (Download) -> Unit,
    onResume: (Download) -> Unit,
    onDelete: (Download) -> Unit,
    onPrioritize: (Download) -> Unit,
) {
    val stats by row.download.transferStats.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val active = row.status == Download.State.DOWNLOADING && !row.paused
    val colors = if (active) colorsFor(row) else listOf(Color(0xFF518DBB), Color(0xFF9E53D1))
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (active) colors.last() else Color(0xFF4D507D)),
        colors = CardDefaults.cardColors(containerColor = panel),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        loadManga(row.download.manga)
                    }
                },
                update = { it.loadManga(row.download.manga) },
                modifier = Modifier.size(width = 58.dp, height = 76.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(row.name, fontSize = 15.sp, color = foreground, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.chapterName, fontSize = 12.sp, color = muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${row.percentage}%", fontSize = 13.sp, color = cyan, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (active) "${bytesString(stats.speedBytesPerSecond)}/s" else
                            when { row.paused -> "متوقف"; row.status == Download.State.DOWNLOADED -> "مكتمل";
                                row.status == Download.State.ERROR -> "متعثر"; else -> "في الانتظار" },
                        fontSize = 11.sp, color = if (active) cyan else muted,
                    )
                }
                NeonProgressTrack(row.percentage / 100f, colors)
                Text(
                    "${row.downloadedPages}/${row.totalPages?.toString() ?: "؟"} صفحة  ·  ${stats.knownChapterBytes?.let(::bytesString) ?: "الحجم غير معروف"}",
                    fontSize = 10.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    if (row.paused || row.status != Download.State.DOWNLOADING) onResume(row.download)
                    else onPause(row.download)
                }) {
                    Icon(
                        if (row.paused || row.status != Download.State.DOWNLOADING) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "إيقاف أو استئناف الفصل", tint = cyan,
                    )
                }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "خيارات الفصل", tint = foreground)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("نقل إلى الأعلى") }, onClick = { expanded = false; onPrioritize(row.download) })
                        DropdownMenuItem(text = { Text("إلغاء تنزيل الفصل") }, onClick = { expanded = false; confirmDelete = true })
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("إلغاء تنزيل الفصل؟") },
            text = { Text(row.chapterName) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(row.download) }) { Text("إلغاء التنزيل") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("رجوع") } },
        )
    }
}
