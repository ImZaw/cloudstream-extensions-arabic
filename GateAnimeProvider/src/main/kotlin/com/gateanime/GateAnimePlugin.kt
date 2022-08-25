package com.gateanime
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Shahid4uPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(GateAnime())
    }
}