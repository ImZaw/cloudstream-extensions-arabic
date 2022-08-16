package com.animeblkom
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimeBlkomPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeBlkom())
    }
}