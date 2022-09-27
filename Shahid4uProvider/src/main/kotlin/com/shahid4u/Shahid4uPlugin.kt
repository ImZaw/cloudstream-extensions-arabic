package com.shahid4u
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Shahid4uPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Shahid4u())
        registerExtractorAPI(VidHD())
        registerExtractorAPI(GoStream())
        registerExtractorAPI(Vidbom())
    }
}