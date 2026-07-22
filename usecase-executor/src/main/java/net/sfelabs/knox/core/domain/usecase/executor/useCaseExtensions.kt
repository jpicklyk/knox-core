package net.sfelabs.knox.core.domain.usecase.executor

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.sfelabs.knox.core.domain.usecase.base.mapThrowableToApiResult
import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.domain.usecase.model.DefaultApiError

/**
 * Extension functions for UseCaseBuilder result validation
 */
fun List<ApiResult<*>>.assertAllSuccessful(): Boolean {
    if (isEmpty()) return false
    return all { it is ApiResult.Success }
}

fun List<ApiResult<*>>.assertAnySuccessful(): Boolean {
    if (isEmpty()) return false
    return any { it is ApiResult.Success }
}

fun List<ApiResult<*>>.assertNoneSuccessful(): Boolean {
    if (isEmpty()) return false
    return none { it is ApiResult.Success }
}

fun List<ApiResult<*>>.assertAllFailed(): Boolean {
    if (isEmpty()) return false
    return all { it is ApiResult.Error }
}

fun List<ApiResult<*>>.assertAnyFailed(): Boolean {
    if (isEmpty()) return false
    return any { it is ApiResult.Error }
}

fun List<ApiResult<*>>.assertNotSupported(): Boolean {
    if (isEmpty()) return false
    return any { it is ApiResult.NotSupported }
}

@Deprecated(
    message = "Untyped/positional combine over a heterogeneous result list is error-prone. " +
        "For a fixed, small set of use cases use the typed parallelResults(...) combinators; " +
        "for dynamic policy sets use UseCaseBuilder and inspect the returned list directly."
)
@Suppress("UNCHECKED_CAST")
inline fun <reified T, R : Any> List<ApiResult<*>>.combine(transform: (List<T>) -> R): ApiResult<R> {
    if (any { it !is ApiResult.Success<*> }) {
        return firstOrNull { it is ApiResult.Error } as? ApiResult.Error
            ?: ApiResult.Error(DefaultApiError.UnexpectedError())
    }

    val results = map { (it as ApiResult.Success<*>).data }
    if (results.any { it !is T }) {
        return ApiResult.Error(DefaultApiError.UnexpectedError())
    }

    return ApiResult.Success(transform(results as List<T>))
}

// Guards a lambda like a use-case body: a thrown exception is cancellation-checked and
// mapped through the module's shared error mapping instead of escaping coroutineScope raw
// (which would also cancel the sibling operations and violate declaration-order semantics).
private suspend fun <T : Any> guarded(block: suspend () -> ApiResult<T>): ApiResult<T> =
    try {
        block()
    } catch (t: Throwable) {
        currentCoroutineContext().ensureActive()
        mapThrowableToApiResult(t)
    }

/**
 * Runs [first] and [second] concurrently in a [coroutineScope] (structured concurrency: they
 * inherit the calling coroutine's context and cancellation) and, when **both** succeed, combines
 * their values with [transform].
 *
 * If either fails, the **first** non-[ApiResult.Success] in declaration order is returned as-is:
 * an [ApiResult.NotSupported] propagates as `NotSupported`, an [ApiResult.Error] propagates with its
 * `apiError`/`exception` intact. Both lambdas always run to completion — a *returned* failure does
 * not cancel its sibling. A **thrown** exception (from a lambda or [transform]) is mapped through
 * the shared error mapping (`NoSuchMethodError` → `NotSupported`, `SecurityException` →
 * `PermissionError`), so nothing escapes as a raw throwable except coroutine cancellation.
 *
 * Use this for a **fixed, small** combination of use cases whose types are known at compile time.
 * For a dynamic, runtime-determined set of operations, use [UseCaseBuilder] instead.
 */
suspend fun <A : Any, B : Any, R : Any> parallelResults(
    first: suspend () -> ApiResult<A>,
    second: suspend () -> ApiResult<B>,
    transform: (A, B) -> R
): ApiResult<R> = coroutineScope {
    val deferredFirst = async { guarded(first) }
    val deferredSecond = async { guarded(second) }
    val resultFirst = deferredFirst.await()
    val resultSecond = deferredSecond.await()

    when (resultFirst) {
        is ApiResult.Error -> resultFirst
        ApiResult.NotSupported -> ApiResult.NotSupported
        is ApiResult.Success -> when (resultSecond) {
            is ApiResult.Error -> resultSecond
            ApiResult.NotSupported -> ApiResult.NotSupported
            is ApiResult.Success ->
                guarded { ApiResult.Success(transform(resultFirst.data, resultSecond.data)) }
        }
    }
}

/**
 * Arity-3 overload of [parallelResults]. Runs [first], [second], and [third] concurrently and, when
 * **all** succeed, combines their values with [transform]; otherwise returns the first
 * non-[ApiResult.Success] in declaration order.
 */
suspend fun <A : Any, B : Any, C : Any, R : Any> parallelResults(
    first: suspend () -> ApiResult<A>,
    second: suspend () -> ApiResult<B>,
    third: suspend () -> ApiResult<C>,
    transform: (A, B, C) -> R
): ApiResult<R> = coroutineScope {
    val deferredFirst = async { guarded(first) }
    val deferredSecond = async { guarded(second) }
    val deferredThird = async { guarded(third) }
    val resultFirst = deferredFirst.await()
    val resultSecond = deferredSecond.await()
    val resultThird = deferredThird.await()

    when (resultFirst) {
        is ApiResult.Error -> resultFirst
        ApiResult.NotSupported -> ApiResult.NotSupported
        is ApiResult.Success -> when (resultSecond) {
            is ApiResult.Error -> resultSecond
            ApiResult.NotSupported -> ApiResult.NotSupported
            is ApiResult.Success -> when (resultThird) {
                is ApiResult.Error -> resultThird
                ApiResult.NotSupported -> ApiResult.NotSupported
                is ApiResult.Success -> guarded {
                    ApiResult.Success(
                        transform(resultFirst.data, resultSecond.data, resultThird.data)
                    )
                }
            }
        }
    }
}