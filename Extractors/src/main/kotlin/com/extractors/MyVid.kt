package com.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app

open class MyVid : ExtractorApi() {
    override val name = "MyVid"
    override val mainUrl = "https://myviid.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
          val sources = mutableListOf<ExtractorLink>()
          val text = app.get(url).document.select("body > script:nth-child(2)").html() ?: ""
          val a = text.substringAfter("||||").substringBefore("'.split").split("|")
          val link = "${a[7]}://${a[24]}.${a[6]}.${a[5]}/${a[83]}/v.${a[82]}"
          if (link.isNotBlank()) {
          sources.add(
                        ExtractorLink(
                            name = name,
                            source = name,
                            url = link,
                            isM3u8 = false,
                            quality = "${a[80]}".replace("p","").toInt() ?: Qualities.Unknown.value,
                            referer = "$mainUrl/"
                        )
                    )
          }
       return sources
    }
}
