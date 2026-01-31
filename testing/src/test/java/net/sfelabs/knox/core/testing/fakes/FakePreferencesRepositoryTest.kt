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

class FakePreferencesRepositoryTest {

    private lateinit var repository: FakePreferencesRepository

    @Before
    fun setup() {
        repository = FakePreferencesRepository()
    }

    @Test
    fun `setValue and getValue round trip for string`() = runTest {
        val key = "test_key"
        val value = "test_value"

        repository.setValue(key, value)
        val result = repository.getValue(key, "default").first()

        assertEquals(value, result)
    }

    @Test
    fun `setValue and getValue round trip for boolean`() = runTest {
        val key = "bool_key"

        repository.setValue(key, true)
        val result = repository.getValue(key, false).first()

        assertTrue(result)
    }

    @Test
    fun `setValue and getValue round trip for int`() = runTest {
        val key = "int_key"
        val value = 42

        repository.setValue(key, value)
        val result = repository.getValue(key, 0).first()

        assertEquals(value, result)
    }

    @Test
    fun `getValue returns default when key not set`() = runTest {
        val defaultValue = "default_value"

        val result = repository.getValue("nonexistent", defaultValue).first()

        assertEquals(defaultValue, result)
    }

    @Test
    fun `Flow emits updated value when setValue called`() = runTest {
        val key = "flow_key"
        val initialValue = "initial"
        val updatedValue = "updated"

        repository.setValue(key, initialValue)

        // Get the flow first to ensure we're subscribed
        val flow = repository.getValue(key, "default")

        // Collect values in the background
        val collectedValues = mutableListOf<String>()
        val job = launch {
            flow.collect { collectedValues.add(it) }
        }

        // Let the initial collection happen
        advanceUntilIdle()

        // Update the value
        repository.setValue(key, updatedValue)

        // Let the update propagate
        advanceUntilIdle()

        job.cancel()

        assertTrue("Expected $updatedValue in $collectedValues", collectedValues.contains(updatedValue))
    }

    @Test
    fun `setValueSync sets value without suspending`() = runTest {
        val key = "sync_key"
        val value = "sync_value"

        repository.setValueSync(key, value)
        val result = repository.getValue(key, "default").first()

        assertEquals(value, result)
    }

    @Test
    fun `getStoredValue returns raw stored value`() = runTest {
        val key = "raw_key"
        val value = 123

        repository.setValue(key, value)
        val result: Int? = repository.getStoredValue(key)

        assertEquals(value, result)
    }

    @Test
    fun `getStoredValue returns null for nonexistent key`() {
        val result: String? = repository.getStoredValue("nonexistent")

        assertNull(result)
    }

    @Test
    fun `clear removes all stored values`() = runTest {
        repository.setValue("key1", "value1")
        repository.setValue("key2", "value2")

        repository.clear()

        assertTrue(repository.getAllKeys().isEmpty())
        assertNull(repository.getStoredValue<String>("key1"))
    }

    @Test
    fun `getAllKeys returns all stored keys`() = runTest {
        repository.setValue("key1", "value1")
        repository.setValue("key2", 42)
        repository.setValue("key3", true)

        val keys = repository.getAllKeys()

        assertEquals(setOf("key1", "key2", "key3"), keys)
    }

    @Test
    fun `setValue overwrites existing value`() = runTest {
        val key = "overwrite_key"

        repository.setValue(key, "first")
        repository.setValue(key, "second")

        val result = repository.getValue(key, "default").first()
        assertEquals("second", result)
    }

    @Test
    fun `supports set of strings`() = runTest {
        val key = "set_key"
        val value = setOf("a", "b", "c")

        repository.setValue(key, value)
        val result = repository.getValue(key, emptySet<String>()).first()

        assertEquals(value, result)
    }

    @Test
    fun `supports float values`() = runTest {
        val key = "float_key"
        val value = 3.14f

        repository.setValue(key, value)
        val result = repository.getValue(key, 0f).first()

        assertEquals(value, result)
    }

    @Test
    fun `supports long values`() = runTest {
        val key = "long_key"
        val value = 1234567890L

        repository.setValue(key, value)
        val result = repository.getValue(key, 0L).first()

        assertEquals(value, result)
    }
}
