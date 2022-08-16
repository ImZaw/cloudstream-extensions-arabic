package com.extractors

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class ExtractorPlugin: Plugin() {
    override fun load(context: Context) {
        registerExtractorAPI(DoodLaExtractor())
        registerExtractorAPI(DoodCxExtractor())
        registerExtractorAPI(DoodShExtractor())
        registerExtractorAPI(DoodWatchExtractor())
        registerExtractorAPI(DoodPmExtractor())
        registerExtractorAPI(DoodToExtractor())
        registerExtractorAPI(DoodSoExtractor())
        registerExtractorAPI(DoodWsExtractor())
        registerExtractorAPI(Uqload())
        registerExtractorAPI(Uqload1())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(VoeExtractor())
        registerExtractorAPI(JWPlayer())
        registerExtractorAPI(VidBom())
        registerExtractorAPI(UpstreamExtractor())
        registerExtractorAPI(Streamlare())
        registerExtractorAPI(Slmaxed())
    }
}
