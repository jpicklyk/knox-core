package net.sfelabs.knox.core.common.domain.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import net.sfelabs.knox.core.android.AndroidApplicationContextProvider
import net.sfelabs.knox.core.common.data.datasource.DataStoreSource

/**
 * Repository interface for accessing and modifying application preferences.
 *
 * This interface provides a clean abstraction over [DataStoreSource] for use
 * in domain and presentation layers.
 *
 * ## Usage Patterns
 *
 * ### With Hilt (recommended for apps)
 * Inject [PreferencesRepository] directly - the knox-hilt module provides it as a singleton.
 *
 * ### Without Hilt (for libraries like knox-tactical)
 * Use [getInstance] which returns the singleton instance. The instance must be
 * initialized first either by:
 * - Calling [getInstance] with a Context (creates default implementation)
 * - Calling [setInstance] with a custom implementation (used by Hilt)
 */
interface PreferencesRepository {
    suspend fun <T> setValue(key: String, value: T)
    fun <T> getValue(key: String, defaultValue: T): Flow<T>

    companion object Factory {
        @Volatile
        private var instance: PreferencesRepository? = null

        /**
         * Gets the singleton instance of [PreferencesRepository].
         *
         * @param context Optional context for initialization. If null, will attempt to use
         *                [AndroidApplicationContextProvider] to obtain the context.
         * @return The singleton [PreferencesRepository] instance
         * @throws IllegalStateException if no instance exists and context cannot be obtained
         */
        @Synchronized
        fun getInstance(context: Context? = null): PreferencesRepository {
            instance?.let { return it }

            val resolvedContext = context ?: try {
                AndroidApplicationContextProvider.get()
            } catch (e: IllegalStateException) {
                throw IllegalStateException(
                    "PreferencesRepository not initialized. Either call getInstance(context) first, " +
                    "ensure Hilt initialization has completed, or initialize AndroidApplicationContextProvider."
                )
            }

            return DefaultPreferencesRepository(
                DataStoreSource.getInstance(resolvedContext)
            ).also { instance = it }
        }

        /**
         * Sets the singleton instance. Used by DI frameworks (like Hilt) to provide
         * their managed instance.
         *
         * @param repository The instance to use as the singleton
         */
        @Synchronized
        fun setInstance(repository: PreferencesRepository) {
            instance = repository
        }

        /**
         * Resets the singleton instance. For testing purposes only.
         */
        @Synchronized
        internal fun reset() {
            instance = null
        }
    }
}