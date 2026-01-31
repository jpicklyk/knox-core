package net.sfelabs.knox.core.common.domain.repository

import kotlinx.coroutines.flow.Flow
import net.sfelabs.knox.core.common.data.datasource.DataStoreSource
/**
 * Default implementation of [PreferencesRepository] that delegates to [DataStoreSource].
 *
 * This class can be:
 * - Instantiated directly for DI-agnostic usage
 * - Provided by Hilt via knox-hilt module
 * - Accessed via [PreferencesRepository.getInstance] for legacy compatibility
 */
class DefaultPreferencesRepository(
    private val dataStoreSource: DataStoreSource
) : PreferencesRepository {

    override suspend fun <T> setValue(key: String, value: T) {
        dataStoreSource.setValue(key, value)
    }

    override fun <T> getValue(key: String, defaultValue: T): Flow<T> {
        return dataStoreSource.getValue(key, defaultValue)
    }
}
