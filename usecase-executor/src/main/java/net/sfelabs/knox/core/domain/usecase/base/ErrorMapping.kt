package net.sfelabs.knox.core.domain.usecase.base

import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.domain.usecase.model.DefaultApiError

/**
 * Default mapping from thrown [Throwable]s to [ApiResult].
 *
 * Shared by [BaseUseCase] and [SuspendingUseCase] so the two base classes stay
 * consistent; individual use cases override `mapError` for API-specific handling.
 */
internal fun <R : Any> mapThrowableToApiResult(throwable: Throwable): ApiResult<R> = when (throwable) {
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
