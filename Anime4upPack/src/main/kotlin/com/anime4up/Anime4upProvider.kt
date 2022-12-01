package com.anime4up

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.ExtractorLink
import okio.ByteString.Companion.decodeBase64
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URL

private fun String.getIntFromText(): Int? {
    return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
}
class WitAnime : Anime4up() {
    override var name = "WitAnime"
    override var mainUrl = "https://witanime.com"
}
open class Anime4up : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://anime4up.tv"
    override var name = "Anime4up"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes =
        setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA, TvType.Others )

    
    private fun Element.toSearchResponse(): SearchResponse {
        val imgElement = select("div.hover > img")
        val url = select("div.hover > a").attr("href")
            .replace("-%d8%a7%d9%84%d8%ad%d9%84%d9%82%d8%a9-.*".toRegex(), "")
            .replace("episode", "anime")
        val title = imgElement.attr("alt")
        val posterUrl = imgElement.attr("src")
        val typeText = select("div.anime-card-type > a").text()
        val type =
            if (typeText.contains("TV|Special".toRegex())) TvType.Anime
            else if(typeText.contains("OVA|ONA".toRegex())) TvType.OVA
            else if(typeText.contains("Movie")) TvType.AnimeMovie
            else TvType.Others
        return newAnimeSearchResponse(
            title,
            url,
            type,
        ) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/").document
        val homeList = doc.select(".page-content-container")
            .mapNotNull {
                val title = it.select("div.main-didget-head h3").text()
                val list =
                    it.select("div.anime-card-container, div.episodes-card-container").map {
                            anime -> anime.toSearchResponse()
                    }.distinct()
                HomePageList(title, list, isHorizontalImages = title.contains("حلقات"))
            }
        return newHomePageResponse(homeList, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?search_param=animes&s=$query").document
            .select("div.row.display-flex > div").mapNotNull {
                it.toSearchResponse()
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.select("h1.anime-details-title").text()
        val poster = doc.select("div.anime-thumbnail img").attr("src")
        val description = doc.select("p.anime-story").text()
        val year = doc.select("div.anime-info:contains(بداية العرض)").text().replace("بداية العرض: ", "").toIntOrNull()

        val typeText = doc.select(".anime-info:contains(النوع) a").text()
        val type =
            if (typeText.contains("TV|Special".toRegex())) TvType.Anime
            else if(typeText.contains("OVA|ONA".toRegex())) TvType.OVA
            else if(typeText.contains("Movie")) TvType.AnimeMovie
            else TvType.Others

        val malId = doc.select("a.anime-mal").attr("href").replace(".*e\\/|\\/.*".toRegex(),"").toIntOrNull()

        val episodes = doc.select("div#DivEpisodesList > div").apmap {
            val episodeElement = it.select("h3 a")
            val episodeUrl = episodeElement.attr("href")
            val episodeTitle = episodeElement.text()
            val posterUrl = it.select(".hover img").attr("src")
            Episode(
                episodeUrl,
                episodeTitle,
                episode = episodeTitle.getIntFromText(),
                posterUrl = posterUrl
            )
        }
        return newAnimeLoadResponse(title, url, type) {
            this.apiName = this@Anime4up.name
            addMalId(malId)
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(if(title.contains("مدبلج")) DubStatus.Dubbed else DubStatus.Subbed, episodes)
            plot = description
            this.rating = rating
            
        }
    }
    data class sources (
        @JsonProperty("hd"  ) var hd  : Map<String, String>? = null,
        @JsonProperty("fhd" ) var fhd : Map<String, String>? = null,
        @JsonProperty("sd"  ) var sd  : Map<String, String>?  = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        if(data.contains("anime4up")) {
            val watchJSON = parseJson<sources>(doc.select("input[name=\"wl\"]").attr("value").decodeBase64()?.utf8() ?: "")
            watchJSON.let { source ->
                source.fhd?.apmap {
                    loadExtractor(it.value, data, subtitleCallback, callback)
                }
                source.hd?.apmap {
                    loadExtractor(it.value, data, subtitleCallback, callback)
                }
                source.sd?.apmap {
                    loadExtractor(it.value, data, subtitleCallback, callback)
                }
            }
            val moshahdaID =  doc.select("input[name=\"moshahda\"]").attr("value").decodeBase64()?.utf8()
            if(moshahdaID.isNotEmpty()) {
                mapOf(
                    "Original" to "download_o",
                    "720" to "download_x",
                    "480" to "download_h",
                    "360" to "download_n",
                    "240" to "download_l"
                ).apmap { (quality, qualityCode) ->
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            this.name + " Moshahda",
                            "https://moshahda.net/$moshahdaID.html?${qualityCode}",
                            "https://moshahda.net",
                            quality.toIntOrNull() ?: 1080
                        )
                    ) }
            }
        } else if(data.contains("witanime")) { // witanime
            doc.select("ul#episode-servers li a").apmap {
                loadExtractor(it.attr("data-ep-url"), data, subtitleCallback, callback)
            }
        }
        return true
    }
}
