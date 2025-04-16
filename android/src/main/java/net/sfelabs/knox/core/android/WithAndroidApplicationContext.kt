package net.sfelabs.knox.core.android

import android.content.Context

interface WithAndroidApplicationContext {
    val applicationContext: Context
        get() = AndroidApplicationContextProvider.get()
}