package com.egybest


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.Requests

class EgyBest : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://www.egy.best"
    override var name = "EgyBest"
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

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        // url, title
        val doc = app.get(mainUrl).document
        val pages = arrayListOf<HomePageList>()
        doc.select("#mainLoad div.mbox").apmap {
            val name = it.select(".bdb.pda > strong").text()
            if (it.select(".movie").first()?.attr("href")?.contains("season-(.....)|ep-(.....)".toRegex()) == true) return@apmap
            val list = arrayListOf<SearchResponse>()
            it.select(".movie").map { element ->
                list.add(element.toSearchResponse()!!)
            }
            pages.add(HomePageList(name, list))
        }
        return HomePageResponse(pages)
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
    data class Sources (
        @JsonProperty("quality") val quality: Int?,
        @JsonProperty("link") val link: String
    )

    private fun String.ExtractLinks(): Boolean? {
        val list = Regex("#EXT.*\\n.*").findAll(this).toList()
        println(list)
        list.map {
            val url = Regex(".*stream\\.m3u8").find(it.value)?.value.toString()
            val quality = Regex("[0-9]{3,4}x[0-9]{3,4}").find(it.value)?.value?.replace(".*x".toRegex(),"")?.toInt()
                ExtractorLink(
                    this@EgyBest.name,
                    this@EgyBest.name,
                    url,
                    this@EgyBest.mainUrl,
                    quality ?: Qualities.Unknown.value,
                    true
                )
        }
        return true
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        /*val baseURL = data.split("/")[0] + "//" + data.split("/")[2]

        val session = Requests()
        val episodeSoup = session.get(data).document

        val vidstreamURL = fixUrlNull(episodeSoup.selectFirst("iframe.auto-size")?.attr("src") ) ?: throw ErrorLoadingException("No iframe")

        val videoSoup = app.get(vidstreamURL).document
        fixUrlNull( videoSoup.select("source").firstOrNull { it.hasAttr("src") }?.attr("src"))?.let {
            app.get(it).text.replace("#EXTM3U\n","").ExtractLinks()
        } ?: run {
            var jsCode = videoSoup.select("script")[1].data()
            println(jsCode)
            val verificationToken = Regex("\\{'[0-9a-zA-Z_]*':'ok'\\}").findAll(jsCode).first().value.replace("\\{'|':.*".toRegex(), "")
            val encodedAdLinkVar = Regex("\\([0-9a-zA-Z_]{2,12}\\[Math").findAll(jsCode).first().value.replace("\\(|\\[M.*".toRegex(),"")
            val encodingArraysRegEx = Regex(",[0-9a-zA-Z_]{2,12}=\\[\\]").findAll(jsCode).toList()

            val firstEncodingArray = encodingArraysRegEx[1].value.replace(",|=.*".toRegex(),"")
            val secondEncodingArray = encodingArraysRegEx[2].value.replace(",|=.*".toRegex(),"")

            jsCode = jsCode.replace("^<script type=\"text/javascript\">".toRegex(),"")
            jsCode = jsCode.replace("[;,]\\\$\\('\\*'\\)(.*)\$".toRegex(),";")
            jsCode = jsCode.replace(",ismob=(.*)\\(navigator\\[(.*)\\]\\)[,;]".toRegex(),";")
            jsCode = jsCode.replace("var a0b=function\\(\\)(.*)a0a\\(\\);".toRegex(),"")
            jsCode += "var link = ''; for (var i = 0; i <= $secondEncodingArray['length']; i++) { link += $firstEncodingArray[$secondEncodingArray[i]] || ''; } return [link, $encodedAdLinkVar[0]] }"

            // till here everything should be fine
            val jsCodeReturn = js(jsCode)()
            val verificationPath = jsCodeReturn[0]
            val encodedAdPath = jsCodeReturn[1]

            val adLink = baseURL + "/" + str(decode(encodedAdPath + "=" * (-len(encodedAdPath) % 4)), "utf-8")
            val session.get(adLink)

            val verificationLink = baseURL + "/tvc.php?verify=" + verificationPath
            val session.post(verificationLink, data={verificationToken: "ok"})

            val vidstreamResponseText = session.get(vidstreamURL).text
            val videoSoup = BeautifulSoup(vidstreamResponseText, features="html.parser")

            val qualityLinksFileURL = baseURL + videoSoup.body.find("source").get("src")
        }


        return true*/

        val requestJSON = app.get("https://egybest-sgu8utcomnfb.runkit.sh/egybest?url=$data").text
        // To solve this you need to send a verify request which is pretty hidden, see
        // https://vear.egybest.deals/tvc.php?verify=.......
        val jsonArray = parseJson<List<Sources>>(requestJSON)
        for (i in jsonArray) {
            val quality = i.quality
            val link = i.link
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    link,
                    this.mainUrl,
                    quality!!,
                    link.replace(".*\\.".toRegex(),"") == "m3u8",
                    // Does not work without these headers!
                    headers = mapOf("range" to "bytes=0-"),
                )
            )
        }
        return true
    }
}
