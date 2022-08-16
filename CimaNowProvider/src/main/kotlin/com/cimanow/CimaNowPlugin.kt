package com.cimanow
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class CimaNowPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CimaNow())
    }
}