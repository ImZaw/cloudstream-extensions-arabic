package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.nodes.Element

class FushaarProvider : MainAPI() {
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
            this@FushaarProvider.name,
            TvType.Movie,
            posterUrl.also{print("posterurl :"+it)},
            year,
            null,
            quality = getQualityFromString(quality),
        )
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        // Title, Url
        val moviesUrl = listOf(
            "Movies" to "$mainUrl/page/" + (0..25).random(),
            "Herror" to "$mainUrl/gerne/herror",
            "Thriller" to "$mainUrl/gerne/thriller",
            "Action" to "$mainUrl/gerne/action",
            "Animation" to "$mainUrl/gerne/animation",
            "Comedy" to "$mainUrl/gerne/comedy",
            "Sci-fi" to "$mainUrl/gerne/sci-fi",
            "Crime" to "$mainUrl/gerne/crime",
            "Drama" to "$mainUrl/gerne/drama",
            "Adventure" to "$mainUrl/gerne/adventure",
            "Biography" to "$mainUrl/gerne/biography",
            "Music" to "$mainUrl/gerne/music",
            "Sport" to "$mainUrl/gerne/sport",
            "Documentary" to "$mainUrl/gerne/documentary",
            "History" to "$mainUrl/gerne/history",
            "Family" to "$mainUrl/gerne/family",
            "Romance" to "$mainUrl/gerne/romance",
            "Mystery" to "$mainUrl/gerne/mystery"
        )
        val pages = moviesUrl.apmap {
            val doc = app.get(it.second).document
            val list = doc.select("article.poster").mapNotNull { element ->
                element.toSearchResponse()
            }
            HomePageList(it.first, list)
        }.sortedBy { it.name }
        return HomePageResponse(pages)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "%20")
        val result = arrayListOf<SearchResponse>()
        listOf(
            "$mainUrl/?s=$q",
        ).apmap { url ->
            val d = app.get(url).document
            d.select("article.poster").mapNotNull {
                it.toSearchResponse()?.let { it1 -> result.add(it1) }
            }
        }
        return result.distinct().sortedBy { it.name }
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
            "$url"
        ) {
            this.posterUrl = posterUrl.also{print("posterurl2 :"+it)}
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
                        quality = 1080,
                        isM3u8 = true
                    )
                )
        }
return true
    }
}