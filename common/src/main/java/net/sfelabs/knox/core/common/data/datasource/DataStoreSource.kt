package net.sfelabs.knox.core.common.data.datasource

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import net.sfelabs.knox.core.android.AndroidApplicationContextProvider
import okio.Path.Companion.toPath

/**
 * Interface for accessing and modifying preference data.
 *
 * This interface provides a type-safe way to store and retrieve preferences
 * using Jetpack DataStore under the hood.
 *
 * ## Usage Patterns
 *
 * ### With Hilt (recommended for apps)
 * Inject [DataStoreSource] directly - the knox-hilt module provides it as a singleton.
 *
 * ### Without Hilt (for libraries like knox-tactical)
 * Use [getInstance] which returns the singleton instance. The instance must be
 * initialized first either by:
 * - Calling [getInstance] with a Context (creates default implementation)
 * - Calling [setInstance] with a custom implementation (used by Hilt)
 */
interface DataStoreSource {
    suspend fun <T> setValue(key: String, value: T)
    fun <T> getValue(key: String, defaultValue: T): Flow<T>

    companion object Factory {
        private const val DATASTORE_FILE_NAME = "knox_showcase_settings.preferences_pb"

        @Volatile
        private var instance: DataStoreSource? = null

        /**
         * Gets the singleton instance of [DataStoreSource].
         *
         * @param context Optional context for initialization. If null, will attempt to use
         *                [AndroidApplicationContextProvider] to obtain the context.
         * @return The singleton [DataStoreSource] instance
         * @throws IllegalStateException if no instance exists and context cannot be obtained
         */
        @Synchronized
        fun getInstance(context: Context? = null): DataStoreSource {
            instance?.let { return it }

            val resolvedContext = context ?: try {
                AndroidApplicationContextProvider.get()
            } catch (e: IllegalStateException) {
                throw IllegalStateException(
                    "DataStoreSource not initialized. Either call getInstance(context) first, " +
                    "ensure Hilt initialization has completed, or initialize AndroidApplicationContextProvider."
                )
            }

            return createDefaultInstance(resolvedContext).also { instance = it }
        }

        /**
         * Sets the singleton instance. Used by DI frameworks (like Hilt) to provide
         * their managed instance.
         *
         * @param dataStoreSource The instance to use as the singleton
         */
        @Synchronized
        fun setInstance(dataStoreSource: DataStoreSource) {
            instance = dataStoreSource
        }

        /**
         * Resets the singleton instance. For testing purposes only.
         */
        @Synchronized
        internal fun reset() {
            instance = null
        }

        private fun createDefaultInstance(context: Context): DataStoreSource {
            val dataStore = PreferenceDataStoreFactory.createWithPath(
                corruptionHandler = ReplaceFileCorruptionHandler(
                    produceNewData = { emptyPreferences() }
                ),
                produceFile = {
                    context.filesDir.resolve(DATASTORE_FILE_NAME).absolutePath.toPath()
                }
            )
            return DefaultDataStoreSource(dataStore)
        }
    }
}