package com.anime4up

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Anime4upPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Anime4up())
        registerMainAPI(WitAnime())
    }
}