package net.sfelabs.knox.core.common.data.datasource

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
/**
 * Default implementation of [DataStoreSource] that wraps a Jetpack DataStore.
 *
 * This class can be:
 * - Instantiated directly for DI-agnostic usage
 * - Provided by Hilt via knox-hilt module
 * - Accessed via [DataStoreSource.getInstance] for legacy compatibility
 */
class DefaultDataStoreSource(
    private val dataStore: DataStore<Preferences>
) : DataStoreSource {

    override suspend fun <T> setValue(key: String, value: T) {
        dataStore.edit { preferences ->
            when (value) {
                is String -> preferences[stringPreferencesKey(key)] = value
                is Int -> preferences[intPreferencesKey(key)] = value
                is Boolean -> preferences[booleanPreferencesKey(key)] = value
                is Float -> preferences[floatPreferencesKey(key)] = value
                is Long -> preferences[longPreferencesKey(key)] = value
                is Set<*> -> {
                    if (value.all { it is String }) {
                        @Suppress("UNCHECKED_CAST")
                        preferences[stringSetPreferencesKey(key)] = value as Set<String>
                    } else {
                        throw IllegalArgumentException("Set must contain only String values")
                    }
                }
                else -> throw IllegalArgumentException("This type cannot be saved into Preferences")
            }
        }
    }

    override fun <T> getValue(key: String, defaultValue: T): Flow<T> {
        @Suppress("UNCHECKED_CAST")
        return when (defaultValue) {
            is String -> getTypedValue(key, defaultValue, ::stringPreferencesKey)
            is Int -> getTypedValue(key, defaultValue, ::intPreferencesKey)
            is Boolean -> getTypedValue(key, defaultValue, ::booleanPreferencesKey)
            is Float -> getTypedValue(key, defaultValue, ::floatPreferencesKey)
            is Long -> getTypedValue(key, defaultValue, ::longPreferencesKey)
            is Set<*> -> {
                if (defaultValue.all { it is String }) {
                    getTypedValue(key, defaultValue as Set<String>, ::stringSetPreferencesKey)
                } else {
                    throw IllegalArgumentException("Set must contain only String values")
                }
            }
            else -> throw IllegalArgumentException("This type cannot be retrieved from Preferences")
        } as Flow<T>
    }

    private fun <T> getTypedValue(
        key: String,
        defaultValue: T,
        keyFactory: (String) -> Preferences.Key<T>
    ): Flow<T> = dataStore.data.map { preferences ->
        preferences[keyFactory(key)] ?: defaultValue
    }
}
