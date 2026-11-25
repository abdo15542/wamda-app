@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
package net.wamdalibya.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Brand palette, per flavor. AR: navy + gold. EN (TechPulse): deep teal + cyan.
private val brandDark = if (BuildConfig.IS_ARABIC) Color(0xFF0B1E3A) else Color(0xFF0B2B33)
private val brandDarkLight = if (BuildConfig.IS_ARABIC) Color(0xFF13294A) else Color(0xFF0E3A44)
private val brandAccent = if (BuildConfig.IS_ARABIC) Color(0xFFD4A017) else Color(0xFF00D4FF)

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        // A bad link must never crash the app.
    }
}

/**
 * Article images are 1200x800 JPEGs on the server. Decoding them at full
 * resolution in every list card was the main cause of the slow, jerky
 * scrolling. Every image now loads through an explicit downsampling
 * request (list ~800px, detail ~1080px) with memory + disk caching and
 * no crossfade, so scrolling stays smooth.
 */
private fun articleImageRequest(context: Context, url: String, sizePx: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(sizePx)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(false)
        .build()

@Composable
fun WamdaApp(viewModel: AppViewModel) {
    val direction = if (BuildConfig.IS_ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = brandAccent,
                onPrimary = Color.Black,
                background = brandDark,
                onBackground = Color.White,
                surface = brandDarkLight,
                onSurface = Color.White,
                surfaceVariant = brandDarkLight,
                onSurfaceVariant = Color(0xFFB0BEC5)
            )
        ) {
            val screen = viewModel.screen
            val context = LocalContext.current
            var showAccountDialog by remember { mutableStateOf(false) }
            BackHandler(enabled = screen is Screen.Article) { viewModel.goHome() }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Header: brand name + tagline + follow-on-Facebook button,
                // with the account (Google sign-in) button top-right.
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            color = brandAccent,
                            fontSize = 14.sp
                        )
                        TextButton(
                            onClick = { openUrl(context, BuildConfig.FACEBOOK_PAGE_URL) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.follow_facebook),
                                color = brandAccent,
                                fontSize = 13.sp
                            )
                        }
                    }
                    IconButton(onClick = { showAccountDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = stringResource(R.string.account_title),
                            tint = if (viewModel.isGoogleSignedIn) brandAccent else Color(0xFFB0BEC5),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                // One dialog for both triggers: the header account icon
                // AND a gated Like/Comment (v3.2 — signInDialogVisible).
                // On success the pending action runs exactly once, then
                // the dialog closes; on dismiss the pending action is
                // dropped and nothing was registered.
                if (showAccountDialog || viewModel.signInDialogVisible) {
                    AccountDialog(
                        viewModel = viewModel,
                        onDismiss = {
                            showAccountDialog = false
                            viewModel.dismissSignInDialog()
                            viewModel.clearGoogleSignInError()
                        },
                        onSignedIn = { viewModel.consumePendingSignInAction() }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (val s = screen) {
                        is Screen.Home -> HomeContent(viewModel)
                        is Screen.Article -> ArticleContent(viewModel, s.slug)
                    }
                }
                // ONE small AdMob banner fixed at the bottom only.
                AdBanner()
            }
        }
    }
}

/**
 * Account dialog (v3.1): Google Sign-In. One tap once, then automatic —
 * the device links the Google account to the anonymous user, so likes
 * and comments survive. When signed in it shows the Google name and a
 * sign-out option instead.
 *
 * v3.2: (a) the dialog is also the gate for Like/Comment — when it was
 * opened by a gated action it says so, and [onSignedIn] runs the
 * pending action exactly once after a successful sign-in; (b) a real
 * sign-in failure shows its actual error (code + message, from the
 * ViewModel) instead of a generic message — a user-cancel shows no
 * error at all, and the dialog stays open so the user can retry or
 * close it.
 */
@Composable
private fun AccountDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onSignedIn: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var signingIn by remember { mutableStateOf(false) }
    // Real failure detail from the ViewModel (v3.2); null = no error.
    val errorDetail = viewModel.googleSignInErrorDetail
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_title)) },
        text = {
            Column {
                if (viewModel.isGoogleSignedIn) {
                    Text(
                        text = stringResource(
                            R.string.account_signed_in_as,
                            viewModel.googleDisplayName ?: ""
                        ),
                        fontSize = 14.sp
                    )
                } else {
                    // Gated open (v3.2): say why sign-in is being asked.
                    if (viewModel.signInDialogVisible) {
                        Text(
                            text = stringResource(R.string.account_signin_required),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.account_sign_in_hint),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (errorDetail != null) {
                        Text(
                            text = stringResource(R.string.account_signin_error_detail, errorDetail),
                            color = Color(0xFFFF8A80),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (viewModel.isGoogleSignedIn) {
                TextButton(
                    onClick = { viewModel.requestGoogleSignOut { onDismiss() } }
                ) {
                    Text(stringResource(R.string.account_sign_out), color = brandAccent)
                }
            } else {
                Button(
                    onClick = {
                        val act = activity ?: return@Button
                        if (signingIn) return@Button
                        signingIn = true
                        viewModel.requestGoogleSignIn(act) { ok ->
                            signingIn = false
                            if (ok) {
                                // Signed in: run the pending Like/Comment
                                // (if any) exactly once, then close.
                                onSignedIn()
                                onDismiss()
                            }
                            // Failure: the dialog stays open and shows
                            // the real error from the ViewModel; cancel:
                            // no error shown, retry or close.
                        }
                    },
                    enabled = !signingIn && activity != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandAccent,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        if (signingIn) stringResource(R.string.account_signing_in)
                        else stringResource(R.string.account_sign_in)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
    )
}

@Composable
private fun HomeContent(viewModel: AppViewModel) {
    val ann = viewModel.announcements
    Column(modifier = Modifier.fillMaxSize()) {
        // TOP ANNOUNCEMENTS AREA (above search):
        // 1. an update card when a newer build exists, else
        // 2. the newest announcement as one compact dismissible card.
        // The in-app download promo is never shown inside the app.
        if (ann.latestVersionCode > BuildConfig.VERSION_CODE && ann.apkUrl.isNotBlank()) {
            UpdateCard(ann)
        } else {
            val latest = remember(ann) {
                ann.items.firstOrNull { it.buttonUrl != ann.apkUrl }
            }
            if (latest != null) {
                LatestAnnouncementCard(latest)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            NewsFeed(viewModel)
        }
    }
}

/**
 * Update card: one tap downloads the APK inside the app (DownloadManager)
 * and opens the Android installer automatically when it finishes.
 *
 * v3.3: the button NEVER yanks the user out of the app on its own.
 * When the app still needs the one-time install permission, an in-app
 * dialog explains it first — only the dialog's button opens Settings.
 * A failed download resets the button so the user can tap again.
 */
@Composable
private fun UpdateCard(ann: AnnouncementsData) {
    val context = LocalContext.current
    var downloading by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    UpdateInstaller.openInstallSettings(context)
                }) {
                    Text(stringResource(R.string.update_permission_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = brandAccent)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.update_title),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = stringResource(R.string.update_text, ann.latestVersionName),
                color = Color.Black,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (downloading) return@Button
                    when (UpdateInstaller.startUpdate(context, ann.apkUrl) { ok ->
                        // Download finished: installer opened (ok) or failed.
                        if (!ok) {
                            downloading = false
                            Toast.makeText(
                                context,
                                context.getString(R.string.update_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }) {
                        UpdateInstaller.UpdateResult.Started -> downloading = true
                        UpdateInstaller.UpdateResult.NeedsPermission ->
                            showPermissionDialog = true
                        UpdateInstaller.UpdateResult.Failed -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.update_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
            ) {
                Text(
                    if (downloading) stringResource(R.string.update_downloading)
                    else stringResource(R.string.update_button)
                )
            }
        }
    }
}

@Composable
private fun LatestAnnouncementCard(ann: Announcement) {
    var dismissed by remember(ann.id) { mutableStateOf(false) }
    if (dismissed) return
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = brandDarkLight)
    ) {
        Column {
            if (ann.image.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = articleImageRequest(context, ann.image, 800),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ann.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { dismissed = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dismiss),
                            color = brandAccent,
                            fontSize = 13.sp
                        )
                    }
                }
                if (ann.text.isNotBlank()) {
                    Text(
                        text = ann.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (ann.buttonUrl.isNotBlank() && ann.buttonText.isNotBlank()) {
                    Button(
                        onClick = { openUrl(context, ann.buttonUrl) },
                        modifier = Modifier.padding(top = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandAccent,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(ann.buttonText)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsFeed(viewModel: AppViewModel) {
    var query by remember { mutableStateOf("") }
    // Search is debounced ~300ms: typing no longer refilters 1201 items
    // on every keystroke.
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(300)
        debouncedQuery = query
    }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val articles = viewModel.articles
    val filtered = remember(articles, debouncedQuery, selectedCategory) {
        articles.filter { a ->
            (selectedCategory == null || a.category == selectedCategory) &&
                (debouncedQuery.isBlank() || a.title.contains(debouncedQuery, ignoreCase = true))
        }
    }
    // Pagination: only the first 40 are composed; 40 more load when the
    // user scrolls near the end.
    val shown = filtered.take(viewModel.visibleCount)
    // Shared like/comment counts for the visible page (one batched read
    // per page; missing articles simply show 0).
    LaunchedEffect(shown) {
        viewModel.fetchCountsFor(shown.map { it.slug })
    }
    val listState = rememberLazyListState()
    LaunchedEffect(debouncedQuery, selectedCategory) {
        viewModel.resetPaging()
        listState.scrollToItem(0)
    }
    LaunchedEffect(listState, filtered.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= 0 && last >= viewModel.visibleCount - 5 &&
                    viewModel.visibleCount < filtered.size
                ) {
                    viewModel.loadMore()
                }
            }
    }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isLoading,
        onRefresh = { viewModel.refresh() }
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(stringResource(R.string.all_categories)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = brandAccent,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
                items(viewModel.categories) { (code, name) ->
                    FilterChip(
                        selected = selectedCategory == code,
                        onClick = { selectedCategory = code },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = brandAccent,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
            if (viewModel.isOffline && articles.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.offline_notice),
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }
            when {
                articles.isEmpty() && viewModel.isLoading -> {
                    // No cache at all yet: this is the only time a full
                    // spinner shows. With a cache, the list is already
                    // on screen and the refresh happens in the background.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = brandAccent)
                    }
                }
                articles.isEmpty() -> {
                    // Nothing cached and no network.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.offline_title),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.offline_text),
                            color = Color(0xFFB0BEC5),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Button(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier.padding(top = 24.dp)
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                filtered.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_results),
                            color = Color(0xFFB0BEC5),
                            fontSize = 15.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(shown, key = { it.slug }) { article ->
                            ArticleRow(
                                article = article,
                                social = viewModel.displaySocial(
                                    article.slug,
                                    viewModel.listCounts[article.slug] ?: ArticleSocial()
                                ),
                                liked = viewModel.isLiked(article.slug),
                                // v3.2: liking needs Google sign-in first —
                                // while signed out the tap opens the
                                // sign-in dialog, and the like registers
                                // exactly once after a successful sign-in.
                                onLike = { viewModel.requireGoogleSignIn { viewModel.toggleLike(article.slug) } },
                                onClick = { viewModel.openArticle(article.slug) }
                            )
                        }
                    }
                }
            }
        }
        PullRefreshIndicator(
            refreshing = viewModel.isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = brandDarkLight,
            contentColor = brandAccent
        )
    }
}

@Composable
private fun ArticleRow(
    article: FeedArticle,
    social: ArticleSocial?,
    liked: Boolean,
    onLike: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = brandDarkLight)
    ) {
        Column {
            if (article.image.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = articleImageRequest(context, article.image, 800),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = article.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = article.categoryName,
                        color = brandAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = article.date,
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp
                    )
                }
                // Shared social counts, Facebook-style: tapping the heart
                // likes WITHOUT opening the article (inner clickable wins
                // over the card's click); the comment count opens it.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(onClick = onLike)
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Text(
                            text = "♥",
                            color = if (liked) brandAccent else Color(0xFFB0BEC5),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = (social?.likes ?: 0L).toString(),
                            color = Color(0xFFB0BEC5),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "💬",
                            color = Color(0xFFB0BEC5),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = (social?.comments ?: 0L).toString(),
                            color = Color(0xFFB0BEC5),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/** Short "2 Jan 14:30"-style stamp for a comment, or "" while pending. */
private fun commentTime(millis: Long): String {
    if (millis == Long.MAX_VALUE) return ""
    return try {
        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
    } catch (e: Exception) {
        ""
    }
}

@Composable
private fun ArticleContent(viewModel: AppViewModel, slug: String) {
    val detail = viewModel.articleDetail
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            TextButton(onClick = { viewModel.goHome() }) {
                Text(text = stringResource(R.string.back_label), color = brandAccent, fontSize = 15.sp)
            }
        }
        when {
            viewModel.articleLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = brandAccent)
                }
            }
            viewModel.articleError || detail == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.article_error),
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { viewModel.openArticle(slug) },
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (detail.image.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = articleImageRequest(context, detail.image, 1080),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = detail.title,
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                            Text(
                                text = detail.categoryName,
                                color = brandAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = detail.date,
                                color = Color(0xFFB0BEC5),
                                fontSize = 13.sp
                            )
                        }
                        // body[0] is the title — shown above already.
                        val paragraphs = detail.body.filterIndexed { i, p ->
                            !(i == 0 && p == detail.title) && p.isNotBlank()
                        }
                        for (p in paragraphs) {
                            Text(
                                text = p,
                                color = Color.White,
                                fontSize = 17.sp,
                                modifier = Modifier.padding(bottom = 14.dp)
                            )
                        }
                        // Shared like + in-app comments (v3). The like
                        // count is optimistic (server + pending tap) and
                        // converges to the server value; the comments
                        // live under the article INSIDE the app — nothing
                        // here opens Facebook any more.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF334155))
                        )
                        SocialSection(viewModel, detail)
                    }
                }
            }
        }
    }
}

/**
 * Like bar (heart + shared count) and the in-app comments section.
 * Everything happens inside the app: no Facebook redirect anywhere.
 */
@Composable
private fun SocialSection(viewModel: AppViewModel, detail: ArticleDetail) {
    val social = viewModel.displaySocial(detail.slug, viewModel.detailSocial)
    val liked = viewModel.detailLiked
    var draft by remember(detail.slug) { mutableStateOf("") }
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingText by remember { mutableStateOf<String?>(null) }

    /**
     * Sends the draft comment (v3.2: gated behind Google sign-in).
     * While signed out, the tap stores this whole send as the pending
     * action and opens the sign-in dialog — after a successful sign-in
     * the send runs exactly once (including the first-time name dialog
     * when no display name exists yet); dismissing the dialog drops it
     * and nothing is posted. The draft is only cleared on a successful
     * post, so the text is never lost on cancel/failure.
     */
    fun sendComment() {
        val text = draft.trim()
        if (text.isEmpty() || viewModel.commentPosting) return
        viewModel.requireGoogleSignIn {
            if (viewModel.displayName == null) {
                // First comment: ask for a display name, then post.
                pendingText = text
                showNameDialog = true
            } else {
                viewModel.postComment(detail.slug, text) { ok ->
                    if (ok) draft = ""
                }
            }
        }
    }

    Column(modifier = Modifier.padding(top = 12.dp)) {
        // Like row: heart + shared count, Facebook-style.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // v3.2: liking needs Google sign-in first — see the list row.
            Button(
                onClick = { viewModel.requireGoogleSignIn { viewModel.toggleLike(detail.slug) } },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (liked) brandAccent else brandDarkLight,
                    contentColor = if (liked) Color.Black else Color.White
                )
            ) {
                Text(
                    text = if (liked) stringResource(R.string.liked_button)
                    else stringResource(R.string.like_button)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = social.likes.toString(),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "💬 ${social.comments}",
                color = Color(0xFFB0BEC5),
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.comments_title, social.comments),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        when {
            viewModel.commentsLoading -> {
                CircularProgressIndicator(
                    color = brandAccent,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(24.dp)
                )
            }
            viewModel.comments.isEmpty() -> {
                Text(
                    text = stringResource(R.string.comments_empty),
                    color = Color(0xFFB0BEC5),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            else -> {
                for (c in viewModel.comments) {
                    CommentItem(c)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        // Composer: name row + text field + send.
        val name = viewModel.displayName
        if (name != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.comment_as_name, name),
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { showNameDialog = true },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.comment_change_name),
                        color = brandAccent,
                        fontSize = 13.sp
                    )
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= 500) draft = it },
            placeholder = { Text(stringResource(R.string.comment_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.commentError) {
                Text(
                    text = stringResource(R.string.comment_error),
                    color = Color(0xFFFF8A80),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
            }
            Button(
                onClick = { sendComment() },
                enabled = !viewModel.commentPosting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandAccent,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    if (viewModel.commentPosting) stringResource(R.string.comment_posting)
                    else stringResource(R.string.comment_send)
                )
            }
        }
    }

    if (showNameDialog) {
        NameDialog(
            currentName = viewModel.displayName,
            onDismiss = {
                showNameDialog = false
                pendingText = null
            },
            onSave = { newName ->
                viewModel.saveDisplayName(newName)
                showNameDialog = false
                val text = pendingText
                pendingText = null
                if (text != null) {
                    viewModel.postComment(detail.slug, text) { ok ->
                        if (ok) draft = ""
                    }
                }
            }
        )
    }
}

@Composable
private fun CommentItem(comment: AppComment) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = comment.name.ifBlank { "?" },
                color = brandAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            val stamp = commentTime(comment.createdAtMillis)
            if (stamp.isNotBlank()) {
                Text(
                    text = stamp,
                    color = Color(0xFF78909C),
                    fontSize = 12.sp
                )
            }
        }
        Text(
            text = comment.text,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(1.dp)
                .background(Color(0xFF1E3A56))
        )
    }
}

/** Asks once for the display name shown with the user's comments. */
@Composable
private fun NameDialog(
    currentName: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val defaultName = stringResource(R.string.comment_default_name)
    var name by remember { mutableStateOf(currentName ?: defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.comment_name_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.comment_name_hint),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.trim().isNotEmpty()) onSave(name) },
                enabled = name.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.comment_name_save), color = brandAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.comment_name_cancel))
            }
        }
    )
}

/**
 * AdMob: ONE small adaptive banner fixed at the bottom only.
 * The AdView is created PROGRAMMATICALLY — an AdView inflated from XML
 * without app:adSize never renders in play-services-ads 23.6.0
 * ("Required XML attribute adSize was missing", seen on Abdoo's phone
 * 2026-11-23). setAdSize runs before loadAd.
 * No interstitials, no popups, no video, no sound.
 */
@Composable
private fun AdBanner() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val adView = remember {
        AdView(context).apply {
            val widthDp = (context.resources.displayMetrics.widthPixels /
                context.resources.displayMetrics.density).toInt()
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(lifecycle, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_DESTROY -> adView.destroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(
        factory = { adView },
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    )
}
