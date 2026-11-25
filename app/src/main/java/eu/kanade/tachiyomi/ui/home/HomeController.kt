package eu.kanade.tachiyomi.ui.home

import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.databinding.HomeControllerBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.download.center.DownloadCenterActivity
import eu.kanade.tachiyomi.ui.enhanced.EnhancedReadingTheme
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.system.executeOnIO
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * MANGA VIRUS home dashboard. Every title and cover comes from the user's existing
 * library or reading history; this screen never inserts sample catalogue records.
 */
class HomeController : BaseController<HomeControllerBinding>(), RootSearchInterface, FloatingSearchInterface {
    private var libraryManga by mutableStateOf<List<Manga>>(emptyList())
    private var recentManga by mutableStateOf<List<Manga>>(emptyList())
    private var sourceCount by mutableIntStateOf(0)
    private var genreTags by mutableStateOf<List<String>>(emptyList())
    private var loading by mutableStateOf(true)

    private val db: DatabaseHelper by lazy { Injekt.get() }
    private val sourceManager: SourceManager by lazy { Injekt.get() }

    override fun createBinding(inflater: LayoutInflater) = HomeControllerBinding.inflate(inflater)

    override fun getTitle(): String? = view?.context?.getString(R.string.home)

    override fun getBigIcon(): Drawable? = activity?.let { ContextCompat.getDrawable(it, R.mipmap.ic_launcher) }

    override fun getSearchTitle(): String? = searchTitle(getTitle()?.lowercase())

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        (binding.homeCompose as ComposeView).setContent {
            EnhancedReadingTheme {
                HomeDashboard(
                    library = libraryManga,
                    recent = recentManga,
                    sourceCount = sourceCount,
                    genres = genreTags,
                    loading = loading,
                    onOpenManga = ::openManga,
                    onOpenLibrary = { navigateTo(R.id.nav_library) },
                    onOpenRecents = { navigateTo(R.id.nav_recents) },
                    onBrowse = { navigateTo(R.id.nav_browse) },
                    onDownloads = {
                        view.context.startActivity(Intent(view.context, DownloadCenterActivity::class.java))
                    },
                )
            }
        }
        viewScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val library = db.getFavoriteMangas().executeOnIO().sortedByDescending { it.date_added }
                    val recent = db.getRecentMangaLimit("", 0, false).executeOnIO()
                        .map { it.manga }
                        .distinctBy { it.id }
                        .take(12)
                    val genres = (library + recent)
                        .flatMap { it.getGenres().orEmpty() }
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(8)
                    Triple(library, recent, genres)
                }
                libraryManga = data.first
                recentManga = data.second
                genreTags = data.third
                sourceCount = sourceManager.getCatalogueSources().size
            } catch (error: Exception) {
                Timber.e(error, "Failed to load MANGA VIRUS home dashboard")
            } finally {
                loading = false
            }
        }
    }

    private fun openManga(manga: Manga) {
        router.pushController(MangaDetailsController(manga).withFadeTransaction())
    }

    private fun navigateTo(itemId: Int) {
        if (activity !is MainActivity) return
        val navigation = activityBinding?.bottomNav ?: activityBinding?.sideNav
        navigation?.selectedItemId = itemId
    }
}

private val homeBackground = Color(0xFF050613)
private val homePanel = Color(0xFF10152D)
private val homePanelRaised = Color(0xFF1D183A)
private val homeText = Color(0xFFF8F5FF)
private val homeMuted = Color(0xFFB2AEC9)
private val homeCyan = Color(0xFF36E6FF)
private val homeBlue = Color(0xFF6C8BFF)
private val homePurple = Color(0xFF9A4DFF)
private val homePink = Color(0xFFFC39ED)

@Composable
private fun HomeDashboard(
    library: List<Manga>,
    recent: List<Manga>,
    sourceCount: Int,
    genres: List<String>,
    loading: Boolean,
    onOpenManga: (Manga) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenRecents: () -> Unit,
    onBrowse: () -> Unit,
    onDownloads: () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val context = LocalContext.current
        // The concept's featured carousel navigates only actual user library/history items.
        val featuredPool = remember(library, recent) {
            (recent + library).distinctBy { it.id ?: it.url.hashCode().toLong() }.take(8)
        }
        var featuredIndex by remember { mutableIntStateOf(0) }
        val activeFeaturedIndex = if (featuredPool.isEmpty()) 0 else featuredIndex.mod(featuredPool.size)
        val featured = featuredPool.getOrNull(activeFeaturedIndex)
        var selectedGenre by remember { mutableStateOf<String?>(null) }
        val visibleLibrary = remember(library, selectedGenre) {
            if (selectedGenre == null) library else library.filter { manga ->
                manga.getGenres().orEmpty().any { genre -> genre.equals(selectedGenre, ignoreCase = true) }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(homeBackground),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF142A49), Color(0xFF17152F), Color(0xFF30143B))))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AndroidView(
                        factory = { imageContext ->
                            ImageView(imageContext).apply {
                                setImageResource(R.mipmap.ic_launcher)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                contentDescription = context.getString(R.string.app_name)
                            }
                        },
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("MANGA VIRUS", color = homeCyan, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text(context.getString(R.string.home), color = homeText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(context.getString(R.string.continue_reading), color = homeMuted, fontSize = 11.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = Color(0xFF281744),
                        border = BorderStroke(1.dp, homePink.copy(alpha = 0.8f)),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(library.size.toString(), color = homeText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text(context.getString(R.string.library), color = homeMuted, fontSize = 10.sp)
                        }
                    }
                }
            }

            item {
                if (featured != null) {
                    HomeFeaturedCard(
                        manga = featured,
                        isContinueReading = recent.any { it.id == featured.id },
                        currentIndex = activeFeaturedIndex,
                        totalCount = featuredPool.size,
                        onPrevious = { if (featuredPool.isNotEmpty()) featuredIndex = (activeFeaturedIndex + featuredPool.size - 1) % featuredPool.size },
                        onNext = { if (featuredPool.isNotEmpty()) featuredIndex = (activeFeaturedIndex + 1) % featuredPool.size },
                        onSelect = { position -> featuredIndex = position },
                        onClick = { onOpenManga(featured) },
                    )
                } else {
                    HomeEmptyCard(loading = loading, onBrowse = onBrowse)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    HomeStatCard(context.getString(R.string.library), library.size.toString(), homeCyan, Modifier.weight(1f))
                    HomeStatCard(context.getString(R.string.continue_reading), recent.size.toString(), homePink, Modifier.weight(1f))
                    HomeStatCard(context.getString(R.string.browse), sourceCount.toString(), homePurple, Modifier.weight(1f))
                }
            }

            if (genres.isNotEmpty()) {
                item {
                    HomeSectionHeader(context.getString(R.string.home_genres_title), genres.size, null)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 3.dp),
                    ) {
                        items(genres) { genre ->
                            HomeGenreChip(genre, genres.indexOf(genre), selectedGenre == genre) {
                                selectedGenre = if (selectedGenre == genre) null else genre
                            }
                        }
                    }
                }
            }

            item {
                HomeSectionHeader(context.getString(R.string.browse), null, null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    HomeAction(context.getString(R.string.library), homePurple, Modifier.weight(1f), onOpenLibrary)
                    HomeAction(context.getString(R.string.updates), homePink, Modifier.weight(1f), onOpenRecents)
                    HomeAction(context.getString(R.string.browse), homeCyan, Modifier.weight(1f), onBrowse)
                    HomeAction(context.getString(R.string.downloads), homeBlue, Modifier.weight(1f), onDownloads)
                }
            }

            item {
                HomeSectionHeader(context.getString(R.string.continue_reading), recent.size.takeIf { it > 0 }, onOpenRecents)
            }
            if (recent.isEmpty()) {
                item { HomeEmptyHint(if (loading) context.getString(R.string.loading) else context.getString(R.string.home_no_recent)) }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 3.dp)) {
                        items(recent, key = { it.id ?: it.url.hashCode().toLong() }) { manga ->
                            HomeMangaPoster(manga, onClick = { onOpenManga(manga) })
                        }
                    }
                }
            }

            item {
                HomeSectionHeader(context.getString(R.string.library), visibleLibrary.size.takeIf { it > 0 }, onOpenLibrary)
            }
            if (visibleLibrary.isEmpty()) {
                item { HomeEmptyHint(if (loading) context.getString(R.string.loading) else context.getString(R.string.home_empty_library)) }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 3.dp)) {
                        items(visibleLibrary.take(18), key = { it.id ?: it.url.hashCode().toLong() }) { manga ->
                            HomeMangaPoster(manga, onClick = { onOpenManga(manga) })
                        }
                    }
                }
            }

            // The second discovery rail is built from genuine recently-added library records.
            // The existing database query sorts libraryManga by date_added (never mock results).
            if (library.isNotEmpty() && selectedGenre == null) {
                item { HomeSectionHeader("أُضيف مؤخرًا", library.size, onOpenLibrary) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 3.dp)) {
                        items(library.take(12), key = { it.id ?: it.url.hashCode().toLong() }) { manga ->
                            HomeMangaPoster(manga, onClick = { onOpenManga(manga) })
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onBrowse),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, homeCyan.copy(alpha = 0.65f)),
                    colors = CardDefaults.cardColors(containerColor = homePanel),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(context.getString(R.string.browse), color = homeText, fontWeight = FontWeight.Bold)
                            Text(context.getString(R.string.home_sources_available, sourceCount), color = homeMuted, fontSize = 12.sp)
                        }
                        Button(
                            onClick = onBrowse,
                            colors = ButtonDefaults.buttonColors(containerColor = homePurple, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(context.getString(R.string.browse))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
        colors = CardDefaults.cardColors(containerColor = homePanel),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, color = homeMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeGenreChip(label: String, index: Int, selected: Boolean, onClick: () -> Unit) {
    val accents = listOf(homeCyan, homePink, homePurple, homeBlue)
    val accent = accents[index % accents.size]
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) accent.copy(alpha = 0.24f) else homePanelRaised,
        border = BorderStroke(if (selected) 2.dp else 1.dp, accent.copy(alpha = if (selected) 1f else 0.78f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            // Icons are semantic decoration based on the real genre label, not new catalogue metadata.
            val icon = when {
                label.contains("action", ignoreCase = true) || label.contains("أكشن") -> "⚔"
                label.contains("romance", ignoreCase = true) || label.contains("رومان") -> "♡"
                label.contains("fantasy", ignoreCase = true) || label.contains("فانتاز") -> "✧"
                label.contains("sci", ignoreCase = true) || label.contains("خيال علم") -> "◎"
                label.contains("adventure", ignoreCase = true) || label.contains("مغام") -> "◇"
                label.contains("super", ignoreCase = true) || label.contains("خارقة") -> "✴"
                else -> "✦"
            }
            Text(icon, color = accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(label, color = homeText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeAction(label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(66.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = homePanelRaised,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = homeText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomeFeaturedCard(
    manga: Manga,
    isContinueReading: Boolean,
    currentIndex: Int,
    totalCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (Int) -> Unit,
    onClick: () -> Unit,
) {
    // V22: cinematic artwork comes from the real Manga cover via the existing image loader.
    // It deliberately never uses titles, characters, covers or counters from the concept art.
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, Brush.horizontalGradient(listOf(homeCyan, homePurple, homePink))),
        colors = CardDefaults.cardColors(containerColor = homePanel),
    ) {
        Box(Modifier.fillMaxWidth().height(264.dp)) {
            MangaCover(manga, Modifier.fillMaxSize().alpha(0.55f))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color(0xE4080920), Color(0xC5111233), Color(0xA6200D42), Color(0x42130727)),
                    ),
                ),
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 17.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Surface(
                    color = homePink.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, homePink.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        if (isContinueReading) LocalContext.current.getString(R.string.continue_reading) else LocalContext.current.getString(R.string.library),
                        color = homeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(manga.title, color = homeText, fontSize = 28.sp, fontWeight = FontWeight.Black,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val subtitle = manga.author?.takeIf { it.isNotBlank() } ?: manga.artist?.takeIf { it.isNotBlank() }
                if (subtitle != null) Text(subtitle, color = homeMuted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = homePink, contentColor = Color.White),
                        shape = RoundedCornerShape(25.dp),
                    ) {
                        Text(LocalContext.current.getString(R.string.open), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("MANGA VIRUS", color = homeCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            if (totalCount > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp).clickable(onClick = onPrevious),
                        shape = RoundedCornerShape(50), color = Color(0xA7161033),
                        border = BorderStroke(1.dp, homeCyan.copy(alpha = 0.65f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("‹", color = homeText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        modifier = Modifier.size(34.dp).clickable(onClick = onNext),
                        shape = RoundedCornerShape(50), color = Color(0xA7161033),
                        border = BorderStroke(1.dp, homePink.copy(alpha = 0.65f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("›", color = homeText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(totalCount) { index ->
                        Surface(
                            modifier = Modifier.size(if (index == currentIndex) 9.dp else 6.dp)
                                .clickable { onSelect(index) },
                            shape = RoundedCornerShape(50),
                            color = if (index == currentIndex) homeCyan else homePurple.copy(alpha = 0.64f),
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeEmptyCard(loading: Boolean, onBrowse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, homePurple.copy(alpha = 0.75f)),
        colors = CardDefaults.cardColors(containerColor = homePanel),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MANGA VIRUS", color = homeCyan, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                if (loading) LocalContext.current.getString(R.string.loading) else LocalContext.current.getString(R.string.home_empty_library),
                color = homeText,
                fontSize = 14.sp,
            )
            if (!loading) {
                Button(onClick = onBrowse, colors = ButtonDefaults.buttonColors(containerColor = homePurple)) {
                    Text(LocalContext.current.getString(R.string.browse))
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String, count: Int?, onSeeAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = homeText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (count != null) Text(count.toString(), color = homeCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (onSeeAll != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                LocalContext.current.getString(R.string.home_see_all),
                color = homePink,
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onSeeAll).padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun HomeMangaPoster(manga: Manga, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(126.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, homePurple.copy(alpha = 0.85f)),
        colors = CardDefaults.cardColors(containerColor = homePanelRaised),
    ) {
        Box(Modifier.fillMaxWidth().height(174.dp)) {
            MangaCover(manga, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x10040815), Color(0xF006081C)),
                    ),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(manga.title, color = homeText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MangaCover(manga: Manga, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = manga.title
                loadManga(manga)
            }
        },
        update = { it.loadManga(manga) },
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(homePanelRaised),
    )
}

@Composable
private fun HomeEmptyHint(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(homePanel).padding(14.dp),
    ) {
        Text(text, color = homeMuted, fontSize = 12.sp)
    }
}
