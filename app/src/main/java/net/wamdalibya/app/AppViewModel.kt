package net.wamdalibya.app

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    object Home : Screen()
    data class Article(val slug: String) : Screen()
}

/**
 * Holds feed + social state.
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
 * SOCIAL (v3): likes and comments are SHARED through Firebase
 * (Firestore + Anonymous Auth, see SocialRepository). The heart AND the
 * count flip optimistically on tap — the UI shows the server value plus
 * a pending +1/-1 delta, so the number moves instantly; the server
 * listener confirms the count and clears the delta, so the count always
 * converges to the server value (a failed write rolls both back). The
 * per-device SharedPreferences set is only the local mirror of the
 * heart state, so the heart still shows correctly offline. The display
 * name for comments is chosen once by the user on the device and stored
 * locally — or taken from the Google profile when the user signs in
 * with Google (v3.1, see GoogleSignIn.kt).
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val PAGE_SIZE = 40
    }

    private val repo = FeedRepository(application.applicationContext, BuildConfig.FEED_BASE_URL)
    private val social = SocialRepository()
    private val likesPrefs =
        application.getSharedPreferences("wamda_likes", Context.MODE_PRIVATE)
    private val profilePrefs =
        application.getSharedPreferences("wamda_profile", Context.MODE_PRIVATE)

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
    /** Slugs the user liked on this device (local mirror of the server). */
    var likedSlugs by mutableStateOf<Set<String>>(
        likesPrefs.getStringSet("liked", emptySet())?.toSet() ?: emptySet()
    )
        private set

    // ---- Social state (Firebase) ----

    /** Anonymous uid, once signed in; null while offline at first launch. */
    var uid by mutableStateOf<String?>(null)
        private set
    /** Like/comment counts per slug, for the visible list page. */
    var listCounts by mutableStateOf<Map<String, ArticleSocial>>(emptyMap())
        private set
    /** Live counters for the open article. */
    var detailSocial by mutableStateOf(ArticleSocial())
        private set
    /** Live "did I like this" for the open article. */
    var detailLiked by mutableStateOf(false)
        private set
    /** Live comments for the open article, newest first. */
    var comments by mutableStateOf<List<AppComment>>(emptyList())
        private set
    var commentsLoading by mutableStateOf(false)
        private set
    /** Name shown with this device's comments; null until the user picks one. */
    var displayName by mutableStateOf<String?>(profilePrefs.getString("name", null))
        private set
    var commentPosting by mutableStateOf(false)
        private set
    var commentError by mutableStateOf(false)
        private set
    /** Google display name when signed in with Google; null while anonymous. */
    var googleDisplayName by mutableStateOf<String?>(null)
        private set
    /** True when the current Firebase user is a linked Google account. */
    var isGoogleSignedIn by mutableStateOf(false)
        private set

    /**
     * Optimistic like-count deltas per slug (+1 when the user just
     * liked, -1 when they just unliked). The UI shows serverCount +
     * delta, so the number moves the instant the heart is tapped; the
     * delta clears when the server value moves past the value seen at
     * tap time (see clearConfirmedDeltas) — the count always converges
     * to the server value and never double-counts.
     */
    private var likeDeltas by mutableStateOf<Map<String, Int>>(emptyMap())
    /** Server like count seen when each delta was created. */
    private var likeDeltaBase by mutableStateOf<Map<String, Long>>(emptyMap())

    private var socialRegs = mutableListOf<ListenerRegistration>()
    private var likedReg: ListenerRegistration? = null
    private var socialSlug: String? = null

    init {
        loadFromCache()
        refresh()
        // Silent anonymous sign-in (no login screen, no personal data).
        viewModelScope.launch(Dispatchers.IO) {
            val id = social.ensureSignedIn()
            withContext(Dispatchers.Main) {
                uid = id
                if (id != null) attachLikedListener()
                refreshGoogleState()
            }
        }
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

    // ---- Social: likes ----

    /** Counts for the visible page (batched reads; missing = 0). */
    fun fetchCountsFor(slugs: List<String>) {
        if (slugs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val fetched = social.fetchCounts(slugs)
            if (fetched.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    listCounts = listCounts + fetched
                    clearConfirmedDeltas(fetched.mapValues { it.value.likes })
                }
            }
        }
    }

    /** Count the UI shows for a slug: server value + any pending optimistic delta. */
    fun displaySocial(slug: String, server: ArticleSocial): ArticleSocial {
        val delta = likeDeltas[slug] ?: 0
        return if (delta == 0) server
        else server.copy(likes = (server.likes + delta).coerceAtLeast(0L))
    }

    /**
     * Drops optimistic deltas whose server value has moved past the value
     * seen at tap time — the server has incorporated the write (or a
     * concurrent like/unlike), so the plain server count is now correct.
     * Called on every server update (detail listener + list batch reads).
     * Must run on the Main thread (it mutates State).
     */
    private fun clearConfirmedDeltas(server: Map<String, Long>) {
        if (likeDeltas.isEmpty()) return
        var nextDeltas = likeDeltas
        var nextBase = likeDeltaBase
        var changed = false
        for ((slug, _) in likeDeltas) {
            val base = likeDeltaBase[slug]
            val now = server[slug]
            if (base != null && now != null && now != base) {
                nextDeltas = nextDeltas - slug
                nextBase = nextBase - slug
                changed = true
            }
        }
        if (changed) {
            likeDeltas = nextDeltas
            likeDeltaBase = nextBase
        }
    }

    /** Server like count currently on screen for a slug (0 when unknown). */
    private fun serverLikesFor(slug: String): Long =
        if (slug == socialSlug) detailSocial.likes
        else listCounts[slug]?.likes ?: 0L

    /**
     * Shared like toggle. The heart AND the count flip optimistically —
     * the count shows server + pending delta, so the number moves the
     * instant the heart is tapped, like the big apps; the write goes to
     * Firestore in the background and the delta clears when the server
     * value confirms it. When the write fails (offline), BOTH flip back
     * — the local state is only a mirror, never the truth.
     */
    fun toggleLike(slug: String) {
        val wasLiked = slug in likedSlugs
        val currentUid = uid
        val prevDelta = likeDeltas[slug] ?: 0
        val prevBase = likeDeltaBase[slug]
        viewModelScope.launch(Dispatchers.IO) {
            val signedIn = currentUid ?: social.ensureSignedIn()
            if (signedIn == null) return@launch
            withContext(Dispatchers.Main) {
                if (uid == null) {
                    uid = signedIn
                    attachLikedListener()
                }
            }
            val id = signedIn
            withContext(Dispatchers.Main) {
                // Optimistic heart + count, together.
                val next = if (wasLiked) likedSlugs - slug else likedSlugs + slug
                likedSlugs = next
                likesPrefs.edit().putStringSet("liked", next).apply()
                val step = if (wasLiked) -1 else 1
                val newDelta = prevDelta + step
                if (newDelta == 0) {
                    likeDeltas = likeDeltas - slug
                    likeDeltaBase = likeDeltaBase - slug
                } else {
                    val base = prevBase ?: serverLikesFor(slug)
                    likeDeltas = likeDeltas + (slug to newDelta)
                    likeDeltaBase = likeDeltaBase + (slug to base)
                }
            }
            val result = social.toggleLike(slug, id)
            withContext(Dispatchers.Main) {
                if (result == null) {
                    // Failed (offline): revert heart AND count.
                    val reverted = if (wasLiked) likedSlugs + slug else likedSlugs - slug
                    likedSlugs = reverted
                    likesPrefs.edit().putStringSet("liked", reverted).apply()
                    if (prevDelta == 0) {
                        likeDeltas = likeDeltas - slug
                        likeDeltaBase = likeDeltaBase - slug
                    } else {
                        likeDeltas = likeDeltas + (slug to prevDelta)
                        if (prevBase != null) {
                            likeDeltaBase = likeDeltaBase + (slug to prevBase)
                        }
                    }
                } else {
                    // Reconcile the heart with the server's answer.
                    // The count delta stays until the server value
                    // confirms it — see clearConfirmedDeltas.
                    val reconciled = if (result) likedSlugs + slug else likedSlugs - slug
                    likedSlugs = reconciled
                    likesPrefs.edit().putStringSet("liked", reconciled).apply()
                }
            }
        }
    }

    private fun setLikedLocal(slug: String, liked: Boolean) {
        val next = if (liked) likedSlugs + slug else likedSlugs - slug
        if (next != likedSlugs) {
            likedSlugs = next
            likesPrefs.edit().putStringSet("liked", next).apply()
        }
    }

    // ---- Social: comments ----

    fun saveDisplayName(name: String) {
        val clean = name.trim().take(30)
        if (clean.isEmpty()) return
        displayName = clean
        profilePrefs.edit().putString("name", clean).apply()
    }

    // ---- Social: Google Sign-In (v3.1) ----

    /** Re-reads the Firebase user: Google name/state after any sign-in/out. */
    fun refreshGoogleState() {
        val google = social.googleUser()
        isGoogleSignedIn = google != null
        googleDisplayName = google?.second
        if (google != null) maybePrefillNameFromGoogle(google.second)
    }

    /**
     * One-tap Google sign-in (called from the account dialog). Links the
     * Google account to the current anonymous user, so the uid — and all
     * likes/comments — survive. onResult(true) = signed in.
     */
    fun requestGoogleSignIn(activity: Activity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = signInWithGoogle(activity)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    uid = result.uid
                    refreshGoogleState()
                    attachLikedListener()
                }
                onResult(result != null)
            }
        }
    }

    /** Signs out of Google and back into a fresh anonymous user. */
    fun requestGoogleSignOut(onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            signOutOfGoogle(getApplication<Application>().applicationContext)
            val id = social.ensureSignedIn()
            withContext(Dispatchers.Main) {
                uid = id
                isGoogleSignedIn = false
                googleDisplayName = null
                if (id != null) attachLikedListener()
                onDone()
            }
        }
    }

    /**
     * Prefills the comment nickname from the Google profile — but ONLY
     * when the user never picked a nickname themselves. A name the user
     * chose (or changed) always wins.
     */
    private fun maybePrefillNameFromGoogle(name: String?) {
        val clean = name?.trim()?.take(30).orEmpty()
        if (clean.isEmpty()) return
        if (profilePrefs.getString("name", null) != null) return
        saveDisplayName(clean)
    }

    /** Posts a comment in the app. onResult(true) = queued/sent. */
    fun postComment(slug: String, text: String, onResult: (Boolean) -> Unit) {
        val name = displayName ?: return onResult(false)
        val currentUid = uid
        commentError = false
        commentPosting = true
        viewModelScope.launch(Dispatchers.IO) {
            val signedIn = currentUid ?: social.ensureSignedIn()
            if (signedIn == null) {
                withContext(Dispatchers.Main) {
                    commentPosting = false
                    commentError = true
                    onResult(false)
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (uid == null) {
                    uid = signedIn
                    attachLikedListener()
                }
            }
            val ok = social.postComment(slug, signedIn, name, text)
            withContext(Dispatchers.Main) {
                commentPosting = false
                commentError = !ok
                onResult(ok)
            }
        }
    }

    // ---- Social: live listeners for the open article ----

    private fun attachLikedListener() {
        val slug = socialSlug ?: return
        val id = uid ?: return
        likedReg?.remove()
        likedReg = social.observeLiked(slug, id) { liked ->
            detailLiked = liked
            setLikedLocal(slug, liked)
        }
    }

    private fun startSocial(slug: String) {
        stopSocial()
        socialSlug = slug
        detailSocial = ArticleSocial()
        detailLiked = slug in likedSlugs
        comments = emptyList()
        commentsLoading = true
        socialRegs += social.observeArticle(slug) { social ->
            detailSocial = social
            clearConfirmedDeltas(mapOf(slug to social.likes))
        }
        socialRegs += social.observeComments(slug) {
            comments = it
            commentsLoading = false
        }
        attachLikedListener()
    }

    private fun stopSocial() {
        socialRegs.forEach { it.remove() }
        socialRegs.clear()
        likedReg?.remove()
        likedReg = null
        socialSlug = null
        detailSocial = ArticleSocial()
        detailLiked = false
        comments = emptyList()
        commentsLoading = false
    }

    fun openArticle(slug: String) {
        screen = Screen.Article(slug)
        articleDetail = null
        articleError = false
        articleLoading = true
        startSocial(slug)
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
        stopSocial()
        screen = Screen.Home
        articleDetail = null
        articleError = false
    }
}
