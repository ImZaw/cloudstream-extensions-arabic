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
    override var mainUrl = "https://movizland.cyou"
    override var name = "Movizland"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime, TvType.Cartoon)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }
    
    private fun String.getSeasonNameFromUrl(): String? {
        return Regex("""\/series\/(.+)\/""").find(this)?.groupValues?.getOrNull(1)
    }
    
    private fun String.getDomainFromUrl(): String? {
        return Regex("""^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n\?\=]+)""").find(this)?.groupValues?.firstOrNull()
    }
    
    private fun String.cleanTitle(): String {
		val prefix = setOf("مشاهدة فيلم","مشاهدة وتحميل فيلم","فيلم","انمي","إنمي","مسلسل","برنامج")
		val suffix = setOf("مدبلج للعربية","اون لاين","مترجم")
		this.let{ clean ->
            var aa = clean
				prefix.forEach{ pre ->
            	aa = if (aa.startsWith(pre))	aa.replace(pre,"") 	else	aa	}
                var bb = aa
				suffix.mapNotNull{ suf ->
            	bb = if (bb.endsWith(suf))	bb.replace(suf,"")	else	bb	}
        	return bb
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select(".BlockItem")
        val title = url.select(".BlockTitle").text().cleanTitle()
        val posterUrl = if (url.select(".BlockTitle").text().contains("فيلم")) {select(".BlockImageItem img")?.attr("src")} else {select(".BlockImageItem > img:nth-child(3)")?.attr("src")} 
        val year = select(".InfoEndBlock li").last()?.text()?.getIntFromText()
        var quality = select(".RestInformation li").last()?.text()?.replace(" |-|1080p|720p".toRegex(), "")
            ?.replace("WEB DL","WEBDL")?.replace("BluRay","BLURAY")
        return MovieSearchResponse(
            title,
            url.select("a").attr("href"),
            this@Movizland.name,
            TvType.TvSeries,
            posterUrl,
            year,
            null,
            quality = getQualityFromString(quality),
            // rating = url.select("div.StarsIMDB").text()?.getIntFromText()?.toDouble()
        )
    }
    override val mainPage = mainPageOf(
        "$mainUrl/category/movies/page/" to "Movies",
        "$mainUrl/series/page/" to "Series",
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select("div.BlockItem").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ".toRegex(), "%20")
        val result = arrayListOf<SearchResponse>()
        listOf(
            "$mainUrl/category/movies/?s=$q",
            "$mainUrl/series/?searching=$q"
        ).apmap { url ->
            val d = app.get(url).document
            d.select("div.BlockItem").mapNotNull {
                //if (!it.text().contains("فيلم||موسم")) return@mapNotNull null
                it.toSearchResponse()?.let { it1 -> result.add(it1) }
            }
        }
        return result.distinct().sortedBy { it.name }
    }
    
    /*
    private fun Element.toEpisode(): Episode {
        val a = select("div.EpisodeItem")
        val url = a.select("a")?.attr("href")
        val title = a.text()
        //val thumbUrl = a.select("div.BlockImageItem img")?.attr("src")
        val Epsnum = a.select("em").text()
        return newEpisode(url) {
            name = title
            episode = Epsnum.getIntFromText()
            //posterUrl = thumbUrl
        }
    }*/
    
    
    private fun getSeasonFromString(tit: String): Int {   
            if(tit.contains("الموسم الاول".toRegex())){ return 1 }
            else if(tit.contains("الموسم الحادي عشر".toRegex())){ return 11 }
            else if(tit.contains("الموسم الثاني عشر".toRegex())){return 12}
            else if(tit.contains("الموسم الثالث عشر".toRegex())){return 13}
            else if(tit.contains("الموسم الرابع عشر".toRegex())){return 14}
            else if(tit.contains("الموسم الخامس عشر".toRegex())){return 15}
            else if(tit.contains("الموسم السادس عشر".toRegex())){return 16}
            else if(tit.contains("الموسم السابع عشر".toRegex())){return 17}
            else if(tit.contains("الموسم الثامن عشر".toRegex())){return 18}
            else if(tit.contains("الموسم التاسع عشر".toRegex())){return 19}
            else if(tit.contains("الموسم الثاني".toRegex())){ return 2 }
            else if(tit.contains("الموسم الثالث".toRegex())){ return 3 }
            else if(tit.contains("الموسم الرابع".toRegex())){ return 4 }
            else if(tit.contains("الموسم الخامس".toRegex())){ return 5 }
            else if(tit.contains("الموسم السادس".toRegex())){ return 6 }
            else if(tit.contains("الموسم السابع".toRegex())){ return 7 }
            else if(tit.contains("الموسم الثامن".toRegex())){ return 8 }
            else if(tit.contains("الموسم التاسع".toRegex())){ return 9 }
            else if(tit.contains("الموسم العاشر".toRegex())){ return 10 }
            else if(tit.contains("الموسم العشرون".toRegex())){return 20}     
            else {  return 0    }
    }
    

    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
        val sdetails = doc.select(".SingleDetails")
        val posterUrl = sdetails.select("img")?.attr("data-src")
        val year = sdetails.select("li:has(.fa-clock) a").text()?.getIntFromText()
        val title = doc.select("h2.postTitle").text().cleanTitle()
        val isMovie = if(doc.select("h2.postTitle").text().contains("عرض|فيلم".toRegex())) true else false
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
                    seas.select("a").attr("href") }.apmap{
                    val Sedoc = app.get(it).document
                    val pagEl = Sedoc.select(".pagination > div > ul > li.active > a").isNotEmpty()
                    val pagSt = if(pagEl) true else false
                        if(pagSt){
                            Sedoc.select(".pagination > div > ul > li:nth-child(n):not(:last-child) a").map{ eppages ->
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
