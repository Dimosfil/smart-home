package com.dimosfil.smarthome

import android.app.Application
import com.thingclips.smart.home.sdk.ThingHomeSdk

class SmartHomeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.TUYA_CONFIGURED) {
            ThingHomeSdk.init(this)
            ThingHomeSdk.setDebugMode(BuildConfig.DEBUG)
        }
    }

    override fun onTerminate() {
        if (BuildConfig.TUYA_CONFIGURED) ThingHomeSdk.onDestroy()
        super.onTerminate()
    }
}
