package com.akwam


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
class AkwamBC : Akwam() {
    override var name = "Akwam (Blocked Countries)"
    override var mainUrl = "https://eg1.xn--mgba7fjn.fun"
}
open class Akwam : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://akwam.to"
    override var name = "Akwam"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime, TvType.Cartoon)

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("a.box").attr("href") ?: return null
        if (url.contains("/games/") || url.contains("/programs/")) return null
        val poster = select("picture > img")
        val title = poster.attr("alt")
        val posterUrl = poster.attr("data-src")
        val year = select(".badge-secondary").text().toIntOrNull()

        // If you need to differentiate use the url.
        return MovieSearchResponse(
            title,
            url,
            this@Akwam.name,
            TvType.TvSeries,
            posterUrl,
            year,
            null,
        )
    }
    override val mainPage = mainPageOf(
            "$mainUrl/movies?page=" to "Movies",
            "$mainUrl/series?page=" to "Series",
            "$mainUrl/shows?page=" to "Shows"
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select("div.col-lg-auto.col-md-4.col-6.mb-12").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val doc = app.get(url).document
        return doc.select("div.col-lg-auto").mapNotNull {
            it.toSearchResponse()
        }
    }

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toEpisode(): Episode {
        val a = select("a.text-white")
        val url = a.attr("href")
        val title = a.text()
        val thumbUrl = select("picture > img").attr("src")
        val date = select("p.entry-date").text()
        return newEpisode(url) {
            name = title
            episode = title.getIntFromText()
            posterUrl = thumbUrl
            addDate(date)
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = doc.select("#downloads > h2 > span").isNotEmpty()//url.contains("/movie/")
        val title = doc.select("h1.entry-title").text()
        val posterUrl = doc.select("picture > img").attr("src")

        val year =
            doc.select("div.font-size-16.text-white.mt-2").firstOrNull {
                it.text().contains("السنة")
            }?.text()?.getIntFromText()

        // A bit iffy to parse twice like this, but it'll do.
        val duration =
            doc.select("div.font-size-16.text-white.mt-2").firstOrNull {
                it.text().contains("مدة الفيلم")
            }?.text()?.getIntFromText()

        val synopsis = doc.select("div.widget-body p:first-child").text()

        val rating = doc.select("span.mx-2").text().split("/").lastOrNull()?.toRatingInt()

        val tags = doc.select("div.font-size-16.d-flex.align-items-center.mt-3 > a").map {
            it.text()
        }

        val actors = doc.select("div.widget-body > div > div.entry-box > a").mapNotNull {
            val name = it?.selectFirst("div > .entry-title")?.text() ?: return@mapNotNull null
            val image = it.selectFirst("div > img")?.attr("src") ?: return@mapNotNull null
            Actor(name, image)
        }
        


        val recommendations =
            doc.select("div > div.widget-body > div.row > div > div.entry-box").mapNotNull {
                val recTitle = it?.selectFirst("div.entry-body > .entry-title > .text-white")
                    ?: return@mapNotNull null
                val href = recTitle.attr("href") ?: return@mapNotNull null
                val name = recTitle.text() ?: return@mapNotNull null
                val poster = it.selectFirst(".entry-image > a > picture > img")?.attr("data-src")
                    ?: return@mapNotNull null
                MovieSearchResponse(name, href, this.name, TvType.Movie, fixUrl(poster))
            }

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
                this.rating = rating
                this.tags = tags
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
            }
        } else {
            val episodes = doc.select("div.bg-primary2.p-4.col-lg-4.col-md-6.col-12").map {
                it.toEpisode()
            }.let {
                val isReversed = (it.lastOrNull()?.episode ?: 1) < (it.firstOrNull()?.episode ?: 0)
                if (isReversed)
                    it.reversed()
                else it
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.duration = duration
                this.posterUrl = posterUrl
                this.tags = tags.filterNotNull()
                this.rating = rating
                this.year = year
                this.plot = synopsis
                this.recommendations = recommendations
                addActors(actors)
            }
        }
    }


//    // Maybe possible to not use the url shortener but cba investigating that.
//    private suspend fun skipUrlShortener(url: String): AppResponse {
//        return app.get(app.get(url).document.select("a.download-link").attr("href"))
//    }

    private fun getQualityFromId(id: Int?): Qualities {
        return when (id) {
            2 -> Qualities.P360 // Extrapolated
            3 -> Qualities.P480
            4 -> Qualities.P720
            5 -> Qualities.P1080
            else -> Qualities.Unknown
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        val links = doc.select("div.tab-content.quality").map { element ->
            val quality = getQualityFromId(element.attr("id").getIntFromText())
            element.select(".col-lg-6 > a:contains(تحميل)").map { linkElement ->
                if (linkElement.attr("href").contains("/download/")) {
                    Pair(
                        linkElement.attr("href"),
                        quality,
                    )
                } else {
                    val url = "$mainUrl/download${
                        linkElement.attr("href").split("/link")[1]
                    }${data.split("/movie|/episode|/shows|/show/episode".toRegex())[1]}"
                    Pair(
                        url,
                        quality,
                    )
                    // just in case if they add the shorts urls again
                }
            }
        }.flatten()

        links.map {
            val linkDoc = app.get(it.first).document
            val button = linkDoc.select("div.btn-loader > a")
            val url = button.attr("href")

            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    url,
                    this.mainUrl,
                    it.second.value
                )
            )
        }
        return true
    }
}
