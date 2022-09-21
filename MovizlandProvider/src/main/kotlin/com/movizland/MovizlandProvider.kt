package com.movizland


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Movizland : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://new.movizland.cyou"
    override var name = "Movizland"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime, TvType.Cartoon)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("div.BlockItem")
        val title = select("div.BlockTitle").text()
        val posterUrl = if (title.contains("فيلم")) {select("div.BlockImageItem img")?.attr("src")} else {select("div.BlockImageItem > img:nth-child(3)")?.attr("src")} 
        val year = select("ul.InfoEndBlock li").last()?.text()?.getIntFromText()
        var quality = select("ul.RestInformation li").last()?.text()?.replace(" |-|1080p|720p".toRegex(), "")
            ?.replace("WEB DL","WEBDL")?.replace("BluRay","BLURAY")
        return MovieSearchResponse(
            title,
            url.select("a").attr("href"),
            this@Movizland.name,
            TvType.TvSeries,
            posterUrl,
            year,
            null,
            quality = getQualityFromString(quality),
            // rating = url.select("div.StarsIMDB").text()?.getIntFromText()?.toDouble()
        )
    }
    override val mainPage = mainPageOf(
        "$mainUrl/category/movies/page/" to "Movies",
        "$mainUrl/series/page/" to "Series",
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select("div.BlockItem").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "%20")
        val result = arrayListOf<SearchResponse>()
        listOf(
            "$mainUrl/category/movies/?s=$q",
            "$mainUrl/series/?searching=$q"
        ).apmap { url ->
            val d = app.get(url).document
            d.select("div.BlockItem").mapNotNull {
                //if (!it.text().contains("فيلم||موسم")) return@mapNotNull null
                it.toSearchResponse()?.let { it1 -> result.add(it1) }
            }
        }
        return result.distinct().sortedBy { it.name }
    }
    
    
    private fun Element.toEpisode(): Episode {
        val a = select("div.BlockItem")
        val url = a.select("a")?.attr("href")
        val title = a.select("div.BlockTitle").text()
        val thumbUrl = a.select("div.BlockImageItem img")?.attr("src")
        val Epsnum = a.select("div.EPSNumber").text()
        return newEpisode(url) {
            name = title
            episode = Epsnum.getIntFromText()
            posterUrl = thumbUrl
        }
    }
    
    

    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
        val posterUrl = doc.select("img")?.attr("data-src")
        val year = doc.select("div.SingleDetails a").last()?.text()?.getIntFromText()
        val title = doc.select("h2.postTitle").text()
        val isMovie = title.contains("فيلم")
        val synopsis = doc.select("section.story").text()
        val trailer = doc.select("div.InnerTrailer iframe").attr("data-src")
        val tags = doc.select("div.SingleDetails li").map{ it.text() }


        return if (isMovie) {
        newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.tags = tags
                this.plot = synopsis
                addTrailer(trailer)
            }
    }else{
                val episodes = doc.select("div.BlockItem").map {
                it.toEpisode()
            }
                
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.tags = tags
                this.plot = synopsis
                addTrailer(trailer)
               }
            }
        }

          
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("code[id*='Embed']").apmap {
                            var sourceUrl = it.select("iframe").attr("data-srcout")
                            loadExtractor(sourceUrl, data, subtitleCallback, callback)
            }
        doc.select("table tbody tr").map {
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            this.name,
                            it.select("a").attr("href"),
                            this.mainUrl,
                            quality = it.select("td:nth-child(2)").text().getIntFromText() ?: Qualities.Unknown.value,
                        )
                    )
            }
        return true
    }
}
