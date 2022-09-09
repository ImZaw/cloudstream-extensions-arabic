package com.animeiat
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimeiatPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Animeiat())
    }
}