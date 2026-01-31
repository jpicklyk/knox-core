package net.sfelabs.knox.core.testing.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import net.sfelabs.knox.core.common.domain.repository.PreferencesRepository

/**
 * In-memory fake implementation of [PreferencesRepository] for testing.
 *
 * This fake stores values in memory and provides reactive Flow emissions
 * when values change. Use this instead of mocking PreferencesRepository to get
 * behavior-based testing rather than interaction-based testing.
 *
 * ## Usage
 * ```kotlin
 * private val repository = FakePreferencesRepository()
 *
 * @Test
 * fun `test preference round trip`() = runTest {
 *     repository.setValue("key", true)
 *     val result = repository.getValue("key", false).first()
 *     assertTrue(result)
 * }
 * ```
 *
 * ## Test Helpers
 * - [setValueSync] - Set a value synchronously (useful in test setup)
 * - [getStoredValue] - Get the raw stored value without Flow
 * - [clear] - Reset all stored values
 * - [getAllKeys] - Get all keys currently stored
 *
 * ## When to Use Fakes vs Mocks
 * | Use FakePreferencesRepository | Use mockk() |
 * |-------------------------------|-------------|
 * | Testing data flow (get/set)   | Verifying method calls |
 * | Multiple operations in sequence | Testing error scenarios |
 * | Reusable across tests | One-off verification |
 */
class FakePreferencesRepository : PreferencesRepository {

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
