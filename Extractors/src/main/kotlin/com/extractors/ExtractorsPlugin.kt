package com.extractors
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class ExtractorsPlugin: Plugin() {
    override fun load(context: Context) {
        registerExtractorAPI(VidHDJW())
        registerExtractorAPI(VidHD())
        registerExtractorAPI(GoStream())
        registerExtractorAPI(MyVid())
        registerExtractorAPI(Vidshar())
        registerExtractorAPI(Vadbam())
        registerExtractorAPI(Vidbom())
        registerExtractorAPI(Govad())
        registerExtractorAPI(Moshahda())
        registerExtractorAPI(VoeSx())
        registerExtractorAPI(LinkBox())
        registerExtractorAPI(Vidmoly())
    }
}