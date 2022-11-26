package com.ninegoal

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class NineGoalPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(NineGoal())
    }
}