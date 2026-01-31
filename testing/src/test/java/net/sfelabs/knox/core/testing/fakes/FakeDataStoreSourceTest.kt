package net.sfelabs.knox.core.testing.fakes

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)

class FakeDataStoreSourceTest {

    private lateinit var dataStoreSource: FakeDataStoreSource

    @Before
    fun setup() {
        dataStoreSource = FakeDataStoreSource()
    }

    @Test
    fun `setValue and getValue round trip for string`() = runTest {
        val key = "test_key"
        val value = "test_value"

        dataStoreSource.setValue(key, value)
        val result = dataStoreSource.getValue(key, "default").first()

        assertEquals(value, result)
    }

    @Test
    fun `setValue and getValue round trip for boolean`() = runTest {
        val key = "bool_key"

        dataStoreSource.setValue(key, true)
        val result = dataStoreSource.getValue(key, false).first()

        assertTrue(result)
    }

    @Test
    fun `setValue and getValue round trip for int`() = runTest {
        val key = "int_key"
        val value = 42

        dataStoreSource.setValue(key, value)
        val result = dataStoreSource.getValue(key, 0).first()

        assertEquals(value, result)
    }

    @Test
    fun `getValue returns default when key not set`() = runTest {
        val defaultValue = "default_value"

        val result = dataStoreSource.getValue("nonexistent", defaultValue).first()

        assertEquals(defaultValue, result)
    }

    @Test
    fun `Flow emits updated value when setValue called`() = runTest {
        val key = "flow_key"
        val initialValue = "initial"
        val updatedValue = "updated"

        dataStoreSource.setValue(key, initialValue)

        // Get the flow first to ensure we're subscribed
        val flow = dataStoreSource.getValue(key, "default")

        // Collect values in the background
        val collectedValues = mutableListOf<String>()
        val job = launch {
            flow.collect { collectedValues.add(it) }
        }

        // Let the initial collection happen
        advanceUntilIdle()

        // Update the value
        dataStoreSource.setValue(key, updatedValue)

        // Let the update propagate
        advanceUntilIdle()

        job.cancel()

        assertTrue("Expected $updatedValue in $collectedValues", collectedValues.contains(updatedValue))
    }

    @Test
    fun `setValueSync sets value without suspending`() = runTest {
        val key = "sync_key"
        val value = "sync_value"

        dataStoreSource.setValueSync(key, value)
        val result = dataStoreSource.getValue(key, "default").first()

        assertEquals(value, result)
    }

    @Test
    fun `getStoredValue returns raw stored value`() = runTest {
        val key = "raw_key"
        val value = 123

        dataStoreSource.setValue(key, value)
        val result: Int? = dataStoreSource.getStoredValue(key)

        assertEquals(value, result)
    }

    @Test
    fun `getStoredValue returns null for nonexistent key`() {
        val result: String? = dataStoreSource.getStoredValue("nonexistent")

        assertNull(result)
    }

    @Test
    fun `clear removes all stored values`() = runTest {
        dataStoreSource.setValue("key1", "value1")
        dataStoreSource.setValue("key2", "value2")

        dataStoreSource.clear()

        assertTrue(dataStoreSource.getAllKeys().isEmpty())
        assertNull(dataStoreSource.getStoredValue<String>("key1"))
    }

    @Test
    fun `getAllKeys returns all stored keys`() = runTest {
        dataStoreSource.setValue("key1", "value1")
        dataStoreSource.setValue("key2", 42)
        dataStoreSource.setValue("key3", true)

        val keys = dataStoreSource.getAllKeys()

        assertEquals(setOf("key1", "key2", "key3"), keys)
    }

    @Test
    fun `setValue overwrites existing value`() = runTest {
        val key = "overwrite_key"

        dataStoreSource.setValue(key, "first")
        dataStoreSource.setValue(key, "second")

        val result = dataStoreSource.getValue(key, "default").first()
        assertEquals("second", result)
    }

    @Test
    fun `supports set of strings`() = runTest {
        val key = "set_key"
        val value = setOf("a", "b", "c")

        dataStoreSource.setValue(key, value)
        val result = dataStoreSource.getValue(key, emptySet<String>()).first()

        assertEquals(value, result)
    }

    @Test
    fun `supports float values`() = runTest {
        val key = "float_key"
        val value = 3.14f

        dataStoreSource.setValue(key, value)
        val result = dataStoreSource.getValue(key, 0f).first()

        assertEquals(value, result)
    }

    @Test
    fun `supports long values`() = runTest {
        val key = "long_key"
        val value = 1234567890L

        dataStoreSource.setValue(key, value)
        val result = dataStoreSource.getValue(key, 0L).first()

        assertEquals(value, result)
    }
}
