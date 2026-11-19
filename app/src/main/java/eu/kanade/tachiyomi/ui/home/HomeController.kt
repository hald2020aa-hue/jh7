package eu.kanade.tachiyomi.ui.home

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.databinding.HomeControllerBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.BaseController
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
 * Home dashboard backed only by the user's existing library and reading history.
 * It does not fetch, seed, or insert sample manga and leaves all source flows unchanged.
 */
class HomeController : BaseController<HomeControllerBinding>(), RootSearchInterface, FloatingSearchInterface {
    private var libraryManga by mutableStateOf<List<Manga>>(emptyList())
    private var recentManga by mutableStateOf<List<Manga>>(emptyList())
    private var sourceCount by mutableIntStateOf(0)
    private var loading by mutableStateOf(true)

    private val db: DatabaseHelper by lazy { Injekt.get() }
    private val sourceManager: SourceManager by lazy { Injekt.get() }

    override fun createBinding(inflater: LayoutInflater) = HomeControllerBinding.inflate(inflater)

    override fun getTitle(): String? = view?.context?.getString(R.string.home)

    override fun getSearchTitle(): String? = searchTitle(getTitle()?.lowercase())

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        binding.homeCompose.setContent {
            EnhancedReadingTheme {
                HomeDashboard(
                    library = libraryManga,
                    recent = recentManga,
                    sourceCount = sourceCount,
                    loading = loading,
                    onOpenManga = ::openManga,
                    onOpenLibrary = { navigateTo(R.id.nav_library) },
                    onOpenRecents = { navigateTo(R.id.nav_recents) },
                    onBrowse = { navigateTo(R.id.nav_browse) },
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
                    library to recent
                }
                libraryManga = data.first
                recentManga = data.second
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

private val homeBackground = Color(0xFF070B16)
private val homePanel = Color(0xFF101A2C)
private val homePanelRaised = Color(0xFF17263D)
private val homeText = Color(0xFFF4F7FF)
private val homeMuted = Color(0xFFA7B4C8)
private val homeCyan = Color(0xFF48DDF2)
private val homeBlue = Color(0xFF587CFF)
private val homePurple = Color(0xFF9B69FF)
private val homePink = Color(0xFFFF4FA3)

@Composable
private fun HomeDashboard(
    library: List<Manga>,
    recent: List<Manga>,
    sourceCount: Int,
    loading: Boolean,
    onOpenManga: (Manga) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenRecents: () -> Unit,
    onBrowse: () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(homeBackground),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(homePanelRaised, homePanel, Color(0xFF21172F))))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("MANGA VIRUS", color = homeCyan, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("${LocalContext.current.getString(R.string.home)} • ${LocalContext.current.getString(R.string.continue_reading)}", color = homeMuted, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(library.size.toString(), color = homePink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(LocalContext.current.getString(R.string.library), color = homeMuted, fontSize = 10.sp)
                    }
                }
            }

            item {
                val featured = recent.firstOrNull() ?: library.firstOrNull()
                if (featured != null) {
                    HomeFeaturedCard(featured, recent.isNotEmpty(), onClick = { onOpenManga(featured) })
                } else {
                    HomeEmptyCard(loading = loading, onBrowse = onBrowse)
                }
            }

            item {
                HomeSectionHeader(
                    title = LocalContext.current.getString(R.string.continue_reading),
                    count = recent.size,
                    onSeeAll = onOpenRecents,
                )
            }
            if (recent.isEmpty()) {
                item { HomeEmptyHint(LocalContext.current.getString(R.string.home_no_recent)) }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                        items(recent, key = { it.id ?: it.url.hashCode().toLong() }) { manga ->
                            HomeMangaPoster(manga, onClick = { onOpenManga(manga) })
                        }
                    }
                }
            }

            item {
                HomeSectionHeader(
                    title = LocalContext.current.getString(R.string.library),
                    count = library.size,
                    onSeeAll = onOpenLibrary,
                )
            }
            if (library.isEmpty()) {
                item { HomeEmptyHint(LocalContext.current.getString(R.string.home_empty_library)) }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                        items(library.take(12), key = { it.id ?: it.url.hashCode().toLong() }) { manga ->
                            HomeMangaPoster(manga, onClick = { onOpenManga(manga) })
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, homeCyan.copy(alpha = 0.45f)),
                    colors = CardDefaults.cardColors(containerColor = homePanel),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(LocalContext.current.getString(R.string.browse), color = homeText, fontWeight = FontWeight.Bold)
                            Text(LocalContext.current.getString(R.string.home_sources_available, sourceCount), color = homeMuted, fontSize = 12.sp)
                        }
                        Button(
                            onClick = onBrowse,
                            colors = ButtonDefaults.buttonColors(containerColor = homePurple, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(LocalContext.current.getString(R.string.browse))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFeaturedCard(manga: Manga, isContinueReading: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.2.dp, Brush.horizontalGradient(listOf(homeCyan, homeBlue, homePink))),
        colors = CardDefaults.cardColors(containerColor = homePanel),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF162A46), Color(0xFF111A2E), Color(0xFF26172F))))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    if (isContinueReading) "${LocalContext.current.getString(R.string.continue_reading)}" else LocalContext.current.getString(R.string.library),
                    color = homeCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(manga.title, color = homeText, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val subtitle = manga.author?.takeIf { it.isNotBlank() } ?: manga.artist?.takeIf { it.isNotBlank() }
                if (subtitle != null) Text(subtitle, color = homeMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = homePink, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(LocalContext.current.getString(R.string.open))
                }
            }
            MangaCover(manga, Modifier.size(width = 108.dp, height = 146.dp))
        }
    }
}

@Composable
private fun HomeEmptyCard(loading: Boolean, onBrowse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, homePurple.copy(alpha = 0.65f)),
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
private fun HomeSectionHeader(title: String, count: Int, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = homeText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(count.toString(), color = homeCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(
            LocalContext.current.getString(R.string.home_see_all),
            color = homePink,
            fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onSeeAll).padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun HomeMangaPoster(manga: Manga, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(112.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MangaCover(manga, Modifier.fillMaxWidth().height(148.dp))
        Text(manga.title, color = homeText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val subtitle = manga.author?.takeIf { it.isNotBlank() } ?: manga.artist?.takeIf { it.isNotBlank() }
        if (subtitle != null) Text(subtitle, color = homeMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
