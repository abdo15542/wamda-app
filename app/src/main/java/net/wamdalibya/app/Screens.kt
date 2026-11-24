package net.wamdalibya.app

@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

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
            BackHandler(enabled = screen is Screen.Article) { viewModel.goHome() }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Header: brand name + tagline.
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
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

@Composable
private fun HomeContent(viewModel: AppViewModel) {
    val ann = viewModel.announcements
    Column(modifier = Modifier.fillMaxSize()) {
        // Update banner: shown only when the feed says a newer build exists.
        if (ann.latestVersionCode > BuildConfig.VERSION_CODE && ann.apkUrl.isNotBlank()) {
            UpdateBanner(ann)
        }
        TabRow(
            selectedTabIndex = if (viewModel.tab == Tab.NEWS) 0 else 1,
            containerColor = brandDark,
            contentColor = brandAccent
        ) {
            Tab(
                selected = viewModel.tab == Tab.NEWS,
                onClick = { viewModel.selectTab(Tab.NEWS) },
                text = { Text(stringResource(R.string.tab_news)) }
            )
            Tab(
                selected = viewModel.tab == Tab.ANNOUNCEMENTS,
                onClick = { viewModel.selectTab(Tab.ANNOUNCEMENTS) },
                text = { Text(stringResource(R.string.tab_announcements)) }
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (viewModel.tab == Tab.NEWS) NewsFeed(viewModel) else AnnouncementsList(viewModel)
        }
    }
}

@Composable
private fun UpdateBanner(ann: AnnouncementsData) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
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
                onClick = { openUrl(context, ann.apkUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
            ) {
                Text(stringResource(R.string.update_button))
            }
        }
    }
}

@Composable
private fun NewsFeed(viewModel: AppViewModel) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val articles = viewModel.articles
    val filtered = remember(articles, query, selectedCategory) {
        articles.filter { a ->
            (selectedCategory == null || a.category == selectedCategory) &&
                (query.isBlank() || a.title.contains(query, ignoreCase = true))
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
            if (articles.isEmpty() && !viewModel.isLoading) {
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
            } else if (filtered.isEmpty() && !viewModel.isLoading) {
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.slug }) { article ->
                        ArticleRow(article) { viewModel.openArticle(article.slug) }
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
private fun ArticleRow(article: FeedArticle, onClick: () -> Unit) {
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
                        model = article.image,
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
            }
        }
    }
}

@Composable
private fun ArticleContent(viewModel: AppViewModel, slug: String) {
    val detail = viewModel.articleDetail
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
                                model = detail.image,
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
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementsList(viewModel: AppViewModel) {
    val items = viewModel.announcements.items
    val context = LocalContext.current
    if (items.isEmpty() && !viewModel.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.announcements_empty),
                color = Color(0xFFB0BEC5),
                fontSize = 15.sp
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { ann ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = brandDarkLight)
            ) {
                Column {
                    if (ann.image.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = ann.image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = ann.title,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (ann.text.isNotBlank()) {
                            Text(
                                text = ann.text,
                                color = Color.White,
                                fontSize = 15.sp,
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
    }
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
