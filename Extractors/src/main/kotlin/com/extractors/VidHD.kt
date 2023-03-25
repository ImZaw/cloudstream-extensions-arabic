package com.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app

open class VidHD : ExtractorApi() {
    override val name = "VidHD"
    override val mainUrl = "https://vidhd.fun"
    override val requiresReferer = false
    
    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
          val sources = mutableListOf<ExtractorLink>()
          app.get(url).document.select("body > script:nth-child(2)").html().substringAfter("||||").let{ c->
          val a = c.split("|")
          val b = c.substringAfter("|image|").split("|")
          val f = c.substringAfter("|label|").substringBefore("|file|")
          val e = "${a[6]}://${a[21]}.e-${a[20]}-${a[19]}.${a[18]}/${b[1]}/v.$f"
          val d = e.replace(b[1],b[3])
          val links: MutableMap<String, Int?> = mutableMapOf(
              e to b[0].getIntFromText(),
              d to b[2].getIntFromText(),
          )
          links.forEach { (watchlink, quality) ->
          if(watchlink.isNotBlank()){
              sources.add(
                        ExtractorLink(
                            name = name,
                            source = name,
                            url = watchlink,
                            isM3u8 = false,
                            quality = quality ?: Qualities.Unknown.value,
                            referer = "$mainUrl/"
                            )
                        )
                    }
                 }     
              }
             return sources
            }
           }
