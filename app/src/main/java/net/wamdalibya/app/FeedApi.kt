package net.wamdalibya.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Native content feed. The app reads the JSON feed straight from GitHub
 * Pages — no website, no WebView. Feed shape (both flavors):
 *   <base>/feed/index.json
 *     {"count": N, "articles": [{id, slug, title, date, category,
 *       category_name, image}]} — newest first.
 *   <base>/feed/articles/article-NNN.json
 *     {id, slug, title, date, category, category_name, image,
 *      hashtags[], body[]} — body[0] is the title.
 *   <base>/announcements.json
 *     {latest_version_code, latest_version_name, apk_url,
 *      items: [{id, title, text, image, button_text, button_url}]}
 * Everything fetched is cached on disk, so the app still reads offline.
 */
data class FeedArticle(
    val id: Int,
    val slug: String,
    val title: String,
    val date: String,
    val category: String,
    val categoryName: String,
    val image: String
)

data class ArticleDetail(
    val id: Int,
    val slug: String,
    val title: String,
    val date: String,
    val category: String,
    val categoryName: String,
    val image: String,
    val body: List<String>
)

data class Announcement(
    val id: String,
    val title: String,
    val text: String,
    val image: String,
    val buttonText: String,
    val buttonUrl: String
)

data class AnnouncementsData(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val items: List<Announcement>
)

class FeedRepository(private val context: Context, private val baseUrl: String) {

    private val feedDir: File
        get() = File(context.filesDir, "feed").apply { mkdirs() }

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "WamdaNative/1.0")
                instanceFollowRedirects = true
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseIndex(json: String): List<FeedArticle> {
        val out = ArrayList<FeedArticle>()
        val arr = JSONObject(json).optJSONArray("articles") ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                FeedArticle(
                    id = o.optInt("id", 0),
                    slug = o.optString("slug", ""),
                    title = o.optString("title", ""),
                    date = o.optString("date", ""),
                    category = o.optString("category", ""),
                    categoryName = o.optString("category_name", ""),
                    image = o.optString("image", "")
                )
            )
        }
        return out
    }

    private fun parseArticle(json: String): ArticleDetail? {
        return try {
            val o = JSONObject(json)
            val body = ArrayList<String>()
            val arr = o.optJSONArray("body")
            if (arr != null) {
                for (i in 0 until arr.length()) body.add(arr.optString(i, ""))
            }
            ArticleDetail(
                id = o.optInt("id", 0),
                slug = o.optString("slug", ""),
                title = o.optString("title", ""),
                date = o.optString("date", ""),
                category = o.optString("category", ""),
                categoryName = o.optString("category_name", ""),
                image = o.optString("image", ""),
                body = body
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAnnouncements(json: String): AnnouncementsData {
        return try {
            val o = JSONObject(json)
            val items = ArrayList<Announcement>()
            val arr = o.optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    items.add(
                        Announcement(
                            id = a.optString("id", ""),
                            title = a.optString("title", ""),
                            text = a.optString("text", ""),
                            image = a.optString("image", ""),
                            buttonText = a.optString("button_text", ""),
                            buttonUrl = a.optString("button_url", "")
                        )
                    )
                }
            }
            AnnouncementsData(
                latestVersionCode = o.optInt("latest_version_code", 0),
                latestVersionName = o.optString("latest_version_name", ""),
                apkUrl = o.optString("apk_url", ""),
                items = items
            )
        } catch (e: Exception) {
            AnnouncementsData(0, "", "", emptyList())
        }
    }

    private fun writeCache(name: String, text: String) {
        try {
            val f = File(feedDir, name)
            f.parentFile?.mkdirs()
            f.writeText(text, Charsets.UTF_8)
        } catch (e: Exception) {
            // Cache is best-effort; a failed write must never break reading.
        }
    }

    private fun readCache(name: String): String? {
        return try {
            val f = File(feedDir, name)
            if (f.exists()) f.readText(Charsets.UTF_8) else null
        } catch (e: Exception) {
            null
        }
    }

    /** Network-first; returns null only when both network and cache miss. */
    fun fetchIndex(): List<FeedArticle>? {
        val net = httpGet(baseUrl + "feed/index.json")
        if (net != null) {
            return try {
                val parsed = parseIndex(net)
                writeCache("index.json", net)
                parsed
            } catch (e: Exception) {
                null
            }
        }
        val cached = readCache("index.json") ?: return null
        return try {
            parseIndex(cached)
        } catch (e: Exception) {
            null
        }
    }

    /** Network-first per article; cached copy is used offline. */
    fun fetchArticle(slug: String): ArticleDetail? {
        val net = httpGet(baseUrl + "feed/articles/" + slug + ".json")
        if (net != null) {
            val parsed = parseArticle(net)
            if (parsed != null) {
                writeCache("articles/" + slug + ".json", net)
                return parsed
            }
        }
        val cached = readCache("articles/" + slug + ".json") ?: return null
        return parseArticle(cached)
    }

    /** Network-first; falls back to the cached copy when offline. */
    fun fetchAnnouncements(): AnnouncementsData {
        val net = httpGet(baseUrl + "announcements.json")
        if (net != null) {
            writeCache("announcements.json", net)
            return parseAnnouncements(net)
        }
        val cached = readCache("announcements.json") ?: return AnnouncementsData(0, "", "", emptyList())
        return parseAnnouncements(cached)
    }
}
