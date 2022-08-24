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
    override var mainUrl = "https://movizland.cyou"
    override var name = "Movizland"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("div.BlockItem")
        val posterUrl = select("div img")?.attr("data-src")
        val year = select("ul.InfoEndBlock li").last()?.text()?.getIntFromText()
        var quality = select("ul.RestInformation li").last()?.text()?.replace(" |-|1080p|720p".toRegex(), "")
            ?.replace("WEB DL","WEBDL")?.replace("BluRay","BLURAY")
        val title = select("div.BlockTitle").text()
            .replace("اون لاين", "")
            .replace("مشاهدة و تحميل", "")
            .replace("4K", "")
            .replace("${year.toString()}", "")
            .replace("فيلم", "")
            .replace("مترجم", "")
            .replace("مشاهدة", "")
            .replace("بجودة", "")
            .replace("3D", "")
            .replace("وتحميل", "")
        // val quality =select("ul.RestInformation li").last()?.text()
        return MovieSearchResponse(
            title,
            url.select("a").attr("href"),
            this@Movizland.name,
            TvType.Movie,
            posterUrl,
            year,
            null,
            quality = getQualityFromString(quality),
            // rating = url.select("div.StarsIMDB").text()?.getIntFromText()?.toDouble()
        )
    }
    override val mainPage = mainPageOf(
        "$mainUrl/category/movies/page/" to "Movies",
        "$mainUrl/category/movies/anime/page/" to "Animation",
        "$mainUrl/category/movies/arab/page/" to "Arab",
        "$mainUrl/category/movies/asia/page/" to "Asia",
        "$mainUrl/category/movies/documentary/page/" to "Documentary",
        "$mainUrl/category/movies/foreign/page/" to "Foreign",
        "$mainUrl/category/movies/india/page/" to "India",
        "$mainUrl/category/movies/netflix/page/" to "Netflix",
        "$mainUrl/category/movies/turkey/page/" to "Turkey",
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
            //"$mainUrl/category/series/?s=$q"
        ).apmap { url ->
            val d = app.get(url).document
            d.select("div.BlockItem").mapNotNull {
                if (it.text().contains("اعلان")) return@mapNotNull null
                it.toSearchResponse()?.let { it1 -> result.add(it1) }
            }
        }
        return result.distinct().sortedBy { it.name }
    }

    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
        val posterUrl = doc.select("img")?.attr("data-src")
        val year = doc.select("div.SingleDetails a").last()?.text()?.getIntFromText()
        val title = doc.select("h2.postTitle").text()
            .replace("اون لاين", "")
            .replace("مشاهدة و تحميل", "")
            .replace("4K", "")
            .replace("${year.toString()}", "")
            .replace("فيلم", "")
            .replace("مترجم", "")
            .replace("مشاهدة", "")
            .replace("بجودة", "")
            .replace("3D", "")
            .replace("وتحميل", "")
        val isMovie = doc.select("h2.postTitle").text().contains("فيلم".toRegex())
        val synopsis = doc.select("section.story").text()
        val trailer = doc.select("div.InnerTrailer iframe").attr("src")
        val tags = doc.select("div.SingleDetails").select("li")?.map { it.text() }


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
        val doc = app.get(data).document
//        doc.select(
//            "li:contains(dood), li:contains(streamlare), li:contains(streamtape), li:contains(uqload), li:contains(upstream)"
//        ).map {
//            val dataServer = it.attr("data-server")
//            val url = it.select("code#EmbedSc$dataServer iframe").attr("data-srcout")
//            println(url)
//            loadExtractor(url, data, subtitleCallback, callback)
//        }
        doc.select("table tbody tr").map {
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            this.name,
                            it.select("a").attr("href"),
                            this.mainUrl,
                            quality = getQualityFromName(it.select("td:nth-child(2)").text().replace("Original","1080"))
                        )
                    )
            }
        return true
    }
}
