package com.egybest


import android.annotation.TargetApi
import android.os.Build
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.util.Base64
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

fun String.runJS(variableName: String): String {
    val rhino = Context.enter()
    rhino.initSafeStandardObjects()
    rhino.optimizationLevel = -1
    val scope: Scriptable = rhino.initSafeStandardObjects()
    val script = this
    val result: String
    try {
        var js = ""
        for (i in script.indices) {
            js += script[i]
        }
        rhino.evaluateString(scope, js, "JavaScript", 1, null)
        result = Context.toString(scope.get(variableName, scope))
    } finally {
        Context.exit()
    }
    return result
}

class EgyBest : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://www.egy.best"
    override var name = "EgyBest"
	var pssid = ""
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = this.attr("href") ?: return null
        val posterUrl = select("img")?.attr("src")
        var title = select("span.title").text()
        val year = title.getYearFromTitle()
        val isMovie = Regex(".*/movie/.*|.*/masrahiya/.*").matches(url)
        val tvType = if (isMovie) TvType.Movie else TvType.TvSeries
        title = if (year !== null) title else title.split(" (")[0].trim()
        val quality = select("span.ribbon span").text().replace("-", "")
        // If you need to differentiate use the url.
        return MovieSearchResponse(
            title,
            url,
            this@EgyBest.name,
            tvType,
            posterUrl,
            year,
            null,
            quality = getQualityFromString(quality)
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/trending/?page=" to "الأفلام الأكثر مشاهدة",
        "$mainUrl/movies/?page=" to "أفلام جديدة",
        "$mainUrl/tv/?page=" to "مسلسلات جديدة ",
        "$mainUrl/tv/korean?page=" to "الدراما الكورية ",
        "$mainUrl/animes/popular?page=" to "مسلسلات الانمي",
        "$mainUrl/wwe/?page=" to "عروض المصارعة ",
        "$mainUrl/movies/latest-bluray-2020-2019?page=" to "أفلام جديدة BluRay",
        "$mainUrl/masrahiyat/?page=" to "مسرحيات ",
        "$mainUrl/movies/latest?page=" to "أحدث الاضافات",
        "$mainUrl/movies/comedy?page=" to "أفلام كوميدية",
        "$mainUrl/explore/?q=superhero/" to "أفلام سوبر هيرو",
        "$mainUrl/movies/animation?page=" to "أفلام انمي و كرتون",
        "$mainUrl/movies/romance?page=" to "أفلام رومانسية",
        "$mainUrl/movies/drama?page=" to "أفلام دراما",
        "$mainUrl/movies/horror?page=" to "أفلام رعب",
        "$mainUrl/movies/documentary?page=" to "أفلام وثائقية",
        "$mainUrl/World-War-Movies/?page=" to "أفلام عن الحرب العالمية ☢",
        "$mainUrl/End-Of-The-World-Movies/?page=" to "أفلام عن نهاية العالم",
        "$mainUrl/movies/arab?page=" to "أفلام عربية ",
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select(".movie")
            .mapNotNull { element ->
                element.toSearchResponse()
            }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ","%20")
        val result = arrayListOf<SearchResponse>()
        listOf("$mainUrl/explore/?q=$q").apmap { url ->
            val d = app.get(url).document
            d.select("div.movies a").not("a.auto.load.btn.b").mapNotNull {
                it.toSearchResponse()?.let { it1 -> result.add(it1) }
            }
        }
        return result.distinct().sortedBy { it.name }
    }

    private fun String.getYearFromTitle(): Int? {
        return Regex("""\(\d{4}\)""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = Regex(".*/movie/.*|.*/masrahiya/.*").matches(url)
        val posterUrl = doc.select("div.movie_img a img")?.attr("src")
        val year = doc.select("div.movie_title h1 a")?.text()?.toIntOrNull()
        val title = doc.select("div.movie_title h1 span").text()
        val youtubeTrailer = doc.select("div.play")?.attr("url")

        val synopsis = doc.select("div.mbox").firstOrNull {
            it.text().contains("القصة")
        }?.text()?.replace("القصة ", "")

        val tags = doc.select("table.movieTable tbody tr").firstOrNull {
            it.text().contains("النوع")
        }?.select("a")?.map { it.text() }

        val actors = doc.select("div.cast_list .cast_item").mapNotNull {
            val name = it.selectFirst("div > a > img")?.attr("alt") ?: return@mapNotNull null
            val image = it.selectFirst("div > a > img")?.attr("src") ?: return@mapNotNull null
            val roleString = it.selectFirst("div > span")!!.text()
            val mainActor = Actor(name, image)
            ActorData(actor = mainActor, roleString = roleString)
        }

        return if (isMovie) {
            val recommendations = doc.select(".movies_small .movie").mapNotNull { element ->
                element.toSearchResponse()
            }

            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.recommendations = recommendations
                this.plot = synopsis
                this.tags = tags
                this.actors = actors
                addTrailer(youtubeTrailer)
            }
        } else {
            val episodes = ArrayList<Episode>()
            doc.select("#mainLoad > div:nth-child(2) > div.h_scroll > div a").map {
                it.attr("href")
            }.apmap {
                val d = app.get(it).document
                val season = Regex("season-(.....)").find(it)?.groupValues?.getOrNull(1)?.getIntFromText()
                if(d.select("tr.published").isNotEmpty()) {
                    d.select("tr.published").map { element ->
                        val ep = Regex("ep-(.....)").find(element.select(".ep_title a").attr("href"))?.groupValues?.getOrNull(1)?.getIntFromText()
                        episodes.add(
                            Episode(
                                element.select(".ep_title a").attr("href"),
                                name = element.select("td.ep_title").html().replace(".*</span>|</a>".toRegex(), ""),
                                season,
                                ep,
                                rating = element.select("td.tam:not(.date, .ep_len)").text().getIntFromText()
                            )
                        )
                    }
                } else {
                    d.select("#mainLoad > div:nth-child(3) > div.movies_small a").map { eit ->
                        val ep = Regex("ep-(.....)").find(eit.attr("href"))?.groupValues?.getOrNull(1)?.getIntFromText()
                        episodes.add(
                            Episode(
                                eit.attr("href"),
                                eit.select("span.title").text(),
                                season,
                                ep,
                            )
                        )
                    }
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().sortedBy { it.episode }) {
                this.posterUrl = posterUrl
                this.tags = tags
                this.year = year
                this.plot = synopsis
                this.actors = actors
                addTrailer(youtubeTrailer)
            }
        }
    }
	
    @TargetApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val baseURL = data.split("/")[0] + "//" + data.split("/")[2]
        val client = Requests().baseClient
        val session = Session(client)
        val doc = session.get(data).document

        val vidstreamURL = baseURL + doc.select("iframe.auto-size").attr("src")

        val videoSoup = session.get(vidstreamURL, cookies = mapOf(
            "PSSID" to this@EgyBest.pssid,
        )).document
        videoSoup.select("source").firstOrNull { it.hasAttr("src") }?.attr("src")?.let {
            M3u8Helper.generateM3u8(
                this.name,
                it,
                referer = mainUrl,
                headers = mapOf("range" to "bytes=0-")
            ).forEach(callback)
        } ?: run {
            var jsCode = videoSoup.select("script")[1].html()
            val function = videoSoup.select("script")[2].attr("onload")
            val verificationToken = Regex("\\{'[0-9a-zA-Z_]*':'ok'\\}").findAll(jsCode).first().value.replace("\\{'|':.*".toRegex(), "")
            val encodedAdLinkVar = Regex("\\([0-9a-zA-Z_]{2,12}\\[Math").findAll(jsCode).first().value.replace("\\(|\\[M.*".toRegex(),"")
            val encodingArraysRegEx = Regex(",[0-9a-zA-Z_]{2,12}=\\[]").findAll(jsCode).toList()
            val firstEncodingArray = encodingArraysRegEx[1].value.replace(",|=.*".toRegex(),"")
            val secondEncodingArray = encodingArraysRegEx[2].value.replace(",|=.*".toRegex(),"")
            
            jsCode = jsCode.replace("^<script type=\"text/javascript\">".toRegex(),"")
            jsCode = jsCode.replace(",\\\$\\('\\*'\\).*".toRegex(),";")
            jsCode = jsCode.replace(",ismob=.*]\\);".toRegex(),";")
            jsCode = jsCode.replace("var a0b=\\(function\\(\\)(.*)a0a\\(\\);".toRegex(),"")
            jsCode = "$jsCode var link = ''; for (var i = 0; i <= $secondEncodingArray['length']; i++) { link += $firstEncodingArray[$secondEncodingArray[i]] || ''; } return [link, $encodedAdLinkVar[0]] };var result = $function"

            val javascriptResult = jsCode.runJS("result").split(",")
            val verificationPath = javascriptResult[0]
            val encodedAdPath = javascriptResult[1]

            val encodedString = encodedAdPath + "=".repeat(encodedAdPath.length % 4)
            val decodedPath = String(Base64.getDecoder().decode(encodedString))
            val adLink = "$baseURL/$decodedPath"
            val verificationLink = "$baseURL/tvc.php?verify=$verificationPath"
            session.get(adLink)
            session.post(verificationLink, data=mapOf(verificationToken to "ok"))

            val vidstreamResponse = session.get(vidstreamURL).document
            val mediaLink = baseURL + vidstreamResponse.select("source").attr("src")
            this@EgyBest.pssid = session.baseClient.cookieJar.loadForRequest(data.toHttpUrl())[0].toString().split(";")[0].split("=")[1]
            M3u8Helper.generateM3u8(
                this.name,
                mediaLink,
                referer = mainUrl,
                headers = mapOf("range" to "bytes=0-")
            ).forEach(callback)
        }
        return true
    }
}
