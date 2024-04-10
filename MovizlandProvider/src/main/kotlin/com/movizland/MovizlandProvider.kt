package com.movizland


import android.annotation.SuppressLint
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Movizland : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://movizlands.com"
    override var name = "Movizland"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.AsianDrama, TvType.Anime)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun String.getFullSize(): String? {
        return this.replace("""-\d+x\d+""".toRegex(),"")
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
        val title = url.select(".BlockTitle").text()
	val img = url.select("img:last-of-type")
        val posterUrl = img?.attr("src")?.ifEmpty { img?.attr("data-src") }
        val year = url.select(".InfoEndBlock li").last()?.text()?.getIntFromText()
        var quality = url.select(".RestInformation li").last()?.text()?.replace(" |-|1080p|720p".toRegex(), "")?.replace("BluRay","BLURAY")
	val tvtype = if(title.contains("فيلم")) TvType.Movie else TvType.TvSeries
        return MovieSearchResponse(
            title.cleanTitle(),
            url.select("a").attr("href"),
            this@Movizland.name,
            tvtype,
            posterUrl,
            year,
            null,
            quality = getQualityFromString(quality),
        )
    }
    override val mainPage = mainPageOf(
	"$mainUrl/page/" to "أضيف حديثا",
        "$mainUrl/category/movies/page/" to "أفلام",
        "$mainUrl/series/page/" to "مسلسلات"
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
	val doc = app.get(request.data + page).document   
        val list = doc.select(".BlockItem").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    @SuppressLint("SuspiciousIndentation")
    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ".toRegex(), "%20")
        val result = arrayListOf<SearchResponse>()

        val rlist = setOf(
            "$mainUrl/?s=$q",
        )
        rlist.forEach{ docs ->
		val d = app.get(docs).document
        	d.select(".BlockItem").mapNotNull {
            	it.toSearchResponse()?.let {
			it1 -> result.add(it1)
			}
        	}
        }
	return result
    }

private val seasonPatterns = arrayOf(
    Pair("الموسم العاشر|الموسم 10", 10),
    Pair("الموسم الحادي عشر|الموسم 11", 11),
    Pair("الموسم الثاني عشر|الموسم 12", 12),
    Pair("الموسم الثالث عشر|الموسم 13", 13),
    Pair("الموسم الرابع عشر|الموسم 14", 14),
    Pair("الموسم الخامس عشر|الموسم 15", 15),
    Pair("الموسم السادس عشر|الموسم 16", 16),
    Pair("الموسم السابع عشر|الموسم 17", 17),
    Pair("الموسم الثامن عشر|الموسم 18", 18),
    Pair("الموسم التاسع عشر|الموسم 19", 19),
    Pair("الموسم العشرون|الموسم 20", 20),
    Pair("الموسم الاول|الموسم 1", 1),
    Pair("الموسم الثاني|الموسم 2", 2),
    Pair("الموسم الثالث|الموسم 3", 3),
    Pair("الموسم الرابع|الموسم 4", 4),
    Pair("الموسم الخامس|الموسم 5", 5),
    Pair("الموسم السادس|الموسم 6", 6),
    Pair("الموسم السابع|الموسم 7", 7),
    Pair("الموسم الثامن|الموسم 8", 8),
    Pair("الموسم التاسع|الموسم 9", 9),
)

private fun getSeasonFromString(sName: String): Int {
    return seasonPatterns.firstOrNull{(pattern, seasonNum) -> sName.contains(pattern.toRegex()) }?.second ?: 1
}
        
    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
        val sdetails = doc.select(".SingleDetails")
        var posterUrl = sdetails.select("img")?.attr("data-src")?.getFullSize()
        val year = sdetails.select("li:has(.fa-clock) a").text()?.getIntFromText()
        var title = doc.select("h2.postTitle").text()
        val isMovie = title.contains("عرض|فيلم".toRegex())
        val synopsis = doc.select("section.story").text()
        val trailer = doc.select("div.InnerTrailer iframe").attr("data-src")
        var tags = sdetails.select("li:has(.fa-film) a").map{ it.text() }
	val recommendations = doc.select(".BlocksUI#LoadFilter .BlockItem").mapNotNull { element ->
                element.toSearchResponse()
        }


        return if (isMovie) {
        newMovieLoadResponse(
                title.cleanTitle().replace("$year",""),
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.tags = tags
                this.plot = synopsis
		this.recommendations = recommendations
		addTrailer(trailer)
            }
    }   else    {
            val episodes = ArrayList<Episode>()
	    val episodesItem = doc.select(".EpisodesList").isNotEmpty()
	    val fBlock = doc.select(".BlockItem")?.first()
	    val img = fBlock?.select("img:last-of-type")

	    if(episodesItem){
		 title = doc.select(".SeriesSingle .ButtonsFilter.WidthAuto span").text()
		 doc.select(".EpisodesList .EpisodeItem").map{ element ->
			 if(!element.text().contains("Full")){
				 episodes.add(
					 Episode(
					    element.select("a").attr("href"),
                                            null,
                                            null,
                                            element.select("em").text().getIntFromText(),
					    null,
					    null,
					 )
				 )
			 }
		 }
		newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                	this.posterUrl = posterUrl
                	this.year = year
                	this.tags = tags
                	this.plot = synopsis
			this.recommendations = recommendations
			addTrailer(trailer)
               }
	    }else{	    
            posterUrl = img?.attr("src")?.ifEmpty { img?.attr("data-src") }
	    tags = fBlock?.select(".RestInformation span")!!.mapNotNull { t ->
                t.text()
            }
	    title = doc.select(".PageTitle .H1Title").text().cleanTitle()
            if(doc.select(".BlockItem a").attr("href").contains("/series/")){//seasons
                doc.select(".BlockItem").map { seas ->
                    seas.select("a").attr("href") }.apmap{ pageIt ->
                    val Sedoc = app.get(pageIt).document
                    val pagEl = Sedoc.select(".pagination > div > ul > li").isNotEmpty()
                    if(pagEl) {
                            Sedoc.select(".pagination > div > ul > li:nth-child(n):not(:last-child) a").apmap {
                                val epidoc = app.get(it.attr("href")).document
                                    epidoc.select(".BlockItem").map{ element ->
                                    episodes.add(
                                        Episode(
                                            element.select("a").attr("href"),
                                            element.select(".BlockTitle").text(),
                                            getSeasonFromString(element.select(".BlockTitle").text()),
                                            element.select(".EPSNumber").text().getIntFromText(),
					    element.select("img:last-of-type").attr("src")?.ifEmpty { img?.attr("data-src") },
					    null,
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
				    el.select("img:last-of-type").attr("src")?.ifEmpty { img?.attr("data-src") },
				    null,
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
                                epidoc.select(".BlockItem").map{ element ->
                                episodes.add(
                                    Episode(
                                        element.select("a").attr("href"),
                                        element.select(".BlockTitle").text(),
                                        getSeasonFromString(element.select(".BlockTitle").text()),
                                        element.select(".EPSNumber").text().getIntFromText(),    
					element.select("img:last-of-type").attr("src")?.ifEmpty { img?.attr("data-src") },
				        null,
                                        )
                                    )
                            }
                        }
                    }else{   
                    doc.select(".BlockItem").map{ el ->
                    episodes.add(
                        Episode(
                                el.select("a").attr("href"),
                                el.select(".BlockTitle").text(),
                                getSeasonFromString(el.select(".BlockTitle").text()),
                                el.select(".EPSNumber").text().getIntFromText(),
				el.select("img:last-of-type").attr("src")?.ifEmpty { img?.attr("data-src") },
				null,
                                )
                           )
                    }
                }
            }
                                
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
		    this.posterUrl = posterUrl?.getFullSize()
		    this.tags = tags
               }
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
	doc.select("code[id*='Embed'] iframe,.DownloadsList a").apmap {
                            var sourceUrl = it.attr("data-srcout").ifEmpty { it.attr("href") }
                            loadExtractor(sourceUrl, data, subtitleCallback, callback)
            }
	return true
    }
}
