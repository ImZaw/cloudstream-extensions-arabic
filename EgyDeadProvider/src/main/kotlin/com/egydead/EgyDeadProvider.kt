package com.egydead

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class EgyDead : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://tv.egydead.live"
    override var name = "EgyDead"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }
    private fun String.cleanTitle(): String {
        return this.replace("جميع مواسم مسلسل|مترجم كامل|مشاهدة فيلم|مترجم|انمي|الموسم.*|مترجمة كاملة|مسلسل|كاملة".toRegex(), "")
    }
    private fun Element.toSearchResponse(): SearchResponse {
        val title = select("h1.BottomTitle").text().cleanTitle()
        val posterUrl = select("img").attr("src")
        val tvType = if (select("span.cat_name").text().contains("افلام")) TvType.Movie else TvType.TvSeries
        return MovieSearchResponse(
            title,
            select("a").attr("href"),
            this@EgyDead.name,
            tvType,
            posterUrl,
            )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/category/افلام-اجنبي/?page=" to "English Movies",
        "$mainUrl/category/افلام-اسيوية/?page=" to "Asian Movies",
        "$mainUrl/season/?page=" to "Series",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("li.movieItem").mapNotNull {
            it.toSearchResponse()
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("li.movieItem").mapNotNull {
            if(it.select("a").attr("href").contains("/episode/")) return@mapNotNull null
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("div.singleTitle em").text().cleanTitle()
        val isMovie = !url.contains("/serie/|/season/".toRegex())

        val posterUrl = doc.select("div.single-thumbnail > img").attr("src")
        val rating = doc.select("a.IMDBRating em").text().getIntFromText()
        val synopsis = doc.select("div.extra-content:contains(القصه) p").text()
        val year = doc.select("ul > li:contains(السنه) > a").text().getIntFromText()
        val tags = doc.select("ul > li:contains(النوع) > a").map { it.text() }
        val recommendations = doc.select("div.related-posts > ul > li").mapNotNull { element ->
            element.toSearchResponse()
        }
        val youtubeTrailer = doc.select("div.popupContent > iframe").attr("src")
        return if (isMovie) {
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
                this.rating = rating
                this.year = year
                addTrailer(youtubeTrailer)
            }
        } else {
            val seasonList = doc.select("div.seasons-list ul > li > a").reversed()
            val episodes = arrayListOf<Episode>()
            if(seasonList.isNotEmpty()) {
                seasonList.apmapIndexed { index, season ->
                    app.get(
                        season.attr("href"),
                    ).document.select("div.EpsList > li > a").map {
                        episodes.add(Episode(
                            it.attr("href"),
                            it.attr("title"),
                            index+1,
                            it.text().getIntFromText()
                        ))
                    }
                }
            } else {
                doc.select("div.EpsList > li > a").map {
                    episodes.add(Episode(
                        it.attr("href"),
                        it.attr("title"),
                        0,
                        it.text().getIntFromText()
                    ))
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().sortedBy { it.episode }) {
                this.posterUrl = posterUrl
                this.tags = tags
                this.plot = synopsis
                this.recommendations = recommendations
                this.rating = rating
                this.year = year
                addTrailer(youtubeTrailer)
            }
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.post(data, data = mapOf("View" to "1")).document
        doc.select(".donwload-servers-list > li").apmap { element ->
            val url = element.select("a").attr("href")
            println(url)
            loadExtractor(url, data, subtitleCallback, callback)
        }
        doc.select("ul.serversList > li").apmap { li ->
            val iframeUrl = li.attr("data-link")
            loadExtractor(iframeUrl, data, subtitleCallback, callback)
        }
        return true
    }
}