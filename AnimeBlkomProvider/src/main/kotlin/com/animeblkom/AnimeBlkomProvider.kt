package com.animeblkom


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class AnimeBlkom : MainAPI() {
    override var mainUrl = "https://animeblkom.net"
    override var name = "AnimeBlkom"
    override var lang = "ar"
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
    )
	
    private fun Element.toSearchResponse(): SearchResponse {
        val url = select("div.poster a").attr("href")
        val name = select("div.name a").text()
        val poster = mainUrl + select("div.poster img").attr("data-original")
        val year = select("div[title=\"سنة الانتاج\"]").text().toIntOrNull()
        val episodesNumber = select("div[title=\"عدد الحلقات\"]").text().toIntOrNull()
        val tvType = select("div[title=\"النوع\"]").text().let { if(it.contains("فيلم|خاصة".toRegex())) TvType.AnimeMovie else if(it.contains("أوفا|أونا".toRegex())) TvType.OVA else TvType.Anime }
        return newAnimeSearchResponse(
            name,
            url,
            tvType,
        ) {
            addDubStatus(false, episodesNumber)
            this.year = year
            this.posterUrl = poster
        }
    }
    override val mainPage = mainPageOf(
        "$mainUrl/anime-list?sort_by=rate&page=" to "Most rated",
        "$mainUrl/anime-list?sort_by=created_at&page=" to "Recently added",
        "$mainUrl/anime-list?states=finished&page=" to "Completed"
    )
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select("div.content-inner")
            .mapNotNull { element ->
                element.toSearchResponse()
            }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ","+")
        return app.get("$mainUrl/search?query=$q").document.select("div.content.ratable").map {
            it.toSearchResponse()
        }
    }
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.select("span h1").text().replace("\\(.*".toRegex(),"")
        val poster = mainUrl + doc.select("div.poster img").attr("data-original")
        val description = doc.select(".story p").text()
        val genre = doc.select("p.genres a").map {
            it.text()
        }
        val year = doc.select(".info-table div:contains(تاريخ الانتاج) span.info").text().split("-")[0].toIntOrNull()
        val status = doc.select(".info-table div:contains(حالة الأنمي) span.info").text().let { if(it.contains("مستمر")) ShowStatus.Ongoing else ShowStatus.Completed }
        val nativeName = doc.select("span[title=\"الاسم باليابانية\"]").text().replace(".*:".toRegex(),"")
        val type = doc.select("h1 small").text().let {
            if (it.contains("movie")) TvType.AnimeMovie
            if (it.contains("ova|ona".toRegex())) TvType.OVA
            else TvType.Anime
        }

        val malId = doc.select("a.blue.cta:contains(المزيد من المعلومات)").attr("href").replace(".*e\\/|\\/.*".toRegex(),"").toInt()
        val episodes = arrayListOf<Episode>()
        val episodeElements = doc.select(".episode-link")
        if(episodeElements.isEmpty()) {
            episodes.add(Episode(
                url,
                "Watch",
            ))
        } else {
            episodeElements.map {
                val a = it.select("a")
                episodes.add(Episode(
                    mainUrl + a.attr("href"),
                    a.text().replace(":"," "),
                    episode = a.select("span").not(".pull-left").last()?.text()?.toIntOrNull()
                ))
            }
        }
        return newAnimeLoadResponse(title, url, type) {
            addMalId(malId)
            japName = nativeName
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes) // TODO CHECK
            plot = description
            tags = genre

            showStatus = status
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select(".panel .panel-body a").forEach {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    it.attr("title") + " " + it.select("small").text(),
                    it.attr("href"),
                    this.mainUrl,
                    it.text().replace("p.*| ".toRegex(),"").toInt(),
                )
            )
        }
        return true
    }
}