package com.lilac.anime.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * The sync rules between this app and the desktop app (LilacAnime-desktop), which share the user's LilacAnime server.
 * Kept free of Android classes so it can be unit tested on the JVM.
 *
 * The desktop keeps each item as a whole object: 내 목록 as the anime ({provider, id, mal_id, title, images, url}) under
 * its mal_id ("reanime:<slug>", "linkani:<id>"), 시청 기록 as {key, name, episode {provider, number, name, url},
 * anime {…}, progress, updated} under the episode's address ("reanime:https://reanime.to/watch/<slug>?ep=3").
 * This app keeps ids: an anime id ("reanime:<slug>", Linkkf's — linkani.tv's — bare number) and "<anime id>:<episode key>".
 *
 * So that both see one entry per work and per episode, with a title:
 * - an item is taken in under this app's id, and the server's id for it is remembered ([remote]), so a later change or
 *   removal made here goes back under the same id instead of making a second entry;
 * - what goes out is in the desktop's shape (with this app's fields too, androidAnimeId / androidEpisodeKey, which this
 *   app reads first), under the server's id when the item came from there, else under the desktop's id when it follows
 *   from this app's ids (Re:Anime and Miruro episode addresses, linkani:<id>);
 * - the works the desktop's items describe are handed back ([MergeResult.works]) for the phone's own copy of them, so
 *   a work never opened on the phone still shows in 내 목록 and 이어보기.
 */
object DeviceSyncMapping {
    val COLLECTIONS = listOf("library", "history")

    data class Identity(val animeId: String, val episodeKey: String, val episodeNumber: Int) {
        val historyId get() = "$animeId:$episodeKey"
    }

    data class Work(val id: String, val title: String, val poster: String, val anilistId: Int?, val malId: Int?, val description: String)

    data class MergeResult(val values: JSONObject, val works: List<Work>, val updated: Map<String, Long>)

    // JavaScript's encodeURIComponent, which the desktop builds its addresses with.
    fun encodeComponent(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~").replace("%2A", "*")

    private fun split(animeId: String): Pair<String, String>? {
        if (animeId.isNotEmpty() && animeId.all { it.isDigit() }) return "linkani" to animeId
        val at = animeId.indexOf(':')
        if (at <= 0 || at == animeId.length - 1) return null
        return animeId.substring(0, at) to animeId.substring(at + 1)
    }

    private fun animePage(provider: String, id: String): String? = when (provider) {
        "reanime" -> "https://reanime.to/anime/$id"
        "animenosub" -> "https://animenosub.to/anime/$id/"
        "miruro" -> "https://www.miruro.to/watch/$id"
        "ohli24" -> "https://ani.ohli24.com/c/${encodeComponent(id)}"
        "linkani" -> "https://linkani.tv/ani/$id/"
        else -> null
    }

    /** This app's anime id for a desktop anime object; null for one this app has no source for (MAL, linkkf.app). */
    fun androidAnimeId(anime: JSONObject?): String? {
        if (anime == null) return null
        val provider = anime.optString("provider")
        val id = anime.optString("id")
        if (provider.isBlank() || id.isBlank()) return null
        return when (provider) {
            "linkani" -> id.removePrefix("linkani:")
            "reanime", "animenosub", "miruro", "ohli24" -> "$provider:$id"
            else -> null
        }
    }

    /** The desktop's 내 목록 id for this app's anime id. */
    fun desktopLibraryId(animeId: String): String = if (animeId.isNotEmpty() && animeId.all { it.isDigit() }) "linkani:$animeId" else animeId

    /** This app's 내 목록 id for a server item, or null when this app cannot open it. */
    fun libraryId(id: String, data: JSONObject?): String? {
        if (data == null || data.optString("provider") == "android") return (data?.optString("id")?.ifBlank { null } ?: id).let { if (it.startsWith("linkani:")) it.removePrefix("linkani:") else it }
        return androidAnimeId(data)
    }

    private fun episodeKey(episode: JSONObject?): String? {
        if (episode == null) return null
        val key = episode.optString("key")
        if (key.isNotBlank()) return key
        val name = episode.optString("name")
        if (Regex("^\\d+[a-zA-Z]?$").matches(name)) return name
        val number = episode.optInt("number", 0)
        return if (number > 0) number.toString() else null
    }

    /** Which work and episode a history item is, whoever wrote it. */
    fun historyIdentity(id: String, data: JSONObject): Identity? {
        val animeId = data.optString("androidAnimeId").ifBlank { androidAnimeId(data.optJSONObject("anime")) ?: "" }
        if (animeId.isBlank()) return null
        val episode = data.optJSONObject("episode")
        val key = data.optString("androidEpisodeKey").ifBlank { episodeKey(episode) ?: "" }
        if (key.isBlank()) return null
        val number = data.optInt("episodeNumber", 0).takeIf { it > 0 } ?: episode?.optInt("number", 0)?.takeIf { it > 0 }
            ?: key.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
        return Identity(animeId, key, number)
    }

    /** The desktop's address for an episode, when it follows from the ids (Re:Anime, Miruro; numbered episodes). */
    fun desktopEpisodeUrl(animeId: String, key: String): String? {
        val (provider, id) = split(animeId) ?: return null
        if (key.isEmpty() || !key.all { it.isDigit() }) return null
        return when (provider) {
            "reanime" -> "https://reanime.to/watch/${encodeComponent(id)}?ep=$key"
            "miruro" -> "https://www.miruro.to/watch/$id?ep=$key"
            else -> null
        }
    }

    fun desktopHistoryId(animeId: String, key: String): String? =
        desktopEpisodeUrl(animeId, key)?.let { "${split(animeId)!!.first}:$it" }

    /** A desktop anime object for this app's anime id, with what is known of the work. */
    fun desktopAnime(animeId: String, work: Work?): JSONObject? {
        val (provider, id) = split(animeId) ?: return null
        val page = animePage(provider, id) ?: return null
        return JSONObject().put("provider", provider).put("id", id).put("mal_id", desktopLibraryId(animeId)).put("url", page).apply {
            if (work != null && work.title.isNotBlank()) put("title", work.title)
            if (work != null && work.poster.isNotBlank()) put("images", JSONObject().put("webp", JSONObject().put("large_image_url", work.poster)))
            work?.anilistId?.let { put("anilistId", it) }
            work?.malId?.let { put("malId", it) }
        }
    }

    /** What a desktop anime object says about its work, for the phone's copy; null without a title of its own. */
    fun workOf(anime: JSONObject?): Work? {
        val id = androidAnimeId(anime) ?: return null
        val title = anime!!.optString("title")
        if (title.isBlank() || title == id || title == anime.optString("id") || title == anime.optString("mal_id")) return null
        val images = anime.optJSONObject("images")
        val poster = images?.optJSONObject("webp")?.optString("large_image_url")?.ifBlank { null }
            ?: images?.optJSONObject("jpg")?.optString("large_image_url")?.ifBlank { null }
            ?: anime.optString("image")
        val anilistId = anime.optInt("anilistId", 0).takeIf { it > 0 }
        val malId = anime.optInt("malId", 0).takeIf { it > 0 }
        return Work(id, title, poster, anilistId, malId, anime.optString("synopsis"))
    }

    private fun remoteOf(remote: JSONObject, collection: String): JSONObject =
        remote.optJSONObject(collection) ?: JSONObject().also { remote.put(collection, it) }

    // The server's copy is kept for history (to send changes back in the desktop's shape), without the work's story.
    private fun keep(data: JSONObject): JSONObject = JSONObject(data.toString()).apply { optJSONObject("anime")?.remove("synopsis") }

    /**
     * What to send: items of [local] (this app's shape, by this app's id) whose hash differs from [snapshot], and
     * removals. [works]: what this app knows of each anime id, for the titles and pictures sent.
     */
    fun outgoing(local: JSONObject, snapshot: JSONObject, remote: JSONObject, works: Map<String, Work>, now: Long, hash: (JSONObject) -> String): JSONArray {
        val out = JSONArray()
        for (collection in COLLECTIONS) {
            val before = snapshot.optJSONObject(collection) ?: JSONObject()
            val current = local.optJSONObject(collection) ?: JSONObject()
            val known = remoteOf(remote, collection)
            for (id in current.keys().asSequence().toList()) {
                val data = current.optJSONObject(id) ?: continue
                if (before.optString(id, "") == hash(data)) continue
                val serverId = known.optJSONObject(id)?.optString("id")?.ifBlank { null }
                if (collection == "library") {
                    val updated = if (before.has(id)) now else data.optLong("savedAt", now).coerceAtLeast(1L)
                    val sent = desktopAnime(id, works[id])?.put("savedAt", data.optLong("savedAt", now)) ?: data
                    out.put(JSONObject().put("collection", collection).put("id", serverId ?: desktopLibraryId(id)).put("updated", updated).put("data", sent))
                } else {
                    val identity = historyIdentity(id, data) ?: continue
                    val updated = data.optLong("updated", now).coerceAtLeast(1L)
                    out.put(JSONObject().put("collection", collection).put("id", serverId ?: desktopHistoryId(identity.animeId, identity.episodeKey) ?: id).put("updated", updated)
                        .put("data", outgoingHistory(known.optJSONObject(id)?.optJSONObject("data"), identity, data, works[identity.animeId], serverId ?: desktopHistoryId(identity.animeId, identity.episodeKey) ?: id)))
                }
            }
            for (id in before.keys().asSequence().toList()) {
                if (current.has(id)) continue
                val serverId = known.optJSONObject(id)?.optString("id")?.ifBlank { null }
                val fallback = if (collection == "library") desktopLibraryId(id) else id.substringBeforeLast(':').let { anime -> desktopHistoryId(anime, id.substringAfterLast(':')) } ?: id
                out.put(JSONObject().put("collection", collection).put("id", serverId ?: fallback).put("updated", now).put("deleted", true))
                known.remove(id)
            }
        }
        return out
    }

    /** A history entry as it goes out: the desktop's shape (the server's copy when there is one) with this app's fields. */
    fun outgoingHistory(server: JSONObject?, identity: Identity, local: JSONObject, work: Work?, key: String): JSONObject {
        val title = work?.title?.takeIf { it.isNotBlank() }
        val base = server?.let { JSONObject(it.toString()) } ?: desktopAnime(identity.animeId, work)?.let { anime ->
            val url = desktopEpisodeUrl(identity.animeId, identity.episodeKey)
            val label = if (identity.episodeKey.all { it.isDigit() }) "${identity.episodeKey}화" else identity.episodeKey
            JSONObject().put("key", key).put("src", "").put("name", "${title ?: identity.animeId} · $label").put("subtitleTitle", title ?: "")
                .put("image", work?.poster ?: "").put("anime", anime)
                .put("episode", JSONObject().put("provider", anime.optString("provider")).put("number", identity.episodeNumber).put("name", identity.episodeKey).put("key", identity.episodeKey).apply {
                    if (url != null) put("url", url)
                    if (anime.optString("provider") == "miruro") put("animeId", anime.optString("id"))
                })
        } ?: JSONObject(local.toString())
        return base.put("progress", local.optDouble("progress", 0.0)).put("updated", local.optLong("updated", 0L))
            .put("androidAnimeId", identity.animeId).put("androidEpisodeKey", identity.episodeKey).put("episodeNumber", identity.episodeNumber)
    }

    /**
     * Takes the server's items into [local] (this app's shape, by this app's id; items in the desktop's shape stay as
     * they came, under this app's id). [remote] learns the server's id (and, for history, its copy) of each.
     */
    fun merge(local: JSONObject, incoming: List<JSONObject>, remote: JSONObject): MergeResult {
        val out = JSONObject()
        val works = LinkedHashMap<String, Work>()
        val updated = HashMap<String, Long>()
        for (collection in COLLECTIONS) {
            val values = JSONObject(local.optJSONObject(collection)?.toString() ?: "{}")
            val known = remoteOf(remote, collection)
            if (collection == "history") values.keys().forEach { id -> updated[id] = values.optJSONObject(id)?.optLong("updated", 0L) ?: 0L }
            val byServerId = HashMap<String, String>()
            known.keys().forEach { id -> known.optJSONObject(id)?.optString("id")?.let { byServerId[it] = id } }
            for (item in incoming.filter { it.optString("collection") == collection }.sortedBy { it.optLong("updated", 0L) }) {
                val serverId = item.optString("id")
                if (serverId.isBlank()) continue
                val data = item.optJSONObject("data")
                if (item.optBoolean("deleted")) {
                    val id = byServerId[serverId] ?: if (collection == "library") libraryId(serverId, null) else serverId
                    if (id != null) { values.remove(id); known.remove(id) }
                    continue
                }
                if (data == null) continue
                if (collection == "library") {
                    val id = libraryId(serverId, data) ?: continue
                    values.put(id, data)
                    known.put(id, JSONObject().put("id", serverId))
                    byServerId[serverId] = id
                    workOf(data)?.let { works[it.id] = it }
                } else {
                    val identity = historyIdentity(serverId, data) ?: continue
                    val id = identity.historyId
                    values.put(id, data)
                    updated[id] = item.optLong("updated", 0L)
                    if (serverId != id || data.has("anime") && data.optJSONObject("anime")?.has("provider") == true) known.put(id, JSONObject().put("id", serverId).put("data", keep(data)))
                    byServerId[serverId] = id
                    workOf(data.optJSONObject("anime"))?.let { works[it.id] = it }
                }
            }
            out.put(collection, values)
        }
        return MergeResult(out, works.values.toList(), updated)
    }
}
