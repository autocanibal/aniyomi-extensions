package eu.kanade.tachiyomi.animeextension.en.uhdmovies

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

@ExperimentalSerializationApi
class UHDMovies : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "UHD Movies"

    override val baseUrl = "https://uhdmovies.world"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/page/$page/")

    override fun popularAnimeSelector(): String = "div#content  div.gridlove-posts > div.layout-masonry"

    override fun popularAnimeNextPageSelector(): String =
        "div#content  > nav.gridlove-pagination > a.next"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            setUrlWithoutDomain(element.select("div.entry-image > a").attr("abs:href"))
            thumbnail_url = element.select("div.entry-image > a > img").attr("abs:src")
            title = element.select("div.entry-image > a").attr("title")
                .replace("Download", "").trim()
        }
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not Used")

    override fun latestUpdatesSelector(): String = throw Exception("Not Used")

    override fun latestUpdatesNextPageSelector(): String = throw Exception("Not Used")

    override fun latestUpdatesFromElement(element: Element): SAnime = throw Exception("Not Used")

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val cleanQuery = query.replace(" ", "+").lowercase()
        return GET("$baseUrl/page/$page/?s=$cleanQuery")
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h2")!!.text()
                .replace("Download", "", true).trim()
            status = SAnime.COMPLETED
        }
    }

    // ============================== Episodes ==============================

    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> {
        val response = client.newCall(GET(baseUrl + anime.url)).execute()
        val resp = response.asJsoup()
        val episodeList = mutableListOf<SEpisode>()
        val episodeElements = resp.select("p:has(a[href*=?id])[style*=center],p:has(a[href*=?id]):has(span.maxbutton-1-center)")
        val qualityRegex = "\\d{3,4}p".toRegex(RegexOption.IGNORE_CASE)
        val firstText = episodeElements.first()!!.text()
        if (firstText.contains("Episode", true) ||
            firstText.contains("Zip", true) ||
            firstText.contains("Pack", true)
        ) {
            episodeElements.map { row ->
                val prevP = row.previousElementSibling()!!
                val seasonRegex = "[ .]S(?:eason)?[ .]?(\\d{1,2})[ .]".toRegex(RegexOption.IGNORE_CASE)
                val result = seasonRegex.find(prevP.text())

                val season = (
                    result?.groups?.get(1)?.value ?: let {
                        val prevPre = row.previousElementSiblings().prev("pre,div.mks_separator")
                        val preResult = seasonRegex.find(prevPre.first()!!.text())
                        preResult?.groups?.get(1)?.value ?: let {
                            val title = resp.select("h1.entry-title")
                            val titleResult = "[ .\\[(]S(?:eason)?[ .]?(\\d{1,2})[ .\\])]".toRegex(RegexOption.IGNORE_CASE).find(title.text())
                            titleResult?.groups?.get(1)?.value ?: "-1"
                        }
                    }
                    ).replaceFirst("^0+(?!$)".toRegex(), "")

                val qualityMatch = qualityRegex.find(prevP.text())
                val quality = qualityMatch?.value ?: let {
                    val qualityMatchOwn = qualityRegex.find(row.text())
                    qualityMatchOwn?.value ?: "HD"
                }

                row.select("a").filter { it ->
                    !it.text().contains("Zip", true) &&
                        !it.text().contains("Pack", true) &&
                        !it.text().contains("Volume ", true)
                }.mapIndexed { index, linkElement ->
                    val episode = linkElement?.text()
                        ?.replace("Episode", "", true)
                        ?.trim()?.toIntOrNull() ?: (index + 1)
                    Triple(
                        season + "_$episode",
                        linkElement.attr("href") ?: return@mapIndexed null,
                        quality,
                    )
                }.filterNotNull()
            }.flatten().groupBy { it.first }.map { group ->
                val (season, episode) = group.key.split("_")
                episodeList.add(
                    SEpisode.create().apply {
                        url = EpLinks(
                            urls = group.value.map {
                                EpUrl(url = it.second, quality = it.third)
                            },
                        ).toJson()
                        name = "Season $season Ep $episode"
                        episode_number = episode.toFloat()
                    },
                )
            }
        } else {
            var collectionIdx = 0F
            episodeElements.asSequence().filter {
                !it.text().contains("Zip", true) &&
                    !it.text().contains("Pack", true) &&
                    !it.text().contains("Volume ", true)
            }.map { row ->
                val prevP = row.previousElementSibling()!!
                val qualityMatch = qualityRegex.find(prevP.text())
                val quality = qualityMatch?.value ?: let {
                    val qualityMatchOwn = qualityRegex.find(row.text())
                    qualityMatchOwn?.value ?: "HD"
                }

                val collectionName = row.previousElementSiblings().prev("h1,h2,h3,pre").first()!!.text()
                    .replace("Download", "", true).trim().let {
                        if (it.contains("Collection", true)) {
                            row.previousElementSibling()!!.ownText()
                        } else {
                            it
                        }
                    }

                row.select("a").map { linkElement ->
                    Triple(linkElement.attr("href"), quality, collectionName)
                }
            }.flatten().groupBy { it.third }.map { group ->
                collectionIdx++
                episodeList.add(
                    SEpisode.create().apply {
                        url = EpLinks(
                            urls = group.value.map {
                                EpUrl(url = it.first, quality = it.second)
                            },
                        ).toJson()
                        name = group.key
                        episode_number = collectionIdx
                    },
                )
            }
            if (episodeList.isEmpty()) throw Exception("Only Zip Pack Available")
        }
        return Observable.just(episodeList.reversed())
    }

    override fun episodeListSelector(): String = throw Exception("Not Used")

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("Not Used")

    // ============================ Video Links =============================

    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> {
        val urlJson = json.decodeFromString<EpLinks>(episode.url)
        val failedMediaUrl = mutableListOf<Pair<String, String>>()
        val videoList = mutableListOf<Video>()
        videoList.addAll(
            urlJson.urls.parallelMap { url ->
                runCatching {
                    val (videos, mediaUrl) = extractVideo(url)
                    if (videos.isEmpty() && mediaUrl.isNotBlank()) failedMediaUrl.add(Pair(mediaUrl, url.quality))
                    return@runCatching videos
                }.getOrNull()
            }
                .filterNotNull()
                .flatten(),
        )

        videoList.addAll(
            failedMediaUrl.mapNotNull { (url, quality) ->
                runCatching {
                    extractGDriveLink(url, quality)
                }.getOrNull()
            }.flatten(),
        )

        if (videoList.isEmpty()) throw Exception("No working links found")

        return Observable.just(videoList.sort())
    }

    override fun videoFromElement(element: Element): Video = throw Exception("Not Used")

    override fun videoListSelector(): String = throw Exception("Not Used")

    override fun videoUrlParse(document: Document): String = throw Exception("Not Used")

// ============================= Utilities ==============================

    private fun extractVideo(epUrl: EpUrl): Pair<List<Video>, String> {
        val postLink = epUrl.url.substringBefore("?id=").substringAfter("/?")
        val formData = FormBody.Builder().add("_wp_http", epUrl.url.substringAfter("?id=")).build()
        val response = client.newCall(POST(postLink, body = formData)).execute().asJsoup()
        val link = response.selectFirst("form#landing")!!.attr("action")
        val wpHttp = response.selectFirst("input[name=_wp_http2]")!!.attr("value")
        val token = response.selectFirst("input[name=token]")!!.attr("value")
        val blogFormData = FormBody.Builder()
            .add("_wp_http2", wpHttp)
            .add("token", token)
            .build()
        val blogResponse = client.newCall(POST(link, body = blogFormData)).execute().body.string()
        val skToken = blogResponse.substringAfter("?go=").substringBefore("\"")
        val tokenUrl = "$postLink?go=$skToken"
        val cookieHeader = Headers.headersOf("Cookie", "$skToken=$wpHttp")
        val tokenResponse = client.newBuilder().followRedirects(false).build()
            .newCall(GET(tokenUrl, cookieHeader)).execute().asJsoup()
        val redirectUrl = tokenResponse.select("meta[http-equiv=refresh]").attr("content")
            .substringAfter("url=").substringBefore("\"")
        val mediaResponse = client.newBuilder().followRedirects(false).build()
            .newCall(GET(redirectUrl)).execute()
        val path = mediaResponse.body.string().substringAfter("replace(\"").substringBefore("\"")
        if (path == "/404") return Pair(emptyList(), "")
        val mediaUrl = "https://" + mediaResponse.request.url.host + path
        val videoList = mutableListOf<Video>()

        for (type in 1..3) {
            videoList.addAll(
                extractWorkerLinks(mediaUrl, epUrl.quality, type),
            )
        }
        return Pair(videoList, mediaUrl)
    }

    private val sizeRegex = "\\[((?:.(?!\\[))+)] *\$".toRegex(RegexOption.IGNORE_CASE)

    private fun extractWorkerLinks(mediaUrl: String, quality: String, type: Int): List<Video> {
        val reqLink = mediaUrl.replace("/file/", "/wfile/") + "?type=$type"
        val resp = client.newCall(GET(reqLink)).execute().asJsoup()
        val sizeMatch = sizeRegex.find(resp.select("div.card-header").text().trim())
        val size = sizeMatch?.groups?.get(1)?.value?.let { " - $it" } ?: ""
        return try {
            resp.select("div.card-body div.mb-4 > a").mapIndexed { index, linkElement ->
                val link = linkElement.attr("href")
                val decodedLink = if (link.contains("workers.dev")) {
                    link
                } else {
                    String(Base64.decode(link.substringAfter("download?url="), Base64.DEFAULT))
                }

                Video(
                    url = decodedLink,
                    quality = "$quality - CF $type Worker ${index + 1}$size",
                    videoUrl = decodedLink,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractGDriveLink(mediaUrl: String, quality: String): List<Video> {
        val tokenClient = client.newBuilder().addInterceptor(TokenInterceptor()).build()
        val response = tokenClient.newCall(GET(mediaUrl)).execute().asJsoup()
        val gdBtn = response.selectFirst("div.card-body a.btn")!!
        val gdLink = gdBtn.attr("href")
        val sizeMatch = sizeRegex.find(gdBtn.text())
        val size = sizeMatch?.groups?.get(1)?.value?.let { " - $it" } ?: ""
        val gdResponse = client.newCall(GET(gdLink)).execute().asJsoup()
        val link = gdResponse.select("form#download-form")
        return if (link.isNullOrEmpty()) {
            listOf()
        } else {
            val realLink = link.attr("action")
            listOf(Video(realLink, "$quality - Gdrive$size", realLink))
        }
    }

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", "1080")!!
        val ascSort = preferences.getString("preferred_size_sort", "asc")!! == "asc"

        val comparator = compareByDescending<Video> { it.quality.contains(quality) }.let { cmp ->
            if (ascSort) {
                cmp.thenBy { it.quality.fixQuality() }
            } else {
                cmp.thenByDescending { it.quality.fixQuality() }
            }
        }
        return this.sortedWith(comparator)
    }

    private fun String.fixQuality(): Float {
        val size = this.substringAfterLast("-").trim()
        return if (size.contains("GB", true)) {
            size.replace("GB", "", true)
                .toFloat() * 1000
        } else {
            size.replace("MB", "", true)
                .toFloat()
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            entries = arrayOf("2160p", "1080p", "720p", "480p")
            entryValues = arrayOf("2160", "1080", "720", "480")
            setDefaultValue("1080")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }
        val sizeSortPref = ListPreference(screen.context).apply {
            key = "preferred_size_sort"
            title = "Preferred Size Sort"
            entries = arrayOf("Ascending", "Descending")
            entryValues = arrayOf("asc", "dec")
            setDefaultValue("asc")
            summary = "%s -  Sort order to be used after the videos are sorted by their quality."

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }

        screen.addPreference(videoQualityPref)
        screen.addPreference(sizeSortPref)
    }

    @Serializable
    data class EpLinks(
        val urls: List<EpUrl>,
    )

    @Serializable
    data class EpUrl(
        val quality: String,
        val url: String,
    )

    private fun EpLinks.toJson(): String {
        return json.encodeToString(this)
    }

    // From Dopebox
    private fun <A, B> Iterable<A>.parallelMap(f: suspend (A) -> B): List<B> =
        runBlocking {
            map { async(Dispatchers.Default) { f(it) } }.awaitAll()
        }
}
