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
        .replace("مشاهدة","").replace("وتحميل","").replace("فيلم","").replace("مسلسل","").replace("مترجم||مترجمة","").replace("انمي","").replace("عرض","")
        val posterUrl = select("div.BlockImageItem img")?.attr("src")
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
        )
    }
    override val mainPage = mainPageOf(
        "$mainUrl/category/movies/page/" to "Movies",
        "$mainUrl/category/series/page/" to "Series",
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
            "$mainUrl/?s=$q",
        ).apmap { url ->
            val d = app.get(url).document
            d.select("div.BlockItem").mapNotNull {
                it.toSearchResponse()?.let { it1 -> result.add(it1) }
            }
        }
        return result.distinct().sortedBy { it.name }
    }
    
    
    private fun Element.toEpisode(): Episode {
        var EpiIt = select("div.EpisodeItem").isNotEmpty()
        var EpiSt = if (EpiIt) true else false
        if(EpiSt){
            val a = select("div.EpisodeItem")
            val url = a.select("a")?.attr("href")
            val title = select("h2 > span > a").text()
            val Epsnum = a.select("a > em").text().getIntFromText()
        return newEpisode(url) {
            name = title
            episode = Epsnum
            //posterUrl = thumbUrl
            }
        }else{
            val a = select("div.BlockItem")
            val url = a.select("a")?.attr("href")
            val title = a.select("div.BlockTitle").text()
            val Epsnum = a.select("div.EPSNumber").text().getIntFromText()
         return newEpisode(url) {
            name = title
            episode = Epsnum
            }
        }
    }
    
    

    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
        val sdetails = doc.select(".SingleDetails")
        val isMovie = if(doc.select("h2.postTitle").text().contains("عرض|فيلم".toRegex())) true else false
        val title = if(isMovie){doc.select("h2.postTitle").text()}else{doc.select("div > h2 > span > a").text()}
        .replace("مشاهدة","").replace("وتحميل","").replace("فيلم","").replace("مسلسل","").replace("مترجم||مترجمة","").replace("انمي","").replace("عرض","")
        val posterUrl = sdetails.select("img")?.attr("data-src")
        val year = sdetails.select("li:has(.fa-clock) a").text()?.getIntFromText()
        val synopsis = doc.select("section.story").text()
        val trailer = doc.select("div.InnerTrailer iframe").attr("data-src")
        val tags = sdetails.select("li:has(.fa-film) a").map{ it.text() }
        
        val recmovies = doc.select("div.RecentItems .BlockItem").map { element ->
            MovieSearchResponse(
                apiName = this@Movizland.name,
                url = element.select("a").attr("href"),
                name = element.select(".BlockTitle").text(),
                posterUrl = element.select(".BlockImageItem img")?.attr("data-src")
            )
        }
        
        val recseries = doc.select(".BottomBarRecent .ButtonsFilter.WidthAuto li li").map { element ->
            MovieSearchResponse(
                apiName = this@Movizland.name,
                url = element.select("a").attr("href"),
                name = element.text(),
                posterUrl = doc.select(".SingleDetails img")?.attr("data-src")
            )
        }

        
        val recommendations = if(isMovie){ recmovies }else{ recseries }

        
        
        
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
                this.recommendations = recommendations
                addTrailer(trailer)
            }
    }else{      
                var EpIt = doc.select("div.EpisodeItem").isNotEmpty()
                var EpSt = if (EpIt) true else false
                var episodes = if(EpSt) { doc.select("div.EpisodeItem").map { it.toEpisode()} } else { doc.select("div.BlockItem").map { it.toEpisode() } }
             
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.tags = tags
                this.plot = synopsis
                this.recommendations = recommendations
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
