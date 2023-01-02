package com.arabseed
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class ArabSeedPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(ArabSeed())
    }
}