package net.sfelabs.knox.core.domain.usecase.base

import net.sfelabs.knox.core.domain.usecase.model.ApiResult

/**
 * Represents a use case for API operations.
 *
 * ## Parameter Type Guidelines
 *
 * Choose the parameter type `P` based on the number of inputs required:
 *
 * ### No Parameters
 * Use [Unit] when no input is needed:
 * ```kotlin
 * class GetBrightnessUseCase : SuspendingUseCase<Unit, Int>() {
 *     override suspend fun execute(params: Unit): ApiResult<Int> {
 *         return ApiResult.Success(settings.brightness)
 *     }
 * }
 * // Usage: getBrightnessUseCase()
 * ```
 *
 * ### Single Parameter
 * Use the type directly (Boolean, Int, String, or a DTO):
 * ```kotlin
 * class SetBrightnessEnabledUseCase : SuspendingUseCase<Boolean, Unit>() {
 *     override suspend fun execute(params: Boolean): ApiResult<Unit> {
 *         settings.setBrightnessEnabled(params)
 *         return ApiResult.Success(Unit)
 *     }
 * }
 * // Usage: setBrightnessEnabledUseCase(true)
 * ```
 *
 * ### Multiple Parameters
 * Use a nested data class when 2+ related parameters are needed:
 * ```kotlin
 * class ConfigureNetworkUseCase : SuspendingUseCase<ConfigureNetworkUseCase.Params, Unit>() {
 *     data class Params(val ssid: String, val password: String, val useDhcp: Boolean)
 *
 *     override suspend fun execute(params: Params): ApiResult<Unit> {
 *         network.configure(params.ssid, params.password, params.useDhcp)
 *         return ApiResult.Success(Unit)
 *     }
 * }
 * // Usage: configureNetworkUseCase(Params("MyNetwork", "password", true))
 * ```
 *
 * @param P The type of input parameters for the use case. Use [Unit] if no parameters are required.
 * @param R The type of the result returned by the use case.
 */
interface UseCase<in P, out R : Any> {
    /**
     * Executes the use case.
     *
     * @param params The input parameters for the use case.
     * @return An [ApiResult] representing the result of the operation.
     */
    @Suppress("UNCHECKED_CAST")
    suspend operator fun invoke(params: P = Unit as P): ApiResult<R>
}