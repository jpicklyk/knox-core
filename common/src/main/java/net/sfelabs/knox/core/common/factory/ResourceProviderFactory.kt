package net.sfelabs.knox.core.common.factory

import android.content.Context
import net.sfelabs.knox.core.common.presentation.ResourceProvider
import net.sfelabs.knox.core.common.presentation.ResourceProviderImpl

/**
 * Factory interface for creating ResourceProvider instances.
 * This allows DI frameworks to create ResourceProvider without
 * needing access to the internal implementation class.
 */
interface ResourceProviderFactory {
    fun create(context: Context): ResourceProvider
}

/**
 * Default factory implementation that creates ResourceProviderImpl.
 */
object DefaultResourceProviderFactory : ResourceProviderFactory {
    override fun create(context: Context): ResourceProvider = ResourceProviderImpl(context)
}
