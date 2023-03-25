package com.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

open class Moshahda : ExtractorApi() {
    override val name = "Moshahda"
    override val mainUrl = "https://moshahda.net"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        val regcode = """$mainUrl/embed-(\w+)""".toRegex()
        val code = regcode.find(url)?.groupValues?.getOrNull(1)
        val link = "$mainUrl/$code.html?"
        if (code?.length == 12) {
        mutableMapOf(
        "download_l" to 240,
        "download_n" to 360,
        "download_h" to 480,
        "download_x" to 720,
        "download_o" to 1080,
        ).forEach{ (key,qual) ->         
                    sources.add(
                        ExtractorLink(
                            name = name,
                            source = name,
                            url = "${link}${key}",
                            isM3u8 = false,
                            quality = qual ?: Qualities.Unknown.value,
                            referer = "$mainUrl/"
                        )
                    )
                  }
            }
            return sources            
        }
    }