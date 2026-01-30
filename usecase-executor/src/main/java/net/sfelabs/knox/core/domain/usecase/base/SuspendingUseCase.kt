package net.sfelabs.knox.core.domain.usecase.base

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.domain.usecase.model.DefaultApiError

/**
 * An abstract implementation of [UseCase] that provides coroutine context switching and error handling.
 *
 * This is the primary base class for implementing use cases. It automatically:
 * - Switches to the IO dispatcher (or a custom dispatcher)
 * - Catches and maps exceptions to [ApiResult.Error]
 * - Handles cancellation properly
 *
 * ## Implementation Examples
 *
 * ### No Parameters (Unit)
 * ```kotlin
 * class GetCurrentUserUseCase : SuspendingUseCase<Unit, User>() {
 *     override suspend fun execute(params: Unit): ApiResult<User> {
 *         return ApiResult.Success(userRepository.getCurrentUser())
 *     }
 * }
 * ```
 *
 * ### Single Primitive Parameter
 * ```kotlin
 * class SetVolumeUseCase : SuspendingUseCase<Int, Unit>() {
 *     override suspend fun execute(params: Int): ApiResult<Unit> {
 *         audioManager.setVolume(params)
 *         return ApiResult.Success(Unit)
 *     }
 * }
 * ```
 *
 * ### Single DTO Parameter
 * ```kotlin
 * class UpdateUserUseCase : SuspendingUseCase<UserDto, Unit>() {
 *     override suspend fun execute(params: UserDto): ApiResult<Unit> {
 *         userRepository.update(params)
 *         return ApiResult.Success(Unit)
 *     }
 * }
 * ```
 *
 * ### Multiple Parameters (Nested Params Class)
 * ```kotlin
 * class LoginUseCase : SuspendingUseCase<LoginUseCase.Params, AuthToken>() {
 *     data class Params(val username: String, val password: String)
 *
 *     override suspend fun execute(params: Params): ApiResult<AuthToken> {
 *         val token = authService.login(params.username, params.password)
 *         return ApiResult.Success(token)
 *     }
 * }
 * ```
 *
 * ## Parameter Naming Convention
 *
 * Always name the parameter `params` in the [execute] method to match the base class signature
 * and avoid Kotlin compiler warnings about named argument mismatches.
 *
 * @param P The type of input parameters for the use case. Use [Unit] if no parameters are required.
 * @param R The type of the result returned by the use case.
 * @param dispatcher The coroutine dispatcher to use for this specific use case. Defaults to [Dispatchers.IO].
 *
 * @see UseCase for parameter type guidelines
 */
abstract class SuspendingUseCase<in P, out R : Any>(
    private val dispatcher: CoroutineDispatcher? = null
) : UseCase<P, R> {
    private val defaultDispatcher = Dispatchers.IO

    /**
     * Executes the use case with error handling and context switching.
     *
     * @param params The input parameters for the use case.
     * @return An [ApiResult] representing the result of the operation.
     */
    final override suspend operator fun invoke(params: P): ApiResult<R> =
        withContext(
            dispatcher ?: defaultDispatcher
        ) {
            try {
                execute(params)
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive()
                mapError(e)
            }
        }

    /**
     * Implements the core logic of the use case.
     * This method should be implemented by subclasses to define the specific behavior of the use case.
     *
     * @param params The input parameters for the use case.
     * @return An [ApiResult] representing the result of the operation.
     */
    @Suppress("UNCHECKED_CAST")
    protected abstract suspend fun execute(params: P = Unit as P): ApiResult<R>

    /**
     * Maps exceptions to appropriate [ApiResult.Error] instances.
     * This method can be overridden in subclasses to provide custom error mapping.
     *
     * @param throwable The throwable to be mapped.
     * @return An [ApiResult] representing the error state.
     */
    protected open fun mapError(throwable: Throwable): ApiResult<R> = when (throwable) {
        is NoSuchMethodError -> ApiResult.NotSupported
        is SecurityException -> ApiResult.Error(
            apiError = DefaultApiError.PermissionError("Permission error: ${throwable.message}"),
            exception = throwable
        )
        else -> ApiResult.Error(
            apiError = DefaultApiError.UnexpectedError(),
            exception = Exception(throwable)
        )
    }
}