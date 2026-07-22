package net.sfelabs.knox.core.domain.usecase.executor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.sfelabs.knox.core.domain.usecase.base.mapThrowableToApiResult
import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.domain.usecase.model.DefaultApiError
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A DSL for enforcing a **dynamic, runtime-determined set of policy operations** — the shape an
 * MDM (or any policy-driven consumer) needs when it must run *many* [SuspendingUseCase] classes to
 * apply a compliance profile, with per-operation retry, fallback, and live progress reporting.
 *
 * The heterogeneous `List<ApiResult<*>>` return is intentional: the operation list is not known at
 * compile time, so results come back as a flat, order-preserving list you inspect afterwards
 * (see [assertAllSuccessful] and friends). For a **fixed, small** combination of use cases whose
 * types you know, prefer the typed combinators [parallelResults] instead — they return a single
 * strongly-typed `ApiResult<R>`.
 *
 * ### Example: an MDM enforcing a policy set
 * ```
 * val results = UseCaseBuilder()
 *     .parallel(label = "Disable Camera") { setCameraDisabledUseCase(true) }
 *     .add(label = "Disable Bluetooth") { setBluetoothDisabledUseCase(true) }
 *     .add(label = "Enforce Password") { enforcePasswordPolicyUseCase(profile) }
 *         .withRetry(maxAttempts = 3)
 *         .withFallback { queuePasswordPolicyUseCase(profile) }
 *     .withTimeout(30.seconds)
 *     .onStateChanged { state ->
 *         // Live progress for a UI, e.g. "Disable Camera: applied"
 *         state.executedOperations.forEach { op ->
 *             report(op.label ?: op.id, if (op.wasSuccessful) "applied" else "failed")
 *         }
 *     }
 *     .execute()
 *
 * if (results.assertAllSuccessful()) markCompliant() else markNonCompliant(results)
 * ```
 *
 * ### Grouping and chaining
 * Operations are grouped and chained with [then] (sequential), [parallel], and [any]; [add] appends
 * to the current group. Between groups the **chain** advances only while the preceding unit
 * succeeds (see failure semantics).
 *
 * ### Failure semantics (unified)
 * An executed unit whose results contain a non-[ApiResult.Success] stops execution of *subsequent*
 * units. Concretely:
 * - **Sequential** groups stop at the first failure internally, and a failed sequential group stops
 *   the chain.
 * - **Parallel** groups always run *all* their own operations to completion (no intra-group
 *   fail-fast), but a parallel group containing any non-success stops the chain.
 * - **Any** groups stop internally at the first success; they stop the chain only if *every*
 *   operation failed.
 *
 * ### Per-operation controls
 * [`when`]/[unless] gate an operation on a predicate (skipped operations record an
 * `ApiResult.Success(Unit)` and are marked `skipped`). [withFallback] runs a replacement when the
 * main operation does not succeed. [onError] observes an [ApiResult.Error]; a **throwing**
 * `errorHandler` is cancellation-checked and then suppressed — it never changes the outcome.
 * [withRetry] retries failures with exponential backoff.
 *
 * ### ApiResult contract
 * Every operation, predicate, fallback, and retry attempt is guarded: a thrown exception is mapped
 * through the shared error mapping (identical to [SuspendingUseCase]), so `NoSuchMethodError`
 * surfaces as [ApiResult.NotSupported] and `SecurityException` as a `PermissionError`. Nothing
 * escapes `execute()` as a raw throwable except coroutine cancellation.
 *
 * ### Dispatcher and lifecycle
 * `execute()` runs on [withDispatcher] (default [Dispatchers.IO]). It never adopts a foreign [Job]:
 * cancellation and lifecycle come from the coroutine that *calls* `execute()`. [withScope] is kept
 * for source compatibility but only extracts the scope's dispatcher — bind the builder to a
 * lifecycle by launching `execute()` inside that lifecycle's coroutine (e.g. `viewModelScope`).
 *
 * ### Timeout
 * [withTimeout] bounds the chain at suspension points: a blocking SDK call that never suspends
 * cannot be interrupted mid-call and will delay the return until it completes. On expiry,
 * `execute()` returns a snapshot of the results completed so far plus a trailing
 * `ApiResult.Error(TimeoutError)`, so all-success checks correctly report failure and partial
 * progress remains visible. Note on ordering: the list returned by `execute()` is in declaration
 * order, while progress states and the timeout partial snapshot list parallel results in
 * *completion* order — correlate operations by `label`, not position.
 *
 * ### Single use
 * A builder is single-use: a second call to `execute()` throws [IllegalStateException]. Create a new
 * [UseCaseBuilder] to run another chain.
 *
 * @see SuspendingUseCase
 * @see ApiResult
 * @see parallelResults
 */
class UseCaseBuilder {
    internal sealed class ExecutionUnit {
        data class SingleOperation(
            val execute: suspend () -> ApiResult<*>,
            val label: String? = null,
            val predicate: (suspend () -> Boolean)? = null,
            val fallback: (suspend () -> ApiResult<*>)? = null,
            val errorHandler: ((ApiResult.Error) -> Unit)? = null,
            val retryPolicy: Builder.RetryPolicy? = null
        ) : ExecutionUnit()

        data class OperationGroup(
            val operations: MutableList<SingleOperation> = mutableListOf(),
            val type: GroupType = GroupType.SEQUENTIAL
        ) : ExecutionUnit()
    }

    internal enum class GroupType {
        SEQUENTIAL,    // Stops internally at first failure; failed group stops the chain
        PARALLEL,      // Runs all operations; any failure stops the chain
        ANY            // First success wins; stops the chain only if all failed
    }

    interface UseCaseBuilderState {
        val executedOperations: List<ExecutedOperation>
        val currentResults: List<ApiResult<*>>
    }

    data class ExecutedOperation(
        val id: String,
        val label: String? = null,
        val timestamp: Long,
        val wasSuccessful: Boolean,
        val skipped: Boolean = false
    )

    class Builder {
        private var dispatcher: CoroutineDispatcher? = null
        private val executed = AtomicBoolean(false)
        private val executionUnits = mutableListOf<ExecutionUnit.OperationGroup>()
        private var currentGroup = ExecutionUnit.OperationGroup()
        private var timeoutDuration: Duration? = null
        private var retryPolicy: RetryPolicy? = null
        private var stateListener: ((UseCaseBuilderState) -> Unit)? = null

        // Guards results/executedOperations and serializes state-listener callbacks:
        // parallel groups mutate them from multiple coroutines on Dispatchers.IO.
        private val stateLock = Any()
        private val executedOperations = mutableListOf<ExecutedOperation>()
        private val results = mutableListOf<ApiResult<*>>()

        data class RetryPolicy(
            val maxAttempts: Int = 3,
            val initialDelay: Duration = 100.milliseconds,
            val maxDelay: Duration = 500.milliseconds,
            val factor: Double = 2.0,
            val predicate: (ApiResult.Error) -> Boolean = { true }
        ) {
            init {
                require(maxAttempts > 0) { "maxAttempts must be positive, was $maxAttempts" }
            }
        }

        fun onStateChanged(listener: (UseCaseBuilderState) -> Unit): Builder {
            stateListener = listener
            return this
        }

        private fun notifyStateChanged() {
            val listener = stateListener ?: return
            synchronized(stateLock) {
                val currentExecutedOperations = executedOperations.toList()
                val currentResults = results.toList()

                val state = object : UseCaseBuilderState {
                    override val executedOperations: List<ExecutedOperation> = currentExecutedOperations
                    override val currentResults: List<ApiResult<*>> = currentResults
                }
                listener.invoke(state)
            }
        }

        /**
         * Appends an operation to the current group.
         *
         * @param label optional human-readable name surfaced in [ExecutedOperation.label] for
         *   progress reporting (e.g. an MDM showing "Disable Camera: applied").
         * @param operation the use-case invocation returning an [ApiResult].
         */
        fun add(label: String? = null, operation: suspend () -> ApiResult<*>): Builder {
            currentGroup.operations.add(
                ExecutionUnit.SingleOperation(execute = operation, label = label)
            )
            return this
        }

        fun `when`(predicate: suspend () -> Boolean): Builder {
            val lastOp = currentGroup.operations.lastOrNull()
                ?: throw IllegalStateException("No operation to apply predicate to")
            currentGroup.operations[currentGroup.operations.lastIndex] =
                lastOp.copy(predicate = predicate)
            return this
        }

        fun unless(predicate: suspend () -> Boolean): Builder {
            return `when` { !predicate() }
        }

        fun withFallback(fallback: suspend () -> ApiResult<*>): Builder {
            val lastOp = currentGroup.operations.lastOrNull()
                ?: throw IllegalStateException("No operation to apply fallback to")
            currentGroup.operations[currentGroup.operations.lastIndex] =
                lastOp.copy(fallback = fallback)
            return this
        }

        fun withTimeout(duration: Duration): Builder {
            timeoutDuration = duration
            return this
        }

        fun withRetry(
            maxAttempts: Int = 3,
            initialDelay: Duration = 100.milliseconds,
            maxDelay: Duration = 500.milliseconds,
            factor: Double = 2.0,
            predicate: (ApiResult.Error) -> Boolean = { true }
        ): Builder {
            retryPolicy = RetryPolicy(maxAttempts, initialDelay, maxDelay, factor, predicate)
            return this
        }

        fun onError(handler: (ApiResult.Error) -> Unit): Builder {
            val lastOp = currentGroup.operations.lastOrNull()
                ?: throw IllegalStateException("No operation to apply error handler to")
            currentGroup.operations[currentGroup.operations.lastIndex] =
                lastOp.copy(errorHandler = handler)
            return this
        }

        fun any(): Builder {
            if (currentGroup.operations.isNotEmpty()) {
                executionUnits.add(currentGroup)
            }
            currentGroup = ExecutionUnit.OperationGroup(type = GroupType.ANY)
            return this
        }

        fun parallel(): Builder {
            if (currentGroup.operations.isNotEmpty()) {
                executionUnits.add(currentGroup)
            }
            currentGroup = ExecutionUnit.OperationGroup(type = GroupType.PARALLEL)
            return this
        }

        fun then(): Builder {
            if (currentGroup.operations.isNotEmpty()) {
                executionUnits.add(currentGroup)
            }
            currentGroup = ExecutionUnit.OperationGroup(type = GroupType.SEQUENTIAL)
            return this
        }

        /**
         * Runs `execute()` on the given [dispatcher] (default [Dispatchers.IO]). Preferred over
         * [withScope]: lifecycle/cancellation come from the coroutine that calls `execute()`, not
         * from a foreign scope's [kotlinx.coroutines.Job].
         */
        fun withDispatcher(dispatcher: CoroutineDispatcher): Builder {
            this.dispatcher = dispatcher
            return this
        }

        /**
         * Kept for source compatibility. Only the scope's [CoroutineDispatcher] is extracted; the
         * scope's [kotlinx.coroutines.Job] is deliberately **not** adopted. To bind execution to a
         * lifecycle, launch `execute()` inside that lifecycle's coroutine instead.
         */
        fun withScope(scope: CoroutineScope): Builder {
            this.dispatcher = scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher
            return this
        }

        suspend fun execute(): List<ApiResult<*>> {
            check(executed.compareAndSet(false, true)) {
                "This UseCaseBuilder has already been executed. Create a new UseCaseBuilder to run another chain."
            }

            if (currentGroup.operations.isNotEmpty()) {
                executionUnits.add(currentGroup)
            }

            val resolvedDispatcher = dispatcher ?: Dispatchers.IO

            return withContext(resolvedDispatcher) {
                try {
                    withTimeoutOrNull(timeoutDuration?.inWholeMilliseconds ?: Long.MAX_VALUE) {
                        executeOperations()
                    } ?: run {
                        // On timeout: return what completed plus a trailing TimeoutError so
                        // all-success checks fail and partial progress stays visible.
                        val completed = synchronized(stateLock) { results.toList() }
                        completed + ApiResult.Error(
                            DefaultApiError.TimeoutError("Execution timed out after $timeoutDuration")
                        )
                    }
                } finally {
                    cleanup()
                }
            }
        }

        private suspend fun executeSingleOperation(
            operation: ExecutionUnit.SingleOperation
        ): ApiResult<*> {
            // Records the result and returns its index in [results] so the fallback
            // path can replace the correct slot even when other operations append
            // concurrently from a parallel group.
            fun recordResult(result: ApiResult<*>, skipped: Boolean): Int {
                val index = synchronized(stateLock) {
                    results.add(result)
                    executedOperations.add(ExecutedOperation(
                        id = operation.hashCode().toString(),
                        label = operation.label,
                        timestamp = System.currentTimeMillis(),
                        wasSuccessful = result is ApiResult.Success,
                        skipped = skipped
                    ))
                    results.lastIndex
                }
                notifyStateChanged()
                return index
            }

            // Check predicate if it exists. A throwing predicate is cancellation-checked and then
            // recorded as a mapped error result (the ApiResult contract must hold).
            val predicate = operation.predicate
            if (predicate != null) {
                val shouldExecute = try {
                    predicate()
                } catch (e: Throwable) {
                    currentCoroutineContext().ensureActive()
                    val mapped = mapThrowableToApiResult<Any>(e)
                    recordResult(mapped, skipped = false)
                    return mapped
                }
                if (!shouldExecute) {
                    val result = ApiResult.Success(Unit)
                    recordResult(result, skipped = true)
                    return result
                }
            }

            val result = executeWithRetry(operation)
            val resultIndex = recordResult(result, skipped = false)

            // Observe errors if a handler exists. A throwing errorHandler is cancellation-checked
            // and then suppressed: side effects must never alter the operation outcome.
            if (result is ApiResult.Error) {
                try {
                    operation.errorHandler?.invoke(result)
                } catch (e: Throwable) {
                    currentCoroutineContext().ensureActive()
                    // Suppressed intentionally.
                }
            }

            // Try fallback if the main operation did not succeed. A throwing fallback is
            // cancellation-checked and then recorded as a mapped error result.
            return when {
                result !is ApiResult.Success && operation.fallback != null -> {
                    val fallbackResult = try {
                        operation.fallback.invoke()
                    } catch (e: Throwable) {
                        currentCoroutineContext().ensureActive()
                        mapThrowableToApiResult<Any>(e)
                    }
                    synchronized(stateLock) {
                        executedOperations.add(ExecutedOperation(
                            id = "${operation.hashCode()}_fallback",
                            label = operation.label?.let { "$it (fallback)" },
                            timestamp = System.currentTimeMillis(),
                            wasSuccessful = fallbackResult is ApiResult.Success,
                            skipped = false
                        ))
                        results[resultIndex] = fallbackResult  // Replace this operation's result
                    }
                    notifyStateChanged()
                    fallbackResult
                }
                else -> result
            }
        }

        private suspend fun executeWithRetry(
            operation: ExecutionUnit.SingleOperation
        ): ApiResult<*> {
            val policy = operation.retryPolicy ?: retryPolicy ?: return try {
                operation.execute()
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive()
                mapThrowableToApiResult<Any>(e)
            }

            var currentDelay = policy.initialDelay
            repeat(policy.maxAttempts) { attempt ->
                try {
                    val result = operation.execute()
                    when (result) {
                        is ApiResult.Success -> return result
                        is ApiResult.Error -> {
                            if (!policy.predicate(result) || attempt == policy.maxAttempts - 1) return result
                            currentCoroutineContext().ensureActive()
                            delay(currentDelay.inWholeMilliseconds)
                            currentDelay = (currentDelay.inWholeMilliseconds * policy.factor)
                                .milliseconds
                                .coerceAtMost(policy.maxDelay)
                        }
                        is ApiResult.NotSupported -> return result
                    }
                } catch (e: Throwable) {
                    currentCoroutineContext().ensureActive()
                    val mapped = mapThrowableToApiResult<Any>(e)
                    // An absent API will not appear on a later attempt - surface NotSupported now.
                    if (mapped is ApiResult.NotSupported || attempt == policy.maxAttempts - 1) {
                        return mapped
                    }
                    delay(currentDelay.inWholeMilliseconds)
                    currentDelay = (currentDelay.inWholeMilliseconds * policy.factor)
                        .milliseconds
                        .coerceAtMost(policy.maxDelay)
                }
            }
            error("unreachable: the final retry attempt always returns")
        }

        private suspend fun executeSequential(
            operations: List<ExecutionUnit.SingleOperation>
        ): List<ApiResult<*>> {
            val results = mutableListOf<ApiResult<*>>()
            for (operation in operations) {
                results.add(executeSingleOperation(operation))
                if (results.last() !is ApiResult.Success) break
            }
            return results
        }

        private suspend fun executeParallel(
            operations: List<ExecutionUnit.SingleOperation>
        ): List<ApiResult<*>> = coroutineScope {
            // No intra-group fail-fast: every operation runs to completion.
            val results = operations.map { operation ->
                async { executeSingleOperation(operation) }
            }.awaitAll()

            // Ensure we have a final state update with all operations
            notifyStateChanged()

            results
        }

        private suspend fun executeAny(
            operations: List<ExecutionUnit.SingleOperation>
        ): List<ApiResult<*>> {
            val results = mutableListOf<ApiResult<*>>()

            for (operation in operations) {
                val result = executeSingleOperation(operation)
                results.add(result)
                if (result is ApiResult.Success) break
            }

            return results
        }

        private suspend fun executeOperations(): List<ApiResult<*>> {
            val results = mutableListOf<ApiResult<*>>()

            for (group in executionUnits) {
                val groupResults = when (group.type) {
                    GroupType.SEQUENTIAL -> executeSequential(group.operations)
                    GroupType.PARALLEL -> executeParallel(group.operations)
                    GroupType.ANY -> executeAny(group.operations)
                }
                results.addAll(groupResults)

                // Unified chain-stop: a non-success stops subsequent units. ANY groups are
                // the exception - they only stop the chain when every operation failed.
                val stopChain = when (group.type) {
                    GroupType.ANY -> groupResults.none { it is ApiResult.Success }
                    else -> groupResults.any { it !is ApiResult.Success }
                }
                if (stopChain) break
            }

            return results
        }

        private fun cleanup() {
            synchronized(stateLock) {
                executedOperations.clear()
                results.clear()
            }
            stateListener = null
            dispatcher = null
        }
    }

    fun sequential(label: String? = null, operation: suspend () -> ApiResult<*>): Builder {
        return Builder().add(label, operation)
    }

    fun parallel(label: String? = null, operation: suspend () -> ApiResult<*>): Builder {
        return Builder().parallel().add(label, operation)
    }

    fun any(label: String? = null, operation: suspend () -> ApiResult<*>): Builder {
        return Builder().any().add(label, operation)
    }
}
