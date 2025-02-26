package eu.kanade.tachiyomi.animeextension.id.kuronime.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.POST
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class StreamlareExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, name: String): List<Video> {
        val id = url.split("/").last()
        val videoList = mutableListOf<Video>()
        val playlist = client.newCall(
            POST(
                "https://slwatch.co/api/video/stream/get",
                body = "{\"id\":\"$id\"}"
                    .toRequestBody("application/json".toMediaType()),
            ),
        ).execute().body.string()

        playlist.substringAfter("\"label\":\"").split("\"label\":\"").forEach {
            val quality = it.substringAfter("\"label\":\"").substringBefore("\",") + " - $name"
            val token = it.substringAfter("\"file\":\"https:\\/\\/larecontent.com\\/video?token=")
                .substringBefore("\",")
            val response = client.newCall(POST("https://larecontent.com/video?token=$token")).execute()
            val videoUrl = response.request.url.toString()
            videoList.add(Video(videoUrl, quality, videoUrl))
        }
        return videoList
    }
}
