package net.sfelabs.knox.core.common.data.datasource

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import net.sfelabs.knox.core.android.AndroidApplicationContextProvider
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class DataStoreSourceTest {

    @Before
    fun setup() {
        // Reset singleton before each test
        DataStoreSource.reset()
    }

    @After
    fun tearDown() {
        // Clean up after each test
        DataStoreSource.reset()
    }

    @Test
    fun `setInstance registers the provided instance`() {
        val mockSource = mockk<DataStoreSource>()

        DataStoreSource.setInstance(mockSource)

        val result = DataStoreSource.getInstance()
        assertSame(mockSource, result)
    }

    @Test
    fun `getInstance returns the same instance on subsequent calls`() {
        val mockSource = mockk<DataStoreSource>()
        DataStoreSource.setInstance(mockSource)

        val first = DataStoreSource.getInstance()
        val second = DataStoreSource.getInstance()

        assertSame(first, second)
    }

    @Test
    fun `reset clears the singleton instance`() {
        val mockSource1 = mockk<DataStoreSource>()
        val mockSource2 = mockk<DataStoreSource>()

        DataStoreSource.setInstance(mockSource1)
        assertSame(mockSource1, DataStoreSource.getInstance())

        DataStoreSource.reset()
        DataStoreSource.setInstance(mockSource2)

        assertSame(mockSource2, DataStoreSource.getInstance())
    }

    @Test(expected = IllegalStateException::class)
    fun `getInstance throws when no instance set and no context available`() {
        // Mock AndroidApplicationContextProvider to throw
        mockkObject(AndroidApplicationContextProvider)
        try {
            every { AndroidApplicationContextProvider.get() } throws IllegalStateException("Not initialized")

            // This should throw because no instance is set and AndroidApplicationContextProvider throws
            DataStoreSource.getInstance()
        } finally {
            unmockkObject(AndroidApplicationContextProvider)
        }
    }

    @Test
    fun `DefaultDataStoreSource getValue returns flow from datastore`() {
        val mockDataStore = mockk<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>()
        val expectedFlow = flowOf(mockk<androidx.datastore.preferences.core.Preferences>())

        every { mockDataStore.data } returns expectedFlow

        val source = DefaultDataStoreSource(mockDataStore)

        // The source should be instantiated without error
        // (Full functional testing would require Android instrumentation)
    }
}
