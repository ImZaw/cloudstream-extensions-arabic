package com.arabseed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class ArabSeed : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://e.arabseed.ink"
    override var name = "ArabSeed"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }
    

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = select("h4").text()
        val posterUrl = select("img.imgOptimzer").attr("data-image").ifEmpty { select("div.Poster img").attr("data-src") }
        val tvType = if (select("span.category").text().contains("مسلسلات")) TvType.TvSeries else TvType.Movie
        return MovieSearchResponse(
            title,
            select("a").attr("href"),
            this@ArabSeed.name,
            tvType,
            posterUrl,
            )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/movies/?offset=" to "Movies",
        "$mainUrl/series/?offset=" to "Series",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page, timeout = 120).document
        val home = document.select("ul.Blocks-UL > div").mapNotNull {
            it.toSearchResponse()
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val list = arrayListOf<SearchResponse>()
        arrayListOf(
            mainUrl to "series",
            mainUrl to "movies"
        ).apmap { (url, type) ->
            val doc = app.post(
                "$url/wp-content/themes/Elshaikh2021/Ajaxat/SearchingTwo.php",
                data = mapOf("search" to query, "type" to type),
                referer = mainUrl
            ).document
            doc.select("ul.Blocks-UL > div").mapNotNull {
                it.toSearchResponse()?.let { it1 -> list.add(it1) }
            }
        }
        return list
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val title = doc.select("h1.Title").text().ifEmpty { doc.select("div.Title").text() }
        val isMovie = title.contains("فيلم")

        val posterUrl = doc.select("div.Poster > img").let{ it.attr("data-src").ifEmpty { it.attr("src") } }
        val rating = doc.select("div.RatingImdb em").text().getIntFromText()
        val synopsis = doc.select("p.descrip").last()?.text()
        val year = doc.select("li:contains(السنه) a").text().getIntFromText()
        val tags = doc.select("li:contains(النوع) > a, li:contains(التصنيف) > a")?.map { it.text() }

        val actors = doc.select("div.WorkTeamIteM").mapNotNull {
            val name = it.selectFirst("h4 > em")?.text() ?: return@mapNotNull null
            val image = it.selectFirst("div.Icon img")?.attr("src") ?: return@mapNotNull null
            val roleString = it.select("h4 > span").text()
            val mainActor = Actor(name, image)
            ActorData(actor = mainActor, roleString = roleString)
        }

        val recommendations = doc.select("ul.Blocks-UL > div").mapNotNull { element ->
            element.toSearchResponse()
        }

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
                this.actors = actors
                this.rating = rating
                this.year = year
            }
        } else {
            val seasonList = doc.select("div.SeasonsListHolder ul > li")
            val episodes = arrayListOf<Episode>()
            if(seasonList.isNotEmpty()) {
                seasonList.apmap { season ->
                    app.post(
                        "$mainUrl/wp-content/themes/Elshaikh2021/Ajaxat/Single/Episodes.php",
                        data = mapOf("season" to season.attr("data-season"), "post_id" to season.attr("data-id"))
                    ).document.select("a").apmap {
                        episodes.add(Episode(
                            it.attr("href"),
                            it.text(),
                            season.attr("data-season")[0].toString().toIntOrNull(),
                            it.text().getIntFromText()
                        ))
                    }
                }
            } else {
                doc.select("div.ContainerEpisodesList > a").apmap {
                    episodes.add(Episode(
                        it.attr("href"),
                        it.text(),
                        0,
                        it.text().getIntFromText()
                    ))
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().sortedBy { it.episode }) {
                this.posterUrl = posterUrl
                this.tags = tags
                this.plot = synopsis
                this.actors = actors
                this.recommendations = recommendations
                this.rating = rating
                this.year = year
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
        val watchUrl = doc.select("a.watchBTn").attr("href")
        val watchDoc = app.get(watchUrl, headers = mapOf("Referer" to mainUrl)).document
        val indexOperators = arrayListOf<Int>()
        val list: List<Element> = watchDoc.select("ul > li[data-link], ul > h3").mapIndexed { index, element ->
            if(element.`is`("h3")) {
                indexOperators.add(index)
                element
            } else element
        }
        var watchLinks: List<Pair<Int, List<Element>>>;
        if(indexOperators.isNotEmpty()) {
            watchLinks = indexOperators.mapIndexed { index, it ->
                var endIndex = list.size
                if (index != indexOperators.size - 1) endIndex = (indexOperators[index + 1]) - 1
                list[it].text().getIntFromText() as Int to list.subList(it + 1, endIndex) as List<Element>
            }
        } else {
            watchLinks = arrayListOf(0 to list)
        }
        watchLinks.apmap { (quality, links) ->
            links.apmap {
                val iframeUrl = it.attr("data-link")
                println(iframeUrl)
                if(it.text().contains("عرب سيد")) {
                    val sourceElement = app.get(iframeUrl).document.select("source")
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            "ArabSeed",
                            sourceElement.attr("src"),
                            data,
                            if(quality != 0) quality else it.text().replace(".*- ".toRegex(), "").replace("\\D".toRegex(),"").toInt(),
                            !sourceElement.attr("type").contains("mp4")
                        )
                    )
                } else loadExtractor(iframeUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }
}