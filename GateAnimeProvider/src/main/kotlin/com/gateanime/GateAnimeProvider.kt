package com.gateanime


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class GateAnime : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://b.gateanime.cam"
    override var name = "GateAnime"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes =
        setOf(TvType.Anime, TvType.AnimeMovie, TvType.Cartoon )

    fun hasEnglishLetters(string: String): Boolean {
        for (c in string)
        {
            if (c !in 'A'..'Z' && c !in 'a'..'z') {
                return false
            }
        }
        return true
    }
    
    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("a").attr("href")
        val title = select("h3.Title").text()
        val posterUrl = select("img").attr("src")
        val type =
            if (select("span.TpTv.BgA").isNotEmpty()) TvType.Anime else TvType.AnimeMovie
        val year = select("span.Year").text().toIntOrNull()
        return newAnimeSearchResponse(
            title,
            url,
            type,
        ) {
            addDubStatus(title.contains("مدبلج") || !hasEnglishLetters(title))
            this.year = year
            this.posterUrl = posterUrl
        }
    }
    override val mainPage = mainPageOf(
            "$mainUrl/movies/page/" to "Anime Movies",
            "$mainUrl/series/page/" to "Anime",
            "$mainUrl/category/مدبلج/page/" to "Dubbed"
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select("ul li.TPostMv")
            .mapNotNull { element ->
                element.toSearchResponse()
            }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?s=$query").document.select("ul li.TPostMv").mapNotNull {
                it.toSearchResponse()
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.select("h1.Title").text()
        val poster = doc.select("div.Image img").attr("src")
        val description = doc.select("p:contains(قصة)").first()?.text()
        val genre = doc.select("p:contains(التصنيفات)").text().replace("التصنيفات : ", "").split("،")
        val year = doc.select(".Date").text().toIntOrNull()
        val rating = doc.select("span.AAIco-star").text().toIntOrNull()

        val nativeName = doc.select(".SubTitle").text()
        val type = if(url.contains("movie")) TvType.AnimeMovie else TvType.Anime

        val malId = doc.select("a:contains(myanimelist)").attr("href").replace(".*e\\/|\\/.*".toRegex(),"").toIntOrNull()

        val episodes = arrayListOf<Episode>()
        val backgroundImage = doc.select("img.TPostBg").first()?.attr("src")
        val seasonsElements = doc.select("div.Wdgt.AABox")
        if(seasonsElements.isEmpty()) {
            episodes.add(Episode(
                url,
                "Watch",
                posterUrl = backgroundImage
            ))
        } else {
            seasonsElements.map { season ->
                val seasonNumber = season.select("div.Title").attr("data-tab").toIntOrNull()
                season.select("tr").forEach {
                    val titleTd = it.select("td.MvTbTtl a")
                    episodes.add(Episode(
                        titleTd.attr("href"),
                        titleTd.text(),
                        seasonNumber,
                        it.select("span.Num").text().toIntOrNull()
                    ))
                }
            }
        }
        return newAnimeLoadResponse(title, url, type) {
            addMalId(malId)
            japName = nativeName
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(if(title.contains("مدبلج")) DubStatus.Dubbed else DubStatus.Subbed, episodes) // TODO CHECK
            plot = description
            tags = genre
            this.rating = rating
            
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        println("URL: $data")
        val doc = app.get(data).document
        doc.select(
            "li:contains(Fembed), li:contains(خيارات 1), li:contains(Uptostream), li:contains(Dood), li:contains(Uqload), li:contains(Drive)"
        ).apmap {
                val id = it.attr("data-tplayernv")
                val iframeLink = doc.select("div#$id").html().replace(".*src=\"|\".*|#038;|amp;".toRegex(), "").replace("<noscript>.*".toRegex(),"")
                var sourceUrl = app.get(iframeLink).document.select("iframe").attr("src")
                if(sourceUrl.contains("ok.ru")) sourceUrl = "https:$sourceUrl"
                if(sourceUrl.contains("drive.google.com")) sourceUrl = "https://gdriveplayer.to/embed2.php?link=$sourceUrl"
                loadExtractor(sourceUrl, data, subtitleCallback, callback)
            }
        return true
    }
}
