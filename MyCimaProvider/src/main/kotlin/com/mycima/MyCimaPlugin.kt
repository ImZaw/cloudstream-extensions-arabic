package com.mycima
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class MyCimaPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(MyCima())
    }
}