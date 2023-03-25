package com.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI

open class LinkBox : ExtractorApi() {
    override val name = "LinkBox"
    override val mainUrl = "https://www.linkbox.to"
    override val requiresReferer = false

    data class LinkBox (
        @JsonProperty("data"   ) var data   : Data? = Data(),
    )
    data class Data (
        @JsonProperty("itemInfo"  ) var itemInfo  : ItemInfo? = ItemInfo(),
    )
    data class ItemInfo (
        @JsonProperty("resolutionList" ) var resolutionList : ArrayList<ResolutionList> = arrayListOf(),
    )
    data class ResolutionList (

        @JsonProperty("resolution" ) var resolution : String? = null,
        @JsonProperty("size"       ) var size       : Double?    = null,
        @JsonProperty("sub_type"   ) var subType    : String? = null,
        @JsonProperty("url"        ) var url        : String? = null,

        )
    private fun bytesToHumanReadableSize(bytes: Double) = when {
        bytes >= 1 shl 30 -> "%.1f GB".format(bytes / (1 shl 30))
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes / (1 shl 20))
        bytes >= 1 shl 10 -> "%.0f kB".format(bytes / (1 shl 10))
        else -> "$bytes bytes"
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        println("LinkBox extractor started")
        val sources = mutableListOf<ExtractorLink>()
        val apiUrl = "https://" + URI(url).host + "/api/file/detail?itemId=" + url.substringAfter("/file/")
        val json = app.get(apiUrl).parsed<LinkBox>()
        json.data?.itemInfo?.resolutionList?.forEach {
            sources.add(ExtractorLink(
                    this.name,
                    "LinkBox " + bytesToHumanReadableSize(it.size ?: 0.0),
                    it.url ?: return@forEach,
                    mainUrl,
                    it.resolution?.replace("p","")?.toInt() ?: Qualities.Unknown.value,
                    false
            ))
        }
        println(sources)
        return sources
    }
}