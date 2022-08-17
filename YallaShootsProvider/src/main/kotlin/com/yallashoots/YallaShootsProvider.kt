package com.yallashoots


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class YallaShoots : MainAPI() {
    override var mainUrl = "https://www.yalla-shoots.com"
    override var name = "Yalla Shoots"
    override var lang = "ar"
    override val hasDownloadSupport = false
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Live
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val dataMap = mapOf(
            "Matches Today" to "$mainUrl",
        )
        return HomePageResponse(dataMap.apmap { (title, data) ->
            val document = app.get(data).document
            val shows = document.select("div.albaflex > div.live").mapNotNull {
                if(it.select("a").attr("href") === "$mainUrl/#/") return@mapNotNull null
                val linkElement = it.select("a")
                LiveSearchResponse(
                    linkElement.attr("title"),
                    linkElement.attr("href"),
                    this@YallaShoots.name,
                    TvType.Live,
                    document.select(".blog-post:contains(${linkElement.attr("title").replace("Vs ","و")}) img").attr("src"),
                    lang = "ar"
                )
            }
            HomePageList(
                title,
                shows.ifEmpty {
                              arrayListOf(LiveSearchResponse(
                                  "لا يوجد اي مباراة حاليا",
                                  mainUrl,
                                  this@YallaShoots.name,
                                  TvType.Live,
                                  "$mainUrl/wp-content/uploads/2021/12/يلا-شوت-1.png",
                                  lang = "ar"
                              ))
                },
                isHorizontalImages = true
            )
        })
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("h1").text()
        val poster = fixUrl(doc.select("img.img-responsive").attr("src"))
        return LiveStreamLoadResponse(
            title,
            url,
            this.name,
            doc.select("iframe[loading=\"lazy\"]").attr("src"),
            poster,
        )
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get("$data?serv=2").document
        val sourceLink = doc.select("script:contains(.m3u8)").html().replace(".*hls: \"|\"};.*".toRegex(),"")
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                sourceLink,
                mainUrl,
                Qualities.Unknown.value,
                isM3u8 = true
            )
        )
        return true
    }
}
