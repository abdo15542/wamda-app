package net.wamdalibya.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    object Home : Screen()
    data class Article(val slug: String) : Screen()
}

enum class Tab { NEWS, ANNOUNCEMENTS }

/**
 * Holds feed state. Refresh is network-first with the disk cache as the
 * offline fallback: the app opens instantly from cache, then updates.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FeedRepository(application.applicationContext, BuildConfig.FEED_BASE_URL)

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
    var tab by mutableStateOf(Tab.NEWS)
        private set
    var articleDetail by mutableStateOf<ArticleDetail?>(null)
        private set
    var articleLoading by mutableStateOf(false)
        private set
    var articleError by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        if (isLoading) return
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            val fresh = repo.fetchIndex()
            val ann = repo.fetchAnnouncements()
            withContext(Dispatchers.Main) {
                if (fresh != null) {
                    articles = fresh
                    categories = fresh
                        .map { it.category to it.categoryName }
                        .distinct()
                        .filter { it.first.isNotEmpty() }
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

    fun selectTab(t: Tab) {
        tab = t
    }
}
