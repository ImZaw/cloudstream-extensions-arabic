package com.fajershow

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URI
import java.util.*
import kotlin.collections.ArrayList

class FajerShow : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://fajer.show"
    override var name = "FajerShow"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }
    

    private fun Element.toSearchResponse(home: Boolean): SearchResponse? {
        val quality = select("span.quality").text().replace("-|p".toRegex(), "")
        if(home == true) {
            val titleElement = select("div.data h3 a")
            val posterUrl = select("img").attr("src")
            val tvType = if (titleElement.attr("href").contains("/movies/")) TvType.Movie else TvType.TvSeries
            // If you need to differentiate use the url.
            return MovieSearchResponse(
                titleElement.text().replace(".*\\|".toRegex(), ""),
                titleElement.attr("href"),
                this@FajerShow.name,
                tvType,
                posterUrl,
                quality = getQualityFromString(quality)
            )
        } else {
            val posterElement = select("img")
            val url = select("div.thumbnail > a").attr("href")
            return MovieSearchResponse(
                posterElement.attr("alt"),
                url,
                this@FajerShow.name,
                if (url.contains("/movies/")) TvType.Movie else TvType.TvSeries,
                posterElement.attr("src"),
                quality = getQualityFromString(quality)
            )
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/genre/english-movies/page/" to "English Movies",
        "$mainUrl/genre/arabic-movies/page/" to "Arabic Movies",
        "$mainUrl/genre/turkish-movies/page/" to "Turkish Movies",
        "$mainUrl/genre/animation/page/" to "Animation Movies",
        "$mainUrl/genre/english-series/page/" to "English Series",
        "$mainUrl/genre/arabic-series/page/" to "Arabic Series",
        "$mainUrl/genre/turkish-series/page/" to "Turkish Series",
        "$mainUrl/genre/indian-series/page/" to "Indian Series",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article.item").mapNotNull {
            it.toSearchResponse(true)
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select(".result-item > article").mapNotNull {
            it.toSearchResponse(false)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = url.contains("/movies/")

        val posterUrl = doc.select("div.poster > img").attr("src")
        val rating = doc.select("span[itemprop=\"ratingValue\"]").text().toIntOrNull()
        val title = doc.select("div.data > h1").text()
        val synopsis = doc.select("div.wp-content > p").text()

        val tags = doc.select("a[rel=\"tag\"]")?.map { it.text() }

        val actors = doc.select("div.person").mapNotNull {
            val name = it.selectFirst("div > a > img")?.attr("alt") ?: return@mapNotNull null
            val image = it.selectFirst("div > a > img")?.attr("src") ?: return@mapNotNull null
            val roleString = it.select("div.data > div.caracter").text()
            val mainActor = Actor(name, image)
            ActorData(actor = mainActor, roleString = roleString)
        }

        return if (isMovie) {
            val recommendations = doc.select(".owl-item article").mapNotNull { element ->
                element.toSearchResponse(true)
            }

            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.recommendations = recommendations
                this.plot = synopsis
                this.tags = tags
                this.actors = actors
                this.rating = rating
            }
        } else {
            val episodes = doc.select(".se-c ul > li").map {
                Episode(
                    it.select("div.episodiotitle > a").attr("href"),
                    it.select("div.episodiotitle > a").text(),
                    it.select("div.numerando").text().split(" - ")[0].toInt(),
                    it.select("div.numerando").text().split(" - ")[1].toInt(),
                    it.select("div.imagen a img").attr("src")
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().sortedBy { it.episode }) {
                this.posterUrl = posterUrl
                this.tags = tags
                this.plot = synopsis
                this.actors = actors
            }
        }
    }

    data class FajerLive (
        @JsonProperty("success"  ) var success  : Boolean?          = null,
        @JsonProperty("data"     ) var data     : ArrayList<Data>   = arrayListOf(),
    )
    data class Data (
        @JsonProperty("file"  ) var file  : String? = null,
        @JsonProperty("label" ) var label : String? = null,
        @JsonProperty("type"  ) var type  : String? = null
    )
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("li.vid_source_option").not("[data-nume=\"trailer\"]").apmap { source ->
            app.post(
                "$mainUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to source.attr("data-post"),
                    "nume" to source.attr("data-nume"),
                    "type" to source.attr("data-type")
                )
            ).document.select("iframe").attr("src").let {
                val hostname = URI(it).host
                if (it.contains("show.alfajertv.com")) {
                    val url = URI(it)
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            "AlfajerTV Palestine",
                            url.query.replace("&.*|source=".toRegex(), ""),
                            data,
                            Qualities.Unknown.value,
                            url.query.replace("&.*|source=".toRegex(), "").contains(".m3u8")
                        )
                    )
                    println("Palestine\n" + url.query.replace("&.*|source=".toRegex(), "") + "\n")
                }
                else if (it.contains("fajer.live")) {
                    val id = it.split("/v/").last().split('/')[0];
                    val response = parseJson<FajerLive>(app.post("https://$hostname/api/source/$id", data = mapOf("r" to "", "d" to hostname)).text)
                    response.data.forEach {
                        callback.invoke(
                            ExtractorLink(
                                this.name,
                                "FajerLive",
                                it.file ?: "",
                                data,
                                it.label?.getIntFromText() ?: Qualities.Unknown.value,
                                it.type != "mp4"
                            )
                        )
                    }
                    println("FajerLive\n$response\n") // parse using FajerLive data class
                }
                else if (it.contains("vidmoly.to")) {
                    val doc = app.get(it).document
                    val m3u8 = doc.select("body > script").map { it.data() }.first { it.contains("sources") }.substringAfter("sources: [{file:\"").substringBefore("\"}],")
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            "Vidmoly",
                            m3u8,
                            data,
                            Qualities.Unknown.value,
                            m3u8.contains(".m3u8")
                        )
                    )
                    println("VIDMOLY.TO\n$m3u8\n")
                }
                else if (it.contains("voe.sx")) {
                    val doc = app.get(it).document
                    val script = doc.select("script").map { it.data() }.first { it.contains("sources") }
                    val m3u8 = script.substringAfter("'hls': '").substringBefore("'")
                    val mp4 = script.substringAfter("'mp4': '").substringBefore("'")
                    val quality = script.substringAfter("'video_height': ").substringBefore(",").toInt()
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            "Voe.sx m3u8",
                            m3u8,
                            data,
                            quality,
                            true
                        )
                    )
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            "Voe.sx mp4",
                            mp4,
                            data,
                            quality,
                        )
                    )
                    println("VOE.SX\n$m3u8\n$mp4\n$quality\n")
                }
                else loadExtractor(it, data, subtitleCallback, callback)
            }
        }
        return true
    }
}