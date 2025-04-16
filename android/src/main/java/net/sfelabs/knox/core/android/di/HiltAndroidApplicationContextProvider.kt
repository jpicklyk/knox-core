package net.sfelabs.knox.core.android.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import net.sfelabs.knox.core.android.AndroidApplicationContextProvider
import javax.inject.Inject

class HiltAndroidApplicationContextProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : AndroidApplicationContextProvider {
    override fun getContext(): Context = context
}