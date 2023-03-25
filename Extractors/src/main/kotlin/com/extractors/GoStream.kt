package com.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app

open class GoStream : ExtractorApi() {
    override val name = "GoStream"
    override val mainUrl = "https://gostream.pro"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
          val sources = mutableListOf<ExtractorLink>()
          val text = app.get(url).document.select("#player_code > script:nth-child(4)").html() ?: ""
          val a = text.split("|")
          val b = a[0].substring(a[0].lastIndexOf("http"))
          val link = "$b://${a[5]}.${a[4]}-${a[3]}.${a[2]}:${a[11]}/d/${a[10]}/${a[9]}.${a[8]}"
          if (link.isNotBlank()) {
          sources.add(
                        ExtractorLink(
                            name = name,
                            source = name,
                            url = link,
                            isM3u8 = false,
                            quality = Qualities.Unknown.value,
                            referer = "$mainUrl/"
                        )
                    )
          }
       return sources
    }
}
