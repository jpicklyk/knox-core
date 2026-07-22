package net.sfelabs.knox.core.domain.usecase

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.sfelabs.knox.core.domain.usecase.executor.assertAllFailed
import net.sfelabs.knox.core.domain.usecase.executor.assertAllSuccessful
import net.sfelabs.knox.core.domain.usecase.executor.assertAnyFailed
import net.sfelabs.knox.core.domain.usecase.executor.assertAnySuccessful
import net.sfelabs.knox.core.domain.usecase.executor.assertNoneSuccessful
import net.sfelabs.knox.core.domain.usecase.executor.assertNotSupported
import net.sfelabs.knox.core.domain.usecase.executor.parallelResults
import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.domain.usecase.model.DefaultApiError
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UseCaseExtensionsTest {

    @Test
    fun `assertAllSuccessful returns true when all results are successful`() {
        val results = listOf(
            ApiResult.Success(1),
            ApiResult.Success("test"),
            ApiResult.Success(true)
        )

        assertTrue(results.assertAllSuccessful())
    }

    @Test
    fun `assertAllSuccessful returns false when any result is not successful`() {
        val results = listOf(
            ApiResult.Success(1),
            ApiResult.Error(DefaultApiError.UnexpectedError()),
            ApiResult.Success(true)
        )

        assertFalse(results.assertAllSuccessful())
    }

    @Test
    fun `assertAllSuccessful returns false for empty list`() {
        val results = emptyList<ApiResult<*>>()

        assertFalse(results.assertAllSuccessful())
    }

    @Test
    fun `assertAnySuccessful returns true when at least one result is successful`() {
        val results = listOf(
            ApiResult.Error(DefaultApiError.UnexpectedError()),
            ApiResult.Success("test"),
            ApiResult.Error(DefaultApiError.UnexpectedError())
        )

        assertTrue(results.assertAnySuccessful())
    }

    @Test
    fun `assertAnySuccessful returns false when no results are successful`() {
        val results = listOf(
            ApiResult.Error(DefaultApiError.UnexpectedError()),
            ApiResult.Error(DefaultApiError.UnexpectedError()),
            ApiResult.NotSupported
        )

        assertFalse(results.assertAnySuccessful())
    }

    @Test
    fun `assertNoneSuccessful returns true when no results are successful`() {
        val results = listOf(
            ApiResult.Error(DefaultApiError.UnexpectedError()),
            ApiResult.NotSupported,
            ApiResult.Error(DefaultApiError.UnexpectedError())
        )

        assertTrue(results.assertNoneSuccessful())
    }

    @Test
    fun `assertNoneSuccessful returns false when any result is successful`() {
        val results = listOf(
            ApiResult.Error(DefaultApiError.UnexpectedError()),
            ApiResult.Success(1),
            ApiResult.Error(DefaultApiError.UnexpectedError())
        )

        assertFalse(results.assertNoneSuccessful())
    }

    @Test
    fun `assertAllFailed returns true when all results are errors`() {
        val results = listOf(
            ApiResult.Error(DefaultApiError.UnexpectedError()),
            ApiResult.Error(DefaultApiError.UnexpectedError()),
            ApiResult.Error(DefaultApiError.UnexpectedError())
        )

        assertTrue(results.assertAllFailed())
    }

    @Test
    fun `assertAllFailed returns false when any result is not an error`() {
        val results = listOf(
            ApiResult.Error(DefaultApiError.UnexpectedError()),
            ApiResult.Success(1),
            ApiResult.Error(DefaultApiError.UnexpectedError())
        )

        assertFalse(results.assertAllFailed())
    }

    @Test
    fun `assertAnyFailed returns true when at least one result is an error`() {
        val results = listOf(
            ApiResult.Success(1),
            ApiResult.Error(DefaultApiError.UnexpectedError()),
            ApiResult.Success(2)
        )

        assertTrue(results.assertAnyFailed())
    }

    @Test
    fun `assertAnyFailed returns false when no results are errors`() {
        val results = listOf(
            ApiResult.Success(1),
            ApiResult.Success(2),
            ApiResult.NotSupported
        )

        assertFalse(results.assertAnyFailed())
    }

    @Test
    fun `assertNotSupported returns true when any result is NotSupported`() {
        val results = listOf(
            ApiResult.Success(1),
            ApiResult.NotSupported,
            ApiResult.Error(DefaultApiError.UnexpectedError())
        )

        assertTrue(results.assertNotSupported())
    }

    @Test
    fun `assertNotSupported returns false when no results are NotSupported`() {
        val results = listOf(
            ApiResult.Success(1),
            ApiResult.Error(DefaultApiError.UnexpectedError()),
            ApiResult.Success(2)
        )

        assertFalse(results.assertNotSupported())
    }

    @Test
    fun `empty list returns false for all assertions`() {
        val results = emptyList<ApiResult<*>>()

        assertFalse(results.assertAllSuccessful())
        assertFalse(results.assertAnySuccessful())
        assertFalse(results.assertNoneSuccessful())
        assertFalse(results.assertAllFailed())
        assertFalse(results.assertAnyFailed())
        assertFalse(results.assertNotSupported())
    }

    @Test
    fun `parallelResults combines both values when both succeed`() = runTest {
        val result = parallelResults(
            first = { ApiResult.Success(2) },
            second = { ApiResult.Success("x") }
        ) { a, b -> "$b$a" }

        assertTrue(result is ApiResult.Success)
        assertEquals("x2", (result as ApiResult.Success).data)
    }

    @Test
    fun `parallelResults returns the first error in declaration order`() = runTest {
        val firstError = DefaultApiError.InvalidInput("first failed")
        val secondError = DefaultApiError.PermissionError("second failed")

        val result = parallelResults(
            first = { ApiResult.Error(firstError) },
            second = { ApiResult.Error(secondError) }
        ) { a: Int, b: Int -> a + b }

        assertTrue(result is ApiResult.Error)
        // First non-success in declaration order wins, with its apiError intact.
        assertEquals(firstError, (result as ApiResult.Error).apiError)
    }

    @Test
    fun `parallelResults propagates error from the second operation when the first succeeds`() = runTest {
        val secondError = DefaultApiError.PermissionError("second failed")

        val result = parallelResults(
            first = { ApiResult.Success(1) },
            second = { ApiResult.Error(secondError) }
        ) { a: Int, b: Int -> a + b }

        assertTrue(result is ApiResult.Error)
        assertEquals(secondError, (result as ApiResult.Error).apiError)
    }

    @Test
    fun `parallelResults propagates NotSupported`() = runTest {
        val result = parallelResults(
            first = { ApiResult.Success(1) },
            second = { ApiResult.NotSupported }
        ) { a: Int, b: Int -> a + b }

        assertTrue(result is ApiResult.NotSupported)
    }

    @Test
    fun `parallelResults returns NotSupported before a later error in declaration order`() = runTest {
        val result = parallelResults(
            first = { ApiResult.NotSupported },
            second = { ApiResult.Error(DefaultApiError.UnexpectedError()) }
        ) { a: Int, b: Int -> a + b }

        // First operation's NotSupported takes precedence over the second's error.
        assertTrue(result is ApiResult.NotSupported)
    }

    @Test
    fun `parallelResults arity-3 combines all three values when all succeed`() = runTest {
        val result = parallelResults(
            first = { ApiResult.Success(1) },
            second = { ApiResult.Success(2) },
            third = { ApiResult.Success(3) }
        ) { a, b, c -> a + b + c }

        assertTrue(result is ApiResult.Success)
        assertEquals(6, (result as ApiResult.Success).data)
    }

    @Test
    fun `parallelResults maps a thrown NoSuchMethodError to NotSupported`() = runTest {
        val result = parallelResults<Int, Int, Int>(
            first = { throw NoSuchMethodError("api absent from this SDK build") },
            second = { ApiResult.Success(2) }
        ) { a, b -> a + b }

        // A thrown absent-API error must surface as NotSupported, not escape raw.
        assertTrue(result is ApiResult.NotSupported)
    }

    @Test
    fun `parallelResults returns first declared failure even when the sibling throws`() = runTest {
        val result = parallelResults<Int, Int, Int>(
            first = { ApiResult.Error(DefaultApiError.InvalidInput("bad input")) },
            second = { throw IllegalStateException("boom") }
        ) { a, b -> a + b }

        // Declaration order wins: first's typed Error, not second's mapped exception.
        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).apiError is DefaultApiError.InvalidInput)
    }

    @Test
    fun `parallelResults maps a throwing transform instead of escaping raw`() = runTest {
        val result = parallelResults(
            first = { ApiResult.Success(1) },
            second = { ApiResult.Success(2) }
        ) { _: Int, _: Int -> throw IllegalStateException("transform failed") }

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).apiError is DefaultApiError.UnexpectedError)
    }
}