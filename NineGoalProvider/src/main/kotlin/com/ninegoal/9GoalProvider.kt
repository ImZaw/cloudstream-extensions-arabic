package com.ninegoal

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.Qualities
import java.util.*

data class Data (
    @JsonProperty("id"           ) var id           : String?     = null,
    @JsonProperty("name"         ) var name         : String?     = null,
    @JsonProperty("slug"         ) var slug         : String?     = null,
    @JsonProperty("home"         ) var home         : Home?       = Home(),
    @JsonProperty("away"         ) var away         : Away?       = Away(),
    @JsonProperty("scores"       ) var scores       : Scores?     = Scores(),
    @JsonProperty("is_live"      ) var isLive       : Boolean?    = null
)
data class Home (
    @JsonProperty("model_id" ) var modelId : String? = null,
    @JsonProperty("name"     ) var name    : String? = null,
    @JsonProperty("slug"     ) var slug    : String? = null,
    @JsonProperty("logo"     ) var logo    : String? = null
)
data class Away (
    @JsonProperty("model_id" ) var modelId : String? = null,
    @JsonProperty("name"     ) var name    : String? = null,
    @JsonProperty("slug"     ) var slug    : String? = null,
    @JsonProperty("logo"     ) var logo    : String? = null
)
data class Scores (
    @JsonProperty("home" ) var home : Int? = null,
    @JsonProperty("away" ) var away : Int? = null
)
data class matchesJSON (
    @JsonProperty("data" ) var data : ArrayList<Data> = arrayListOf()
)
data class oneMatch (
    @JsonProperty("data" ) var data : Data? = Data()
)


data class PlayUrls (
    @JsonProperty("name" ) var name : String? = null,
    @JsonProperty("cdn"  ) var cdn  : String? = null,
    @JsonProperty("slug" ) var slug : String? = null,
    @JsonProperty("url"  ) var url  : String? = null,
    @JsonProperty("role" ) var role : String? = null
)
data class sourceData (
    @JsonProperty("id"          ) var id         : String?             = null,
    @JsonProperty("name"        ) var name       : String?             = null,
    @JsonProperty("slug"        ) var slug       : String?             = null,
    @JsonProperty("has_lineup"  ) var hasLineup  : Boolean?            = null,
    @JsonProperty("has_tracker" ) var hasTracker : Boolean?            = null,
    @JsonProperty("play_urls"   ) var playUrls   : ArrayList<PlayUrls> = arrayListOf()
)
data class sourcesJSON (
    @JsonProperty("data" ) var data : sourceData? = sourceData()
)
class NineGoal : MainAPI() {
    override var mainUrl = "https://9goaltv.to"
    override var name = "9Goal"
    override var lang = "en"
    override val hasDownloadSupport = false
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Live
    )

    private fun String.getDomainFromUrl(): String? {
        return Regex("""^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n\?\=]+)""").find(this)?.groupValues?.firstOrNull()
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val apiUrl = doc.select("head > script").first()?.html()?.substringAfter("window.api_base_url = \"")?.substringBefore("\";")
        val matchesData = parseJson<matchesJSON>(app.get("$apiUrl/v1/match/featured").text)
        val liveHomePageList = matchesData.data.filter { it.isLive == true }.map {
            LiveSearchResponse(
                it.name.toString(),
                apiUrl + "/v1/match/" + it.id,
                this@NineGoal.name,
                TvType.Live,
                "https://img.zr5.repl.co/vs?title=${it.name}&home=${it.home?.logo}&away=${it.away?.logo}",
            )
        }
        val featuredHomePageList = matchesData.data.filter { it.isLive == false }.map {
            LiveSearchResponse(
                it.name.toString(),
                apiUrl + "/v1/match/" + it.id,
                this@NineGoal.name,
                TvType.Live,
                "https://img.zr5.repl.co/vs?title=${it.name}&home=${it.home?.logo}&away=${it.away?.logo}",
            )
        }
        return HomePageResponse(
            arrayListOf(
                HomePageList("Live", liveHomePageList, isHorizontalImages = true),
                HomePageList("Featured", featuredHomePageList, isHorizontalImages = true)
            )
        )
    }

    override suspend fun load(url: String): LoadResponse {
        val json = parseJson<oneMatch>(app.get(url).text).data
        return LiveStreamLoadResponse(
            json?.name.toString(),
            url,
            this.name,
            "$url/stream",
        )
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val sourcesData = parseJson<sourcesJSON>(app.get(data).text).data
        sourcesData?.playUrls?.apmap {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    "${this.name} ${it.name} - ${sourcesData.name}",
                    it.url.toString(),
                    fixUrl(it.url?.getDomainFromUrl().toString()),
                    Qualities.Unknown.value,
                    isM3u8 = true,
                )
            )
        }
        return true
    }
}
