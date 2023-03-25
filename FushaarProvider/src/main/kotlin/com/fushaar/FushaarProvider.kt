package com.fushaar

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Fushaar : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://www.fushaar.com"
    override var name = "Fushaar"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)


    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("article.poster")
        val posterUrl = select("img").attr("data-lazy-src")
        val year = select("ul.labels li.year").text()?.getIntFromText()
        var quality = select("div").first()?.attr("class")?.replace("hdd","hd")?.replace("caam","cam")
        val titleOne = select("div.info h3").text()
        val titleTwo = select("div.info h4").text()
        val title = if(titleOne == titleTwo && titleOne.isNotEmpty()) titleOne else "$titleOne\n$titleTwo"

        return MovieSearchResponse(
            title,
            url.select("a").attr("href"),
            this@Fushaar.name,
            TvType.Movie,
            posterUrl,
            year,
            null,
            quality = getQualityFromString(quality),
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Movies | أفلام",
        "$mainUrl/gerne/action/page/" to "Action | أكشن",
        "$mainUrl/gerne/adventure/page/" to "Adventure | مغامرة",
        "$mainUrl/gerne/animation/page/" to "Animation | أنيمايشن",
        "$mainUrl/gerne/biography/page/" to "Biography | سيرة",
        "$mainUrl/gerne/comedy/page/" to "Comedy | كوميديا",
        "$mainUrl/gerne/crime/page/" to "Crime | جريمة",
        "$mainUrl/gerne/documentary/page/" to "Documentary | وثائقي",
        "$mainUrl/gerne/drama/page/" to "Drama | دراما",
        "$mainUrl/gerne/family/page/"	to "Family | عائلي",
        "$mainUrl/gerne/fantasy/page/"	to "Fantasy | فنتازيا",
        "$mainUrl/gerne/herror/page/" to "Herror | رعب",
        "$mainUrl/gerne/history/page/" to "History | تاريخي",
        "$mainUrl/gerne/music/page/" to "Music | موسيقى",
        "$mainUrl/gerne/musical/page/" to "Musical | موسيقي",
        "$mainUrl/gerne/mystery/page/" to "Mystery | غموض",
        "$mainUrl/gerne/romance/page/" to "Romance | رومنسي",
        "$mainUrl/gerne/sci-fi/page/" to "Sci-fi | خيال علمي",
        "$mainUrl/gerne/short/page/" to "Short | قصير",
        "$mainUrl/gerne/sport/page/" to "Sport | رياضة",
        "$mainUrl/gerne/thriller/page/" to "Thriller | إثارة",
        "$mainUrl/gerne/war/page/" to "War | حرب",
        "$mainUrl/gerne/western/page/" to "Western | غربي",
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select("article.poster").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        return app.get("$mainUrl/?s=$q").document.select("article.poster").mapNotNull {
            it.toSearchResponse()
        }
    }


    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
        val bigPoster = doc.select("""meta[property="og:image"]""").attr("content")
        val posterUrl = bigPoster.ifEmpty() { doc.select("figure.poster noscript img").attr("src")}
        val year = doc.select("header span.yearz").text()?.getIntFromText()
        val ARtitle = doc.select("header h1").text()
        val ENtitle = doc.select("header h2").text()
        val title = if( ARtitle.isNotEmpty() && ENtitle.isNotEmpty() ) "$ARtitle | $ENtitle"  else if(ARtitle == ENtitle) ARtitle else "$ARtitle$ENtitle"
        val synopsis = doc.select("div.postz").text()
        val trailer = doc.select("#new-stream > div > div.ytb > a").attr("href")
        val tags = doc.select("div.zoomInUp  a").map{it.text()}//doc.select("li.iifo").map { it.select("span.z-s-i").text()+" "+it.select("h8").text() }
        val rating = doc.select("body > div.new-info.hide-mobile > div > div.z-imdb > div").text()?.toRatingInt()
        val recommendations = doc.select("article.poster").mapNotNull { element ->
                element.toSearchResponse()
        }  
        
        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = posterUrl
            this.year = year
            this.tags = tags
            this.plot = synopsis
            this.rating = rating
            this.recommendations = recommendations
            addTrailer(trailer)
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        var sourceUrl = doc.select("div:nth-child(3) > div > iframe,div:nth-child(4) > div > iframe").attr("data-lazy-src")
        loadExtractor(sourceUrl, data, subtitleCallback, callback)
            doc.select("#fancyboxID-download > center > a:nth-child(n+19),#fancyboxID-1 > center > a:nth-child(n+16)").map {
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = it.text() ?: name,
                        url = it.attr("href"),
                        referer = this.mainUrl,
                        quality = it.text().getIntFromText() ?: Qualities.Unknown.value,
                )
                )
            }
        return true
    }
}