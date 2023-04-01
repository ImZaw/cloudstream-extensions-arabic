package com.shahid4u


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element

class Shahid4u : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://shahid4uu.cam"
    override var name = "Shahid4u"
    override val usesWebView = false
    override val hasMainPage = true
	private  val cfKiller = CloudflareKiller()
    override val supportedTypes =
        setOf(TvType.TvSeries, TvType.Movie, TvType.Anime, TvType.AsianDrama)
	
	private fun String.getDomainFromUrl(): String? {
        return Regex("""^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n\?\=]+)""").find(this)?.groupValues?.firstOrNull()
    }
    private fun Element.toSearchResponse(): SearchResponse? {
        val urlElement = select("a.fullClick")
        val posterUrl =
            select("a.image img").let { it.attr("data-src").ifEmpty { it.attr("data-image") } }
        val quality = select("span.quality").text().replace("1080p |-".toRegex(), "")
        val type =
            if (select(".category").text().contains("افلام")) TvType.Movie else TvType.TvSeries
        return MovieSearchResponse(
            urlElement.attr("title")
                .replace("برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي".toRegex(), ""),
            urlElement.attr("href") ?: return null,
            this@Shahid4u.name,
            type,
            posterUrl,
            null,
            null,
            quality = getQualityFromString(quality)
        )
    }
    override val mainPage = mainPageOf(
            "$mainUrl/movies-3/page/" to "Movies",
            "$mainUrl/netflix/page/" to "Series & Anime",
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var doc = app.get(request.data + page).document
		if(doc.select("title").text() == "Just a moment...") {
            doc = app.get(request.data + page, interceptor = cfKiller, timeout = 120).document
        }
        val list = doc.select("div.content-box")
            .mapNotNull { element ->
                element.toSearchResponse()
            }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val finalResult = arrayListOf<SearchResponse>()
        listOf(
            "$mainUrl/?s=$query&category=&type=movie",
            "$mainUrl/?s=$query&type=series"
        ).apmap { url ->
            var doc = app.get(url).document
			if(doc.select("title").text() == "Just a moment...") {
				doc = app.get(url, interceptor = cfKiller, timeout = 120).document
			}
			doc.select("div.content-box").mapNotNull {
                finalResult.add(it.toSearchResponse() ?: return@mapNotNull null)
            }
        }
        return finalResult
    }

    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
		if(doc.select("title").text() == "Just a moment...") {
			doc = app.get(url, interceptor = cfKiller, timeout = 120).document
		}
        val isMovie =
            doc.select("ul.half-tags:contains(القسم) li:nth-child(2)").text().contains("افلام")
        val posterUrl =
            doc.select("a.poster-image").attr("style").replace(".*url\\(|\\);".toRegex(), "")

        val year = doc.select("ul.half-tags:contains(السنة) li:nth-child(2)").text().toIntOrNull()

        val title =
            doc.select("div.breadcrumb a:nth-child(3)").text()
                .replace(
                    "الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي|$year".toRegex(),
                    ""
                )

        val tags = doc.select("ul.half-tags:contains(النوع) li").not(":nth-child(1)").map {
            it.text()
        }
        val recommendations =
            doc.select("div.MediaGrid").first()?.select("div.content-box")?.mapNotNull {
                it.toSearchResponse()
            }
        val synopsis = doc.select("div.post-story:contains(قصة) p").text()

        val rating = doc.select("div.imdbR div span").text().toRatingInt()
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
                this.tags = tags
                this.recommendations = recommendations
                this.rating = rating
            }
        } else {
            val episodes = ArrayList<Episode>()
            val episodeElement = doc.select("div.MediaGrid")
            val allEpisodesUrl = doc.select("div.btns:contains(جميع الحلقات) a").attr("href")
            if(allEpisodesUrl.isNotEmpty()) {
                app.get(allEpisodesUrl).document.select("div.row > div").let {
                    it.mapIndexedNotNull { index, element ->
                        episodes.add(
                            Episode(
                                element.select("a.fullClick").attr("href"),
                                element.select("a.fullClick").attr("title"),
                                1,
                                it.size - index
                            )
                        )
                    }
                }
            } else {
                episodeElement[1].select("div.content-box").apmap {
                    val seasonNumber = it.select("div.number em").text().toIntOrNull()
                    val seasonUrl = it.select("a.fullClick").attr("href")
                    app.get(seasonUrl).document.select(".episode-block").map { episode ->
                        episodes.add(
                            Episode(
                                episode.select("a").attr("href"),
                                episode.select("div.title").text(),
                                seasonNumber,
                                episode.select("div.number em").text().toIntOrNull(),
                                episode.select("div.poster img").attr("data-image")
                            )
                        )
                    }
                }
            }
            newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodes.distinct().sortedBy { it.episode }) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.tags = tags
                this.recommendations = recommendations
                this.rating = rating
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val watchUrl = "$data/watch"
		var doc = app.get(watchUrl).document
		if(doc.select("title").text() == "Just a moment...") {
			doc = app.get(watchUrl, interceptor = cfKiller, timeout = 120).document
		}
		doc.select(
            ".servers-list li:contains(ok), li:contains(Streamtape), li:contains(DoodStream), li:contains(Uqload), li:contains(Voe), li:contains(VIDBOM), li:contains(Upstream), li:contains(السيرفر الخاص), li:contains(GoStream), li:contains(الخاص 1080p), li:contains(vidbom), li:contains(Vidbom)"
        ).apmap {
            val id = it.attr("data-id")
            val i = it.attr("data-i")
            val sourceUrl = app.post(
                "${data.getDomainFromUrl()}/wp-content/themes/Shahid4u-WP_HOME/Ajaxat/Single/Server.php",
                headers = mapOf("referer" to watchUrl, "x-requested-with" to "XMLHttpRequest"),
                data = mapOf("id" to id, "i" to i)
            ).document.select("iframe").attr("src").replace(" ", "")
            loadExtractor(sourceUrl, watchUrl, subtitleCallback, callback)
        }
        return true
    }
}