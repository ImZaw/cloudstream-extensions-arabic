package com.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app

open class VoeSx : ExtractorApi() {
    override val name = "VoeSx"
    override val mainUrl = "https://voe.sx"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val doc = app.get(url).document
        val script = doc.select("script").map { it.data() }.first { it.contains("sources") }
        val m3u8 = script.substringAfter("'hls': '").substringBefore("'")
        val mp4 = script.substringAfter("'mp4': '").substringBefore("'")
        val quality = script.substringAfter("'video_height': ").substringBefore(",").toInt()
        return mutableListOf(ExtractorLink(
            this.name,
            "Voe.sx m3u8",
            m3u8,
            referer ?: mainUrl,
            quality,
            true
        ), ExtractorLink(
            this.name,
            "Voe.sx mp4",
            mp4,
            referer ?: mainUrl,
            quality,
        ))
        }
}