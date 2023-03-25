package com.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.Qualities


open class Govad : ExtractorApi() {
    override val name = "Govad"
    override val mainUrl = "https://govad.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        val regcode = """$mainUrl/embed-(\w+)""".toRegex()
        val code = regcode.find(url)?.groupValues?.getOrNull(1)
        val link = "$mainUrl/$code"
        with(app.get(link).document) {
            val data = this.select("script").mapNotNull { script ->
                if (script.data().contains("sources: [")) {
                    script.data().substringAfter("sources: [")
                        .substringBefore("],").replace("file", "\"file\"").replace("label", "\"label\"").replace("type", "\"type\"")
                } else {
                    null
                }
            }

            tryParseJson<List<ResponseSource>>("$data")?.map {
                sources.add(
                    ExtractorLink(
                        name,
                        name,
                        it.file,
                        referer = url,
                        quality = getQualityFromName(it.label) ?: Qualities.Unknown.value,
                        isM3u8 = if(it.file.endsWith(".m3u8")) true else false
                        )
                    )
            }
        }
        return sources
    }

    private data class ResponseSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("label") val label: String?
    )
}
