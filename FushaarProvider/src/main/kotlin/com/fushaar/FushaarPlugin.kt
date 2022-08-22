package com.fushaar
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FushaarPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Fushaar())
    }
}