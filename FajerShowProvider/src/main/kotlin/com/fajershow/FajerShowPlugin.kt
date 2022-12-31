package com.fajershow
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FajerShowPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FajerShow())
    }
}