package net.sfelabs.knox.core.common.domain.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import net.sfelabs.knox.core.android.AndroidApplicationContextProvider
import net.sfelabs.knox.core.common.data.datasource.DataStoreSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class PreferencesRepositoryTest {

    @Before
    fun setup() {
        // Reset singletons before each test
        PreferencesRepository.reset()
        DataStoreSource.reset()
    }

    @After
    fun tearDown() {
        // Clean up after each test
        PreferencesRepository.reset()
        DataStoreSource.reset()
    }

    @Test
    fun `setInstance registers the provided instance`() {
        val mockRepository = mockk<PreferencesRepository>()

        PreferencesRepository.setInstance(mockRepository)

        val result = PreferencesRepository.getInstance()
        assertSame(mockRepository, result)
    }

    @Test
    fun `getInstance returns the same instance on subsequent calls`() {
        val mockRepository = mockk<PreferencesRepository>()
        PreferencesRepository.setInstance(mockRepository)

        val first = PreferencesRepository.getInstance()
        val second = PreferencesRepository.getInstance()

        assertSame(first, second)
    }

    @Test
    fun `reset clears the singleton instance`() {
        val mockRepository1 = mockk<PreferencesRepository>()
        val mockRepository2 = mockk<PreferencesRepository>()

        PreferencesRepository.setInstance(mockRepository1)
        assertSame(mockRepository1, PreferencesRepository.getInstance())

        PreferencesRepository.reset()
        PreferencesRepository.setInstance(mockRepository2)

        assertSame(mockRepository2, PreferencesRepository.getInstance())
    }

    @Test(expected = IllegalStateException::class)
    fun `getInstance throws when no instance set and no context available`() {
        // Mock AndroidApplicationContextProvider to throw
        mockkObject(AndroidApplicationContextProvider)
        try {
            every { AndroidApplicationContextProvider.get() } throws IllegalStateException("Not initialized")

            // This should throw because no instance is set and AndroidApplicationContextProvider throws
            PreferencesRepository.getInstance()
        } finally {
            unmockkObject(AndroidApplicationContextProvider)
        }
    }

    @Test
    fun `DefaultPreferencesRepository delegates to DataStoreSource`() {
        val mockDataStoreSource = mockk<DataStoreSource>()
        val expectedFlow = flowOf("test_value")

        every { mockDataStoreSource.getValue("key", "") } returns expectedFlow

        val repository = DefaultPreferencesRepository(mockDataStoreSource)
        val result = repository.getValue("key", "")

        assertSame(expectedFlow, result)
    }
}
