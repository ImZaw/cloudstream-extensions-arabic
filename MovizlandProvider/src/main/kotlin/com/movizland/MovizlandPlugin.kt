package com.movizland
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class MovizlandPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Movizland())
    }
}