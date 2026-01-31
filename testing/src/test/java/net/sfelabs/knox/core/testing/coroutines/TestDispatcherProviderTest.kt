package net.sfelabs.knox.core.testing.coroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProviderTest {

    @Test
    fun `default constructor uses UnconfinedTestDispatcher`() {
        val provider = TestDispatcherProvider()

        // All dispatchers should be instances of the same type
        // UnconfinedTestDispatcher executes eagerly
        assertSame(provider.io, provider.default)
        assertSame(provider.default, provider.main)
        assertSame(provider.main, provider.mainImmediate)
    }

    @Test
    fun `custom dispatcher is used for all properties`() {
        val testDispatcher = StandardTestDispatcher()
        val provider = TestDispatcherProvider(testDispatcher)

        assertSame(testDispatcher, provider.io)
        assertSame(testDispatcher, provider.default)
        assertSame(testDispatcher, provider.main)
        assertSame(testDispatcher, provider.mainImmediate)
    }

    @Test
    fun `unconfined test dispatcher works correctly`() {
        val testDispatcher = UnconfinedTestDispatcher()
        val provider = TestDispatcherProvider(testDispatcher)

        assertSame(testDispatcher, provider.io)
        assertSame(testDispatcher, provider.default)
        assertSame(testDispatcher, provider.main)
        assertSame(testDispatcher, provider.mainImmediate)
    }

    @Test
    fun `io dispatcher returns configured test dispatcher`() {
        val testDispatcher = StandardTestDispatcher()
        val provider = TestDispatcherProvider(testDispatcher)

        assertSame(testDispatcher, provider.io)
    }

    @Test
    fun `default dispatcher returns configured test dispatcher`() {
        val testDispatcher = StandardTestDispatcher()
        val provider = TestDispatcherProvider(testDispatcher)

        assertSame(testDispatcher, provider.default)
    }

    @Test
    fun `main dispatcher returns configured test dispatcher`() {
        val testDispatcher = StandardTestDispatcher()
        val provider = TestDispatcherProvider(testDispatcher)

        assertSame(testDispatcher, provider.main)
    }

    @Test
    fun `mainImmediate dispatcher returns configured test dispatcher`() {
        val testDispatcher = StandardTestDispatcher()
        val provider = TestDispatcherProvider(testDispatcher)

        assertSame(testDispatcher, provider.mainImmediate)
    }
}
