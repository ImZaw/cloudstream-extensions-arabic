package com.yallashoots
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class YallaShootsPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(YallaShoots())
    }
}