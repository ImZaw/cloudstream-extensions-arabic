package com.faselhd


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.requestCreator
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element

class FaselHD : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://faselhd.io"
    override var name = "FaselHD"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.AsianDrama, TvType.Anime)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("div.postDiv a").attr("href") ?: return null
        val posterUrl = select("div.postDiv a div img").attr("data-src") ?:
        select("div.postDiv a div img").attr("src")
        val title = select("div.postDiv a div img").attr("alt")
        val quality = select(".quality").first()?.text()?.replace("1080p |-".toRegex(), "")
        val type = if(title.contains("فيلم")) TvType.Movie else TvType.TvSeries
        return MovieSearchResponse(
            title.replace("الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي".toRegex(),""),
            url,
            this@FaselHD.name,
            type,
            posterUrl,
            null,
            null,
            quality = getQualityFromString(quality)
        )
    }
    override val mainPage = mainPageOf(
            "$mainUrl/all-movies/page/" to "Movies",
            "$mainUrl/series/page/" to "Series",
            "$mainUrl/movies_top_imdb/page/" to "Top Movies IMDB",
        )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select("div[id=\"postList\"] div[class=\"col-xl-2 col-lg-2 col-md-3 col-sm-3\"]")
            .mapNotNull { element ->
                element.toSearchResponse()
            }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ","+")
        val d = app.get("$mainUrl/?s=$q").document
        return d.select("div[id=\"postList\"] div[class=\"col-xl-2 col-lg-2 col-md-3 col-sm-3\"]")
            .mapNotNull {
                it.toSearchResponse()
            }
    }


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = doc.select("div.epAll").isEmpty()
        val posterUrl = doc.select("div.posterImg img").attr("src")
            .ifEmpty { doc.select("div.seasonDiv.active img").attr("data-src") }

        val year = doc.select("div[id=\"singleList\"] div[class=\"col-xl-6 col-lg-6 col-md-6 col-sm-6\"]").firstOrNull {
            it.text().contains("سنة|موعد".toRegex())
        }?.text()?.getIntFromText()

        val title =
            doc.select("title").text().replace(" - فاصل إعلاني", "")
                .replace("الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي|$year".toRegex(),"")
        // A bit iffy to parse twice like this, but it'll do.
        val duration = doc.select("div[id=\"singleList\"] div[class=\"col-xl-6 col-lg-6 col-md-6 col-sm-6\"]").firstOrNull {
            it.text().contains("مدة|توقيت".toRegex())
        }?.text()?.getIntFromText()

        val tags = doc.select("div[id=\"singleList\"] div[class=\"col-xl-6 col-lg-6 col-md-6 col-sm-6\"]:contains(تصنيف الفيلم) a").map {
            it.text()
        }
        val recommendations = doc.select("div#postList div.postDiv").mapNotNull {
            it.toSearchResponse()
        }
        val synopsis = doc.select("div.singleDesc p").text()
        return if (isMovie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.duration = duration
                this.tags = tags
                this.recommendations = recommendations
            }
        } else {
            val episodes = ArrayList<Episode>()
            doc.select("div.epAll a").map {
                episodes.add(
                    Episode(
                        it.attr("href"),
                        it.text(),
                        doc.select("div.seasonDiv.active div.title").text().getIntFromText() ?: 1,
                        it.text().getIntFromText(),
                    )
                )
            }
            doc.select("div[id=\"seasonList\"] div[class=\"col-xl-2 col-lg-3 col-md-6\"] div.seasonDiv")
                .not(".active").apmap { it ->
                    val s = app.get("$mainUrl/?p="+it.attr("data-href")).document
                    s.select("div.epAll a").map {
                        episodes.add(
                            Episode(
                                it.attr("href"),
                                it.text(),
                                s.select("div.seasonDiv.active div.title").text().getIntFromText(),
                                it.text().getIntFromText(),
                            )
                        )
                    }
                }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().sortedBy { it.episode }) {
                this.duration = duration
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.tags = tags
                this.recommendations = recommendations
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
        val player = app.get(doc.select(".downloadLinks a").attr("href"), interceptor = CloudflareKiller(), referer = data).document
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name + "Download Source",
                player.select("div.dl-link a").attr("href"),
                this.mainUrl,
                Qualities.Unknown.value
            )
        )
        val webView = WebViewResolver(
            Regex("""master\.m3u8""")
        ).resolveUsingWebView(
            requestCreator(
                "GET", doc.select("iframe[name=\"player_iframe\"]").attr("src"), referer = mainUrl
            )
        ).first
        M3u8Helper.generateM3u8(
            this.name,
            webView?.url.toString(),
            referer = mainUrl
        ).forEach(callback)
        return true
    }
}
