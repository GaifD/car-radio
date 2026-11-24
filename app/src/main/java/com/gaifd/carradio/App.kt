package com.gaifd.carradio

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        L.init(this)
    }
}
