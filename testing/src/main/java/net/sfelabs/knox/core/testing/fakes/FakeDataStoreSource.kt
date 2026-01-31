package net.sfelabs.knox.core.testing.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import net.sfelabs.knox.core.common.data.datasource.DataStoreSource

/**
 * In-memory fake implementation of [DataStoreSource] for testing.
 *
 * This fake stores values in memory and provides reactive Flow emissions
 * when values change. Use this instead of mocking DataStoreSource to get
 * behavior-based testing rather than interaction-based testing.
 *
 * ## Usage
 * ```kotlin
 * private val dataStoreSource = FakeDataStoreSource()
 *
 * @Test
 * fun `test preference round trip`() = runTest {
 *     dataStoreSource.setValue("key", "value")
 *     val result = dataStoreSource.getValue("key", "default").first()
 *     assertEquals("value", result)
 * }
 * ```
 *
 * ## Test Helpers
 * - [setValueSync] - Set a value synchronously (useful in test setup)
 * - [getStoredValue] - Get the raw stored value without Flow
 * - [clear] - Reset all stored values
 * - [getAllKeys] - Get all keys currently stored
 */
class FakeDataStoreSource : DataStoreSource {

    private val store = mutableMapOf<String, Any?>()
    private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()

    override suspend fun <T> setValue(key: String, value: T) {
        store[key] = value
        getOrCreateFlow(key, value).value = value
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getValue(key: String, defaultValue: T): Flow<T> {
        return getOrCreateFlow(key, defaultValue).map { it as T }
    }

    // Test helpers

    /**
     * Sets a value synchronously. Useful for test setup before running the test.
     */
    fun setValueSync(key: String, value: Any?) {
        store[key] = value
        flows[key]?.value = value
    }

    /**
     * Gets the raw stored value without going through a Flow.
     * Returns null if the key doesn't exist.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getStoredValue(key: String): T? = store[key] as? T

    /**
     * Clears all stored values and flows. Call in @Before or @After to reset state.
     */
    fun clear() {
        store.clear()
        flows.clear()
    }

    /**
     * Returns all keys currently stored.
     */
    fun getAllKeys(): Set<String> = store.keys.toSet()

    private fun getOrCreateFlow(key: String, defaultValue: Any?): MutableStateFlow<Any?> {
        return flows.getOrPut(key) {
            MutableStateFlow(store[key] ?: defaultValue)
        }
    }
}
