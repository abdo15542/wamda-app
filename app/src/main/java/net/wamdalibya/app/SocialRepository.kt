package net.wamdalibya.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Shared social backend (Firebase Firestore + Anonymous Auth).
 *
 * Data model — one document per article, counters on it:
 *   articles/{slug}            { likes: Long, comments: Long }
 *   articles/{slug}/likers/{uid}   (empty doc, exists = this user liked it)
 *   articles/{slug}/comments/{autoId}
 *       { text: String (<=500), name: String (<=30), uid: String,
 *         createdAt: server Timestamp }
 *
 * Readers never log in: Anonymous Auth signs the device in silently on
 * first launch. No email, no password, no personal data — the only
 * identity is the anonymous uid, used to (a) let a user unlike their own
 * like and (b) tie a comment to its author. The display name is chosen
 * by the user on the device (stored in SharedPreferences) and is only a
 * label on their comments.
 *
 * All calls are failure-safe: they return null/false/empty on any error
 * (offline, Firestore down) and must never crash the app. The like
 * COUNT shown in the UI always comes from the server listener, never
 * from a local guess.
 */
data class ArticleSocial(val likes: Long = 0, val comments: Long = 0)

data class AppComment(
    val id: String,
    val name: String,
    val text: String,
    /** Epoch millis; Long.MAX_VALUE = still pending server ack. */
    val createdAtMillis: Long
)

class SocialRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    /** Signs the device in anonymously (once). Returns the uid, or null offline. */
    suspend fun ensureSignedIn(): String? {
        auth.currentUser?.let { return it.uid }
        return try {
            auth.signInAnonymously().await().user?.uid
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Toggles this user's like on an article, atomically:
     * creates/deletes the liker doc and increments/decrements the counter
     * in one transaction. Returns the NEW liked state, or null on failure
     * (offline — the caller must leave the local state untouched).
     */
    suspend fun toggleLike(slug: String, uid: String): Boolean? {
        val articleRef = db.collection("articles").document(slug)
        val likerRef = articleRef.collection("likers").document(uid)
        return try {
            db.runTransaction { tx ->
                val articleSnap = tx.get(articleRef)
                val likerSnap = tx.get(likerRef)
                val liked = likerSnap.exists()
                val likes = articleSnap.getLong("likes") ?: 0L
                val comments = articleSnap.getLong("comments") ?: 0L
                if (liked) {
                    tx.delete(likerRef)
                    if (articleSnap.exists()) {
                        tx.update(articleRef, "likes", (likes - 1).coerceAtLeast(0L))
                    }
                } else {
                    tx.set(likerRef, mapOf("at" to FieldValue.serverTimestamp()))
                    if (articleSnap.exists()) {
                        tx.update(articleRef, "likes", likes + 1)
                    } else {
                        tx.set(articleRef, mapOf("likes" to 1L, "comments" to comments))
                    }
                }
                !liked
            }.await()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Like/comment counts for a page of articles, in batched reads
     * (whereIn caps at 30 ids per query). Missing docs mean (0, 0).
     * Partial results are still returned on error.
     */
    suspend fun fetchCounts(slugs: List<String>): Map<String, ArticleSocial> {
        if (slugs.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, ArticleSocial>()
        try {
            for (chunk in slugs.chunked(25)) {
                val snap = db.collection("articles")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()
                for (doc in snap.documents) {
                    out[doc.id] = ArticleSocial(
                        likes = doc.getLong("likes") ?: 0L,
                        comments = doc.getLong("comments") ?: 0L
                    )
                }
            }
        } catch (e: Exception) {
            // Keep whatever was fetched; the UI shows 0 for the rest.
        }
        return out
    }

    /** Live like/comment counters for one article. */
    fun observeArticle(slug: String, onChange: (ArticleSocial) -> Unit): ListenerRegistration =
        db.collection("articles").document(slug)
            .addSnapshotListener { snap, _ ->
                onChange(
                    ArticleSocial(
                        likes = snap?.getLong("likes") ?: 0L,
                        comments = snap?.getLong("comments") ?: 0L
                    )
                )
            }

    /** Live "did I like this" for one article. */
    fun observeLiked(slug: String, uid: String, onChange: (Boolean) -> Unit): ListenerRegistration =
        db.collection("articles").document(slug)
            .collection("likers").document(uid)
            .addSnapshotListener { snap, _ -> onChange(snap?.exists() == true) }

    /** Live comments, newest first (pending local writes sort to the top). */
    fun observeComments(slug: String, onChange: (List<AppComment>) -> Unit): ListenerRegistration =
        db.collection("articles").document(slug)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(60)
            .addSnapshotListener { snap, _ ->
                val list = ArrayList<AppComment>()
                if (snap != null) {
                    for (doc in snap.documents) {
                        val ts = doc.getTimestamp("createdAt")
                        list.add(
                            AppComment(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                text = doc.getString("text") ?: "",
                                createdAtMillis = ts?.toDate()?.time ?: Long.MAX_VALUE
                            )
                        )
                    }
                }
                // Pending writes have no server timestamp yet — keep them
                // at the top, newest first.
                list.sortByDescending { it.createdAtMillis }
                onChange(list)
            }

    /**
     * Posts a comment and bumps the counter in one batch (the write
     * queues offline and syncs later — posting works without a network).
     * Returns false only on a local validation/queue failure.
     */
    suspend fun postComment(slug: String, uid: String, name: String, text: String): Boolean {
        val cleanText = text.trim().take(500)
        val cleanName = name.trim().take(30).ifBlank { "?" }
        if (cleanText.isEmpty()) return false
        val articleRef = db.collection("articles").document(slug)
        val commentRef = articleRef.collection("comments").document()
        val data = mapOf(
            "text" to cleanText,
            "name" to cleanName,
            "uid" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            db.batch().apply {
                set(commentRef, data)
                // Merge-set: creates the article doc (comments=1) when it
                // does not exist yet, otherwise bumps the counter.
                set(articleRef, mapOf("comments" to FieldValue.increment(1)), SetOptions.merge())
            }.commit().await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
