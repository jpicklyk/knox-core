package net.sfelabs.knox.core.testing.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.sfelabs.knox.core.common.coroutines.DispatcherProvider

/**
 * Test implementation of [DispatcherProvider] that uses [TestDispatcher] for all dispatchers.
 *
 * This allows tests to control coroutine execution timing and avoid flakiness from
 * real dispatchers. By default, uses [UnconfinedTestDispatcher] which executes
 * coroutines eagerly.
 *
 * ## Usage
 * ```kotlin
 * @OptIn(ExperimentalCoroutinesApi::class)
 * class MyViewModelTest {
 *     private val testDispatcher = UnconfinedTestDispatcher()
 *     private val dispatcherProvider = TestDispatcherProvider(testDispatcher)
 *
 *     @Test
 *     fun `test coroutine execution`() = runTest(testDispatcher) {
 *         val viewModel = MyViewModel(dispatcherProvider)
 *         viewModel.loadData()
 *         // Assertions...
 *     }
 * }
 * ```
 *
 * ## Dispatcher Types
 * - [UnconfinedTestDispatcher] - Executes coroutines eagerly (good for most tests)
 * - [StandardTestDispatcher] - Requires manual advancement (good for timing tests)
 *
 * ## Complements MainCoroutineRule
 * This class complements [MainCoroutineRule] which only sets `Dispatchers.Main`.
 * Use [TestDispatcherProvider] when your code uses [DispatcherProvider] interface
 * for dependency injection.
 *
 * @param testDispatcher The test dispatcher to use for all dispatcher types.
 *                       Defaults to [UnconfinedTestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
    testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : DispatcherProvider {

    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val main: CoroutineDispatcher = testDispatcher
    override val mainImmediate: CoroutineDispatcher = testDispatcher
}
