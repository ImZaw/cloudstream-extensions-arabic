package com.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Qualities

open class Vidmoly : ExtractorApi() {
    override val name = "Vidmoly"
    override val mainUrl = "https://vidmoly.to"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val doc = app.get(url).document
        val m3u8 = doc.select("body > script").map { it.data() }.first { it.contains("sources") }.substringAfter("sources: [{file:\"").substringBefore("\"}],")
        return mutableListOf(ExtractorLink(
            this.name,
            "Vidmoly",
            m3u8,
            referer ?: mainUrl,
            Qualities.Unknown.value,
            m3u8.contains(".m3u8")
        ))
    }
}