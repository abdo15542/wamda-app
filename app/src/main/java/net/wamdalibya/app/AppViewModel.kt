package net.wamdalibya.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    object Home : Screen()
    data class Article(val slug: String) : Screen()
}

/**
 * Holds feed state.
 *
 * Launch is CACHE-FIRST: the saved index (and announcements) are shown
 * instantly, then a background refresh swaps in fresh data — the list no
 * longer waits on a 463 KB download before appearing. The loading spinner
 * only ever shows when there is no cache at all.
 *
 * Refresh fetches the index and the announcements CONCURRENTLY.
 * The list is paginated (40 at a time) so only the visible page is
 * composed; search/category filtering still runs over the full list.
 *
 * Likes are per-device (SharedPreferences, keyed by article slug).
 * There is no backend, so no shared count is invented.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val PAGE_SIZE = 40
    }

    private val repo = FeedRepository(application.applicationContext, BuildConfig.FEED_BASE_URL)
    private val likesPrefs =
        application.getSharedPreferences("wamda_likes", Context.MODE_PRIVATE)

    var articles by mutableStateOf<List<FeedArticle>>(emptyList())
        private set
    var categories by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    /** True when the list on screen came from the cache (no network). */
    var isOffline by mutableStateOf(false)
        private set
    var announcements by mutableStateOf(AnnouncementsData(0, "", "", emptyList()))
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var articleDetail by mutableStateOf<ArticleDetail?>(null)
        private set
    var articleLoading by mutableStateOf(false)
        private set
    var articleError by mutableStateOf(false)
        private set
    /** How many of the (filtered) articles are currently shown. */
    var visibleCount by mutableStateOf(PAGE_SIZE)
        private set
    /** Slugs the user liked on this device. */
    var likedSlugs by mutableStateOf<Set<String>>(
        likesPrefs.getStringSet("liked", emptySet())?.toSet() ?: emptySet()
    )
        private set

    init {
        loadFromCache()
        refresh()
    }

    private fun updateArticles(list: List<FeedArticle>) {
        articles = list
        categories = list
            .map { it.category to it.categoryName }
            .distinct()
            .filter { it.first.isNotEmpty() }
    }

    /** Show whatever is saved on disk right now — no network wait. */
    private fun loadFromCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = repo.fetchIndexCached()
            val cachedAnn = repo.fetchAnnouncementsCached()
            withContext(Dispatchers.Main) {
                if (cached != null) updateArticles(cached)
                if (cachedAnn.latestVersionCode != 0 || cachedAnn.items.isNotEmpty()) {
                    announcements = cachedAnn
                }
            }
        }
    }

    fun refresh() {
        if (isLoading) return
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            // Index + announcements in parallel, not one after the other.
            val freshDeferred = async { repo.fetchIndex() }
            val annDeferred = async { repo.fetchAnnouncements() }
            val fresh = freshDeferred.await()
            val ann = annDeferred.await()
            withContext(Dispatchers.Main) {
                if (fresh != null) {
                    updateArticles(fresh)
                    isOffline = false
                } else {
                    // Network failed: keep showing the cached list (if any)
                    // and mark it offline so the UI says so.
                    isOffline = true
                }
                // Announcements are best-effort: keep the old copy when the
                // fetch returns nothing new.
                if (ann.latestVersionCode != 0 || ann.items.isNotEmpty()) {
                    announcements = ann
                }
                isLoading = false
            }
        }
    }

    fun loadMore() {
        if (visibleCount < articles.size) {
            visibleCount += PAGE_SIZE
        }
    }

    fun resetPaging() {
        visibleCount = PAGE_SIZE
    }

    fun isLiked(slug: String): Boolean = slug in likedSlugs

    fun toggleLike(slug: String) {
        val next = if (slug in likedSlugs) likedSlugs - slug else likedSlugs + slug
        likedSlugs = next
        likesPrefs.edit().putStringSet("liked", next).apply()
    }

    fun openArticle(slug: String) {
        screen = Screen.Article(slug)
        articleDetail = null
        articleError = false
        articleLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            val detail = repo.fetchArticle(slug)
            withContext(Dispatchers.Main) {
                articleDetail = detail
                articleError = detail == null
                articleLoading = false
            }
        }
    }

    fun goHome() {
        screen = Screen.Home
        articleDetail = null
        articleError = false
    }
}
