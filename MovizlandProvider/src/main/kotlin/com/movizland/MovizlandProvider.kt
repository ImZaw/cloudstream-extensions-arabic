package com.movizland

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Movizland : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://movizland.online"
    override var name = "Movizland"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.AsianDrama, TvType.Anime)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }
    
    private fun String.cleanTitle(): String {
		val prefix = setOf("مشاهدة فيلم","مشاهدة وتحميل فيلم","تحميل","فيلم","انمي","إنمي","مسلسل","برنامج")
		val suffix = setOf("مدبلج للعربية","اون لاين","مترجم")
		this.let{ clean ->
            var aa = clean
				prefix.forEach{ pre ->
            	aa = if (aa.contains(pre))	aa.replace(pre,"") 	else	aa	}
                var bb = aa
				suffix.mapNotNull{ suf ->
            	bb = if (bb.contains(suf))	bb.replace(suf,"")	else	bb	}
        	return bb
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select(".BlockItem")
        val title = url.select(".BlockTitle").text().cleanTitle()
	val img = url.select("img:last-of-type")
	val posterUrl = img?.attr("src")?.ifEmpty { img?.attr("data-src") }
        val year = select(".InfoEndBlock li").last()?.text()?.getIntFromText()
        var quality = select(".RestInformation li").last()?.text()?.replace(" |-|1080p|720p".toRegex(), "")
            ?.replace("WEB DL","WEBDL")?.replace("BluRay","BLURAY")
        return MovieSearchResponse(
            title.replace("$year",""),
            url.select("a").attr("href"),
            this@Movizland.name,
            TvType.TvSeries,
            posterUrl,
            year,
            null,
            quality = getQualityFromString(quality),
        )
    }
    
    override val mainPage = mainPageOf(
	"$mainUrl/page/" to "الحلقات و الافلام المضافة حديثا",
        "$mainUrl/category/movies/page/" to "أفلام",
        "$mainUrl/series/page/" to "مسلسلات",
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select(".BlockItem").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val d = app.get("$mainUrl/?s=$query").document
        return d.select(".BlockItem").mapNotNull {
            if(it.select(".BlockTitle").text().contains("الحلقة")) return@mapNotNull null;
            it.toSearchResponse()
        }
    }
    
    private fun getSeasonFromString(sName: String): Int {
        return when (sName.isNotEmpty()) {
            sName.contains("الموسم الاول|الموسم 1".toRegex()) -> 1
            sName.contains("الموسم الحادي عشر|الموسم 11".toRegex()) -> 11
            sName.contains("الموسم الثاني عشر|الموسم 12".toRegex()) -> 12
            sName.contains("الموسم الثالث عشر|الموسم 13".toRegex()) -> 13
            sName.contains("الموسم الرابع عشر|الموسم 14".toRegex()) -> 14
            sName.contains("الموسم الخامس عشر|الموسم 15".toRegex()) -> 15
            sName.contains("الموسم السادس عشر|الموسم 16".toRegex()) -> 16
            sName.contains("الموسم السابع عشر|الموسم 17".toRegex()) -> 17
            sName.contains("الموسم الثامن عشر|الموسم 18".toRegex()) -> 18            
            sName.contains("الموسم التاسع عشر|الموسم 19".toRegex()) -> 19
            sName.contains("الموسم الثاني|الموسم 2".toRegex()) -> 2
            sName.contains("الموسم الثالث|الموسم 3".toRegex()) -> 3
            sName.contains("الموسم الرابع|الموسم 4".toRegex()) -> 4
            sName.contains("الموسم الخامس|الموسم 5".toRegex()) -> 5
            sName.contains("الموسم السادس|الموسم 6".toRegex()) -> 6
            sName.contains("الموسم السابع|الموسم 7".toRegex()) -> 7
            sName.contains("الموسم الثامن|الموسم 8".toRegex()) -> 8
            sName.contains("الموسم التاسع|الموسم 9".toRegex()) -> 9
            sName.contains("الموسم العاشر|الموسم 10".toRegex()) -> 10
            sName.contains("الموسم العشرون|الموسم 20".toRegex()) -> 20
            else -> 1
            }
    }
    
    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
        val sdetails = doc.select(".SingleDetails")
        val posterUrl = sdetails.select(".Poster img").attr("data-src").ifEmpty {
            sdetails.select(".BlockItem").last()?.select(".Poster img")?.attr("src")
        }
        val year = sdetails.select("li:has(.fa-clock) a").text()?.getIntFromText()
        val title = doc.select("h2.postTitle").text().cleanTitle().replace("$year","")
        val isMovie = doc.select("h2.postTitle").text().contains("عرض|فيلم".toRegex())
        val synopsis = doc.select("section.story").text()
        val trailer = doc.select("div.InnerTrailer iframe").attr("data-src")
        val tags = sdetails.select("li:has(.fa-film) a").map{ it.text() }

        return if (isMovie) {
        newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.tags = tags
                this.plot = synopsis
                addTrailer(trailer)
            }
    }   else    {
            val episodes = ArrayList<Episode>()
            val pageUrl = doc.select("meta[property='og:url']").attr("content")
            val refererUrl = doc.select("body > header > div > div.Logo > a").attr("href")
            if(doc.select(".BlockItem a").attr("href").contains("/series/")){//seasons
                doc.select(".BlockItem").map { seas ->
                    seas.select("a").attr("href") }.apmap{ pageIt ->
                    val Sedoc = app.get(pageIt).document
                    val pagEl = Sedoc.select(".pagination > div > ul > li").isNotEmpty()
                    if(pagEl) {
                            Sedoc.select(".pagination > div > ul > li:nth-child(n):not(:last-child) a").apmap {
                                val epidoc = app.get(it.attr("href")).document
                                    epidoc.select("div.BlockItem").map{ element ->
                                    episodes.add(
                                        Episode(
                                            element.select("a").attr("href"),
                                            element.select(".BlockTitle").text(),
                                            getSeasonFromString(element.select(".BlockTitle").text()),
                                            element.select(".EPSNumber").text().getIntFromText(),
                                            )
                                        )
                                }
                            }
                        }else{
                        Sedoc.select(".BlockItem").map{ el ->
                        episodes.add(
                            Episode(
                                    el.select("a").attr("href"),
                                    el.select(".BlockTitle").text(),
                                    getSeasonFromString(el.select(".BlockTitle").text()),
                                    el.select(".EPSNumber").text().getIntFromText(),
                                    )
                               )
                        }
                    }
                }

                        }   else    {//episodes
                    val pagEl = doc.select(".pagination > div > ul > li.active > a").isNotEmpty()
                    val pagSt = if(pagEl) true else false
                    if(pagSt){
                        doc.select(".pagination > div > ul > li:nth-child(n):not(:last-child) a").map{ eppages ->
                            eppages.attr("href") }.apmap{
                            val epidoc = app.get(it).document
                                epidoc.select("div.BlockItem").map{ element ->
                                episodes.add(
                                    Episode(
                                        element.select("a").attr("href"),
                                        element.select(".BlockTitle").text(),
                                        getSeasonFromString(element.select(".BlockTitle").text()),
                                        element.select(".EPSNumber").text().getIntFromText(),
                                        )
                                    )
                            }
                        }
                    }else{   
                    doc.select("div.BlockItem").map{ el ->
                    episodes.add(
                        Episode(
                                el.select("a").attr("href"),
                                el.select(".BlockTitle").text(),
                                getSeasonFromString(el.select(".BlockTitle").text()),
                                el.select(".EPSNumber").text().getIntFromText(),
                                )
                           )
                    }
                }
            }
                                
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
              /*this.posterUrl = posterUrl
                this.year = year
                this.tags = tags
                this.plot = synopsis
                addTrailer(trailer)*/
               }
            }
        }
          
	override suspend fun loadLinks(
        	data: String,
       		 isCasting: Boolean,
        	subtitleCallback: (SubtitleFile) -> Unit,
        	callback: (ExtractorLink) -> Unit
    	): Boolean {
        	val doc = app.get(data).document
		doc.select("code[id*='Embed'] iframe").apmap {
                	var sourceUrl = it.attr("data-srcout")
                        loadExtractor(sourceUrl, data, subtitleCallback, callback)
            }
	return true
    }
}
