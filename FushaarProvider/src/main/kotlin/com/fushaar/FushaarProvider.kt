package com.fushaar

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
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
        val title = select("div.info h3").text()+"\n"+select("div.info h4").text()

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
            "$mainUrl/page/" to "Movies",
            "$mainUrl/gerne/action/page/" to "Action",
            "$mainUrl/gerne/adventure/page/" to "Adventure",
            "$mainUrl/gerne/animation/page/" to "Animation",
            "$mainUrl/gerne/biography/page/" to "Biography",
            "$mainUrl/gerne/comedy/page/" to "Comedy",
            "$mainUrl/gerne/crime/page/" to "Crime",
            "$mainUrl/gerne/documentary/page/" to "Documentary",
            "$mainUrl/gerne/drama/page/" to "Drama",
            "$mainUrl/gerne/family/page/"	to "Family",
            "$mainUrl/gerne/herror/page/" to "Herror",
            "$mainUrl/gerne/history/page/" to "History",
            "$mainUrl/gerne/music/page/" to "Music",
            "$mainUrl/gerne/mystery/page/" to "Mystery",
            "$mainUrl/gerne/romance/page/" to "Romance",
            "$mainUrl/gerne/sci-fi/page/" to "Sci-fi",
            "$mainUrl/gerne/sport/page/" to "Sport",
            "$mainUrl/gerne/thriller/page/" to "Thriller",         
        )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select("article.poster").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "%20")
        return app.get("$mainUrl/?s=$q").document.select("article.poster").mapNotNull {
            it.toSearchResponse()
        }
    }


    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
        val posterUrl = doc.select("figure.poster noscript img").attr("src")
        val year = doc.select("header span.yearz").text()?.getIntFromText()
        val title = doc.select("header h1").text()+"\n"+doc.select("header h2").text()
        val synopsis = doc.select("div.postz").text()
        val trailer = doc.select("div.rll-youtube-player iframe").attr("src")
        val tags = doc.select("div.z-info li").map { it.text() }


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
            addTrailer(trailer)
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document
            .select("#fancyboxID-8 > script").map {
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = name,
                        url = it.html().substring(252,384),
                        referer = this.mainUrl,
                        quality = Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
        }
return true
    }
}
