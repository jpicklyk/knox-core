import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.domain.usecase.model.DefaultApiError
import net.sfelabs.knox.core.domain.usecase.executor.UseCaseBuilder
import net.sfelabs.knox.core.domain.usecase.executor.UseCaseBuilder.UseCaseBuilderState
import net.sfelabs.knox.core.domain.usecase.executor.assertAllSuccessful
import net.sfelabs.knox.core.domain.usecase.MainCoroutineRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class UseCaseBuilderTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var builder: UseCaseBuilder

    @Before
    fun setup() {
        builder = UseCaseBuilder()
    }

    @Test
    fun `sequential execution succeeds when all operations succeed`() = runTest {
        // Given
        val results = mutableListOf<Int>()

        // When
        val apiResults = builder.sequential {
            results.add(1)
            ApiResult.Success(1)
        }
            .add {
                results.add(2)
                ApiResult.Success(2)
            }
            .execute()

        // Then
        assertEquals(2, apiResults.size)
        assertTrue(apiResults.all { it is ApiResult.Success })
        assertEquals(listOf(1, 2), results)
    }

    @Test
    fun `sequential execution stops on first failure`() = runTest {
        // Given
        val results = mutableListOf<Int>()

        // When
        val apiResults = builder.sequential {
            results.add(1)
            ApiResult.Success(1)
        }
            .add {
                results.add(2)
                ApiResult.Error(DefaultApiError.UnexpectedError())
            }
            .add {
                results.add(3)
                ApiResult.Success(3)
            }
            .execute()

        // Then
        assertEquals(2, apiResults.size)
        assertEquals(listOf(1, 2), results)
        assertTrue(apiResults[0] is ApiResult.Success)
        assertTrue(apiResults[1] is ApiResult.Error)
    }

    @Test
    fun `parallel execution executes all operations regardless of failures`() = runTest {
        // Given
        val results = mutableListOf<Int>()

        // When
        val apiResults = builder.parallel {
            results.add(1)
            ApiResult.Success(1)
        }
            .add {
                results.add(2)
                ApiResult.Error(DefaultApiError.UnexpectedError())
            }
            .add {
                results.add(3)
                ApiResult.Success(3)
            }
            .execute()

        // Then
        assertEquals(3, apiResults.size)
        assertTrue(results.containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun `any execution stops on first success`() = runTest {
        // Given
        val results = mutableListOf<Int>()

        // When
        val apiResults = builder.any {
            results.add(1)
            ApiResult.Error(DefaultApiError.UnexpectedError())
        }
            .add {
                results.add(2)
                ApiResult.Success(2)
            }
            .add {
                results.add(3)
                ApiResult.Success(3)
            }
            .execute()

        // Then
        assertEquals(2, apiResults.size)
        assertEquals(listOf(1, 2), results)
        assertTrue(apiResults.last() is ApiResult.Success)
    }

    @Test
    fun `when predicate prevents single operation execution`() = runTest {
        // Given
        val results = mutableListOf<Int>()

        // When
        val apiResults = builder.sequential {
            results.add(1)
            ApiResult.Success(1)
        }
            .add {
                results.add(2)
                ApiResult.Success(2)
            }
            .`when` { false }  // This affects the operation that added 2
            .execute()

        // Then
        assertEquals(2, apiResults.size)
        assertEquals(listOf(1), results)  // Only 1 should be added, 2 was skipped
    }

    @Test
    fun `when predicate applies to immediate previous operation only`() = runTest {
        // Given
        val results = mutableListOf<Int>()

        // When
        val apiResults = builder.sequential {
            results.add(1)
            ApiResult.Success(1)
        }
            .add {
                results.add(2)
                ApiResult.Success(2)
            }
            .`when` { false }  // This affects only operation 2
            .add {
                results.add(3)
                ApiResult.Success(3)
            }
            .execute()

        // Then
        assertEquals(3, apiResults.size)
        assertEquals(listOf(1, 3), results)  // 2 was skipped, but 3 still executes
    }

    @Test
    fun `when predicate prevents operation execution`() = runTest {
        // Given
        val results = mutableListOf<Int>()

        // When
        val apiResults = builder.sequential {
            results.add(1)
            ApiResult.Success(1)
        }
            .add {
                results.add(2)
                ApiResult.Success(2)
            }
            .add {
                ApiResult.Success(3)
            }
            .`when` { false }
            .execute()

        // Then
        assertEquals(3, apiResults.size)
        assertTrue(apiResults[2] is ApiResult.Success)
        assertEquals(Unit, (apiResults[2] as ApiResult.Success).data)  // Predicate false returns Success(Unit)
        assertEquals(listOf(1, 2), results)
    }

    @Test
    fun `retry policy is applied correctly`() = runTest {
        // Given
        var attempts = 0

        // When
        val apiResults = builder.sequential {
            attempts++
            if (attempts < 3) {
                ApiResult.Error(DefaultApiError.UnexpectedError())
            } else {
                ApiResult.Success(attempts)
            }
        }
            .withRetry(maxAttempts = 3)
            .execute()

        // Then
        assertEquals(1, apiResults.size)
        assertTrue(apiResults[0] is ApiResult.Success)
        assertEquals(3, attempts)
    }

    @Test
    fun `fallback is executed on failure`() = runTest {
        // Given
        val results = mutableListOf<String>()

        // When
        val apiResults = builder.sequential {
            results.add("main")
            ApiResult.Error(DefaultApiError.UnexpectedError())
        }
            .withFallback {
                results.add("fallback")
                ApiResult.Success("fallback")
            }
            .execute()

        // Then
        assertEquals(1, apiResults.size)
        assertEquals(listOf("main", "fallback"), results)
        assertTrue(apiResults[0] is ApiResult.Success)
    }

    @Test
    fun `state tracking reports operation progress`() = runTest {
        // Given
        val states = mutableListOf<UseCaseBuilderState>()

        // When
        val apiResults = builder.sequential {
            ApiResult.Success(1)
        }
            .onStateChanged { state ->
                states.add(state)
            }
            .add {
                ApiResult.Success(2)
            }
            .execute()

        // Then
        assertTrue(apiResults.size == 2)
        assertTrue(states.isNotEmpty())
        assertTrue(states.last().executedOperations.size == 2)
        assertTrue(states.last().executedOperations.all { it.wasSuccessful })
    }

    @Test
    fun `state tracking captures skipped operations`() = runTest {
        // Given
        val states = mutableListOf<UseCaseBuilderState>()

        // When
        builder.sequential { ApiResult.Success(1) }
            .onStateChanged { state -> states.add(state) }
            .add { ApiResult.Success(2) }
            .`when` { false }
            .execute()

        // Then
        assertTrue(states.isNotEmpty())
        assertTrue(states.last().executedOperations.any { it.skipped })
    }

    @Test
    fun `state tracking captures failed operations`() = runTest {
        // Given
        val states = mutableListOf<UseCaseBuilderState>()

        // When
        builder.sequential { ApiResult.Success(1) }
            .onStateChanged { state -> states.add(state) }
            .add { ApiResult.Error(DefaultApiError.UnexpectedError()) }
            .execute()

        // Then
        assertTrue(states.isNotEmpty())
        assertTrue(states.last().executedOperations.any { !it.wasSuccessful })
    }

    @Test
    fun `state tracking captures fallback operations`() = runTest {
        // Given
        val states = mutableListOf<UseCaseBuilderState>()

        // When
        builder.sequential { ApiResult.Success(1) }
            .onStateChanged { state -> states.add(state) }
            .add { ApiResult.Error(DefaultApiError.UnexpectedError()) }
            .withFallback { ApiResult.Success(2) }
            .execute()

        // Then
        assertTrue(states.isNotEmpty())
        // Should see both the failed operation and the successful fallback
        assertTrue(states.last().executedOperations.size == 3)
    }

    @Test
    fun `state tracking in parallel execution`() = runTest {
        // Given
        val states = mutableListOf<UseCaseBuilderState>()
        // Parallel operations run on real dispatcher threads, so the counter must be atomic
        val executionCount = AtomicInteger(0)

        // When
        builder.parallel {
            executionCount.incrementAndGet()
            ApiResult.Success(1)
        }
            .onStateChanged { state ->
                println("State changed: operations=${state.executedOperations.size}")
                states.add(state)
            }
            .add {
                executionCount.incrementAndGet()
                ApiResult.Success(2)
            }
            .execute()

        // Then
        println("Execution count: ${executionCount.get()}")
        println("States size: ${states.size}")
        println("Last state operations: ${states.lastOrNull()?.executedOperations?.size}")

        assertEquals(2, executionCount.get())
        assertTrue("Should have received state updates", states.isNotEmpty())
        assertEquals("Should have tracked both operations",
            2, states.last().executedOperations.size)
        assertTrue("All operations should be successful",
            states.last().executedOperations.all { it.wasSuccessful })
    }

    @Test
    fun `timeout returns completed results plus trailing TimeoutError`() = runTest {
        // Given
        val results = mutableListOf<Int>()

        // When: the single operation delays past the timeout, so it never completes.
        val apiResults = builder.sequential {
            results.add(1)
            delay(100)
            ApiResult.Success(1)
        }
            .withTimeout(50.milliseconds)
            .withDispatcher(StandardTestDispatcher(testScheduler))
            .execute()

        // Then: no completed operation result, plus a trailing timeout error.
        assertEquals(1, apiResults.size)
        assertTrue(apiResults.last() is ApiResult.Error)
        assertTrue(
            (apiResults.last() as ApiResult.Error).apiError is DefaultApiError.TimeoutError
        )
        // The side effect before the delay ran, but the operation never produced a result.
        assertEquals(1, results.size)
        // An all-success check now correctly reports failure.
        assertFalse(apiResults.assertAllSuccessful())
    }

    @Test
    fun `timeout preserves already completed results before the trailing TimeoutError`() = runTest {
        // Given: first operation completes, second overruns the timeout.
        val apiResults = builder.sequential {
            ApiResult.Success("first")
        }
            .add {
                delay(1_000)
                ApiResult.Success("second")
            }
            .withTimeout(50.milliseconds)
            .withDispatcher(StandardTestDispatcher(testScheduler))
            .execute()

        // Then: the completed first result is preserved, then the timeout error is appended.
        assertEquals(2, apiResults.size)
        assertTrue(apiResults[0] is ApiResult.Success)
        assertEquals("first", (apiResults[0] as ApiResult.Success).data)
        assertTrue(apiResults[1] is ApiResult.Error)
        assertTrue(
            (apiResults[1] as ApiResult.Error).apiError is DefaultApiError.TimeoutError
        )
    }

    @Test
    fun `handles exceptions during execution`() = runTest {
        // When
        val apiResults = builder.sequential {
            throw IllegalStateException("Test exception")
            ApiResult.Success(1)
        }.execute()

        // Then
        assertEquals(1, apiResults.size)
        assertTrue(apiResults[0] is ApiResult.Error)
    }

    @Test
    fun `executes mixed operation chain`() = runTest {
        // Given
        val results = mutableListOf<Int>()

        // When
        val apiResults = builder.sequential {
            results.add(1)
            ApiResult.Success(1)
        }
            .then()
            .parallel()
            .add {
                results.add(2)
                ApiResult.Success(2)
            }
            .add {
                results.add(3)
                ApiResult.Success(3)
            }
            .then()
            .add {
                results.add(4)
                ApiResult.Success(4)
            }
            .execute()

        // Then
        assertEquals(4, apiResults.size)
        assertTrue(apiResults.all { it is ApiResult.Success })
    }

    @Test
    fun `handles retry with fallback`() = runTest {
        var attempts = 0

        val apiResults = builder.sequential {
            attempts++
            ApiResult.Error(DefaultApiError.UnexpectedError())
        }
            .withRetry(maxAttempts = 2)
            .withFallback {
                ApiResult.Success("fallback")
            }
            .execute()

        assertEquals(1, apiResults.size)
        assertEquals(2, attempts)
        assertTrue(apiResults[0] is ApiResult.Success)
    }

    @Test
    fun `handles not supported results`() = runTest {
        val apiResults = builder.sequential {
            ApiResult.NotSupported
        }
            .add {
                ApiResult.Success(1)  // Should not execute
            }
            .execute()

        assertEquals(1, apiResults.size)
        assertTrue(apiResults[0] is ApiResult.NotSupported)
    }

    @Test
    fun `execute throws IllegalStateException when the builder is reused`() = runTest {
        val configured = builder.sequential { ApiResult.Success(1) }
        configured.execute()

        try {
            configured.execute()
            fail("Expected IllegalStateException on second execute()")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Message should tell the developer to create a new builder",
                e.message!!.contains("already been executed")
            )
        }
    }

    @Test
    fun `predicate that throws is recorded as a mapped error result`() = runTest {
        // Given
        val apiResults = builder.sequential { ApiResult.Success(1) }
            .add { ApiResult.Success(2) }
            .`when` { throw IllegalStateException("predicate boom") }
            .execute()

        // Then: op1 succeeds, op2's throwing predicate becomes a mapped UnexpectedError.
        assertEquals(2, apiResults.size)
        assertTrue(apiResults[0] is ApiResult.Success)
        assertTrue(apiResults[1] is ApiResult.Error)
        assertTrue(
            (apiResults[1] as ApiResult.Error).apiError is DefaultApiError.UnexpectedError
        )
    }

    @Test
    fun `fallback that throws is recorded as a mapped error result`() = runTest {
        // Given
        val apiResults = builder.sequential {
            ApiResult.Error(DefaultApiError.UnexpectedError())
        }
            .withFallback { throw IllegalStateException("fallback boom") }
            .execute()

        // Then: the thrown fallback exception is mapped, not propagated.
        assertEquals(1, apiResults.size)
        assertTrue(apiResults[0] is ApiResult.Error)
        assertTrue(
            (apiResults[0] as ApiResult.Error).apiError is DefaultApiError.UnexpectedError
        )
    }

    @Test
    fun `NoSuchMethodError inside an operation surfaces as NotSupported`() = runTest {
        // Given: mirrors an absent Knox API on the current device.
        val apiResults = builder.sequential {
            throw NoSuchMethodError("Knox API not present on this build")
        }.execute()

        // Then: mapped identically to SuspendingUseCase - NotSupported.
        assertEquals(1, apiResults.size)
        assertTrue(apiResults[0] is ApiResult.NotSupported)
    }

    @Test
    fun `throwing errorHandler does not alter the operation outcome`() = runTest {
        // Given: the errorHandler throws, but the recorded result must remain the original error.
        val apiResults = builder.sequential {
            ApiResult.Error(DefaultApiError.InvalidInput("bad input"))
        }
            .onError { throw IllegalStateException("handler boom") }
            .execute()

        // Then
        assertEquals(1, apiResults.size)
        assertTrue(apiResults[0] is ApiResult.Error)
        assertTrue(
            (apiResults[0] as ApiResult.Error).apiError is DefaultApiError.InvalidInput
        )
    }

    @Test
    fun `labels are carried into executed operations including the fallback label`() = runTest {
        // Given
        val states = mutableListOf<UseCaseBuilderState>()

        // When
        builder.sequential(label = "Load Config") { ApiResult.Success(1) }
            .onStateChanged { states.add(it) }
            .add(label = "Apply Policy") { ApiResult.Error(DefaultApiError.UnexpectedError()) }
            .withFallback { ApiResult.Success(2) }
            .execute()

        // Then
        val labels = states.last().executedOperations.map { it.label }
        assertTrue("Expected primary label", labels.contains("Load Config"))
        assertTrue("Expected failing op label", labels.contains("Apply Policy"))
        assertTrue("Expected fallback label", labels.contains("Apply Policy (fallback)"))
    }

    @Test
    fun `withDispatcher StandardTestDispatcher runs deterministically under runTest`() = runTest {
        // Given: delayed operations whose virtual time is driven by runTest's scheduler.
        val order = mutableListOf<String>()

        // When
        val apiResults = builder.sequential {
            delay(200)
            order.add("first")
            ApiResult.Success("first")
        }
            .add {
                delay(100)
                order.add("second")
                ApiResult.Success("second")
            }
            .withDispatcher(StandardTestDispatcher(testScheduler))
            .execute()

        // Then: execution is controlled by runTest (no foreign Job re-parenting) and ordered.
        assertEquals(listOf("first", "second"), order)
        assertEquals(2, apiResults.size)
        assertTrue(apiResults.assertAllSuccessful())
    }

    @Test
    fun `failed parallel group stops the chain`() = runTest {
        // Given: run on the test dispatcher so the shared list is touched single-threaded.
        val ran = mutableListOf<String>()

        // When
        val apiResults = builder.parallel {
            ran.add("p1")
            ApiResult.Success(1)
        }
            .add {
                ran.add("p2")
                ApiResult.Error(DefaultApiError.UnexpectedError())
            }
            .then()
            .add {
                ran.add("after")
                ApiResult.Success("after")
            }
            .withDispatcher(StandardTestDispatcher(testScheduler))
            .execute()

        // Then: both parallel operations ran (no intra-group fail-fast), but the parallel
        // failure stopped the chain, so the following sequential operation never ran.
        assertTrue(ran.contains("p1"))
        assertTrue(ran.contains("p2"))
        assertFalse(ran.contains("after"))
        assertEquals(2, apiResults.size)
    }

    @Test
    fun `withRetry rejects non-positive maxAttempts at configuration time`() = runTest {
        try {
            builder.sequential { ApiResult.Success(1) }.withRetry(maxAttempts = 0)
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // configuration error surfaces at the withRetry call, not inside execute()
        }
    }

    @Test
    fun `retry does not re-attempt an absent API`() = runTest {
        // Given
        var attempts = 0

        // When
        val apiResults = builder.sequential {
            attempts++
            throw NoSuchMethodError("api absent from this SDK build")
        }
            .withRetry(maxAttempts = 3)
            .execute()

        // Then: NotSupported is final - retrying cannot make the API appear.
        assertEquals(1, attempts)
        assertEquals(1, apiResults.size)
        assertTrue(apiResults[0] is ApiResult.NotSupported)
    }
}