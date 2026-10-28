package com.lilac.anime.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSyncMappingTest {
    private val hash: (JSONObject) -> String = { JSONObject(it.toString()).apply { remove("updated"); remove("savedAt") }.toString() }

    // As the desktop app writes them (LilacAnime-desktop src/app.js and src/device-sync.js).
    private fun desktopAnime(provider: String, id: String, title: String) = JSONObject()
        .put("provider", provider).put("id", id).put("mal_id", "$provider:$id").put("title", title)
        .put("images", JSONObject().put("webp", JSONObject().put("large_image_url", "https://img/$id.jpg"))).put("anilistId", 154587)

    private fun desktopEpisode(slug: String, number: Int, progress: Double, updated: Long): Pair<String, JSONObject> {
        val url = "https://reanime.to/watch/$slug?ep=$number"
        return "reanime:$url" to JSONObject().put("key", "reanime:$url").put("src", "").put("name", "장송의 프리렌 · ${number}화")
            .put("episode", JSONObject().put("provider", "reanime").put("number", number).put("name", "$number").put("url", url))
            .put("anime", desktopAnime("reanime", slug, "장송의 프리렌")).put("progress", progress).put("updated", updated)
    }

    private fun item(collection: String, id: String, data: JSONObject?, updated: Long, deleted: Boolean = false) = JSONObject()
        .put("collection", collection).put("id", id).put("updated", updated).apply { if (deleted) put("deleted", true) else put("data", data) }

    // This app's own shape (DeviceSyncClient.localCollections).
    private fun local(library: Set<String> = emptySet(), history: List<Triple<String, String, Double>> = emptyList(), now: Long = 1000L) = JSONObject()
        .put("library", JSONObject().apply { library.forEach { put(it, JSONObject().put("provider", "android").put("id", it).put("mal_id", it).put("title", it).put("savedAt", now)) } })
        .put("history", JSONObject().apply {
            history.forEach { (anime, key, progress) ->
                put("$anime:$key", JSONObject().put("key", "$anime:$key").put("androidAnimeId", anime).put("androidEpisodeKey", key).put("episodeNumber", key.toInt())
                    .put("episode", JSONObject().put("number", key.toInt()).put("key", key)).put("anime", JSONObject().put("id", anime).put("mal_id", anime).put("title", anime))
                    .put("progress", progress).put("updated", now))
            }
        })

    private fun snapshotOf(values: JSONObject) = JSONObject().apply {
        for (c in DeviceSyncMapping.COLLECTIONS) put(c, JSONObject().apply { val o = values.optJSONObject(c) ?: JSONObject(); o.keys().forEach { put(it, hash(o.getJSONObject(it))) } })
    }

    @Test
    fun `an episode watched on the desktop is this app's entry for that work, with the work kept`() {
        val (id, data) = desktopEpisode("frieren", 3, 40.0, 2000L)
        val remote = JSONObject()
        val merged = DeviceSyncMapping.merge(local(), listOf(item("history", id, data, 2000L)), remote)
        val entry = merged.values.getJSONObject("history").getJSONObject("reanime:frieren:3")
        assertEquals(DeviceSyncMapping.Identity("reanime:frieren", "3", 3), DeviceSyncMapping.historyIdentity("reanime:frieren:3", entry))
        assertEquals(id, remote.getJSONObject("history").getJSONObject("reanime:frieren:3").getString("id"))
        assertEquals(listOf(DeviceSyncMapping.Work("reanime:frieren", "장송의 프리렌", "https://img/frieren.jpg", 154587, null, "")), merged.works)
    }

    @Test
    fun `a change made here to the desktop's episode goes back under its id, in its shape`() {
        val (id, data) = desktopEpisode("frieren", 3, 40.0, 2000L)
        val remote = JSONObject()
        val merged = DeviceSyncMapping.merge(local(), listOf(item("history", id, data, 2000L)), remote)
        val snapshot = snapshotOf(local(history = listOf(Triple("reanime:frieren", "3", 40.0))))
        val out = DeviceSyncMapping.outgoing(local(history = listOf(Triple("reanime:frieren", "3", 90.0)), now = 5000L), snapshot, remote, emptyMap(), 5000L, hash)
        assertEquals(1, out.length())
        val sent = out.getJSONObject(0)
        assertEquals(id, sent.getString("id"))
        val sentData = sent.getJSONObject("data")
        assertEquals(90.0, sentData.getDouble("progress"), 0.0)
        assertEquals("https://reanime.to/watch/frieren?ep=3", sentData.getJSONObject("episode").getString("url"))
        assertEquals("reanime:frieren", sentData.getString("androidAnimeId"))
        assertEquals("장송의 프리렌", sentData.getJSONObject("anime").getString("title"))
        assertTrue(merged.values.getJSONObject("history").has("reanime:frieren:3"))
    }

    @Test
    fun `a Re-Anime episode first watched here goes out under the desktop's id, playable there`() {
        val work = DeviceSyncMapping.Work("reanime:frieren", "장송의 프리렌", "https://img/p.jpg", null, null, "")
        val out = DeviceSyncMapping.outgoing(local(history = listOf(Triple("reanime:frieren", "4", 10.0))), JSONObject(), JSONObject(), mapOf("reanime:frieren" to work), 1000L, hash)
        val sent = out.getJSONObject(0)
        assertEquals("reanime:https://reanime.to/watch/frieren?ep=4", sent.getString("id"))
        val data = sent.getJSONObject("data")
        assertEquals("reanime:https://reanime.to/watch/frieren?ep=4", data.getString("key"))
        assertEquals("https://reanime.to/watch/frieren?ep=4", data.getJSONObject("episode").getString("url"))
        assertEquals("https://reanime.to/anime/frieren", data.getJSONObject("anime").getString("url"))
        assertEquals("장송의 프리렌", data.getJSONObject("anime").getString("title"))
        assertEquals("장송의 프리렌 · 4화", data.getString("name"))
    }

    @Test
    fun `an Animenosub episode keeps this app's id but goes out with its work`() {
        val out = DeviceSyncMapping.outgoing(local(history = listOf(Triple("animenosub:bleach", "5", 10.0))), JSONObject(), JSONObject(), emptyMap(), 1000L, hash)
        val sent = out.getJSONObject(0)
        assertEquals("animenosub:bleach:5", sent.getString("id"))
        val anime = sent.getJSONObject("data").getJSONObject("anime")
        assertEquals("animenosub", anime.getString("provider"))
        assertEquals("bleach", anime.getString("id"))
        assertEquals("https://animenosub.to/anime/bleach/", anime.getString("url"))
        assertFalse(sent.getJSONObject("data").getJSONObject("episode").has("url"))
    }

    @Test
    fun `Linkkf works are the desktop's linkani ones, both ways`() {
        val remote = JSONObject()
        val desktop = desktopAnime("linkani", "777", "링크 작품").put("mal_id", "linkani:777").put("savedAt", 500L)
        val merged = DeviceSyncMapping.merge(local(), listOf(item("library", "linkani:777", desktop, 500L)), remote)
        assertTrue(merged.values.getJSONObject("library").has("777"))
        assertEquals("링크 작품", merged.works.single { it.id == "777" }.title)
        val out = DeviceSyncMapping.outgoing(local(library = setOf("456")), JSONObject(), JSONObject(), emptyMap(), 1000L, hash)
        val sent = out.getJSONObject(0)
        assertEquals("linkani:456", sent.getString("id"))
        assertEquals("linkani", sent.getJSONObject("data").getString("provider"))
        assertEquals("linkani:456", sent.getJSONObject("data").getString("mal_id"))
        assertEquals("https://linkani.tv/ani/456/", sent.getJSONObject("data").getString("url"))
    }

    @Test
    fun `a removal here of the desktop's episode removes it under its id`() {
        val (id, data) = desktopEpisode("frieren", 3, 40.0, 2000L)
        val remote = JSONObject()
        DeviceSyncMapping.merge(local(), listOf(item("history", id, data, 2000L)), remote)
        val snapshot = snapshotOf(local(history = listOf(Triple("reanime:frieren", "3", 40.0))))
        val out = DeviceSyncMapping.outgoing(local(), snapshot, remote, emptyMap(), 5000L, hash)
        assertEquals(id, out.getJSONObject(0).getString("id"))
        assertTrue(out.getJSONObject(0).getBoolean("deleted"))
    }

    @Test
    fun `a removal on the desktop removes this app's entry`() {
        val (id, data) = desktopEpisode("frieren", 3, 40.0, 2000L)
        val remote = JSONObject()
        val first = DeviceSyncMapping.merge(local(), listOf(item("history", id, data, 2000L)), remote)
        val second = DeviceSyncMapping.merge(first.values, listOf(item("history", id, null, 3000L, deleted = true)), remote)
        assertFalse(second.values.getJSONObject("history").has("reanime:frieren:3"))
    }

    @Test
    fun `a desktop work this app has no source for is left out`() {
        val mal = JSONObject().put("mal_id", 52991).put("title", "Frieren").put("savedAt", 1L)
        val merged = DeviceSyncMapping.merge(local(), listOf(item("library", "52991", mal, 1L)), JSONObject())
        assertEquals(0, merged.values.getJSONObject("library").length())
        assertNull(DeviceSyncMapping.libraryId("linkkf:1", JSONObject().put("provider", "linkkf").put("id", "1")))
    }

    @Test
    fun `an entry from an older version of this app is still read`() {
        val legacy = local(history = listOf(Triple("ohli24:slime", "2", 30.0))).getJSONObject("history").getJSONObject("ohli24:slime:2")
        val merged = DeviceSyncMapping.merge(local(), listOf(item("history", "ohli24:slime:2", legacy, 1000L)), JSONObject())
        assertTrue(merged.values.getJSONObject("history").has("ohli24:slime:2"))
        assertEquals(0, merged.works.size)
    }
}
