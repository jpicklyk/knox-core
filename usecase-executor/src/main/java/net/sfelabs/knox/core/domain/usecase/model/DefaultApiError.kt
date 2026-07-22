package net.sfelabs.knox.core.domain.usecase.model

sealed interface ApiError {
    val message: String
}

sealed class DefaultApiError : ApiError {
    data class PermissionError(override val message: String) : DefaultApiError()
    data class TimeoutError(override val message: String) : DefaultApiError()
    data class InvalidInput(override val message: String) : DefaultApiError()

    /** The operation was rejected because a device policy restricts it. */
    data class PolicyRestricted(override val message: String) : DefaultApiError()
    data class UnexpectedError(
        override val message: String = "An unexpected error occurred"
    ) : DefaultApiError()
}