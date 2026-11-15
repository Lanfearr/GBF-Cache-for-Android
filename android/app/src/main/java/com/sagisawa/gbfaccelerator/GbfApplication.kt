package com.sagisawa.gbfaccelerator

import android.app.Application
import com.github.kr328.clash.common.Global

class GbfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Global.init(this)
    }
}
