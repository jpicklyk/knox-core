package net.sfelabs.knox.core.feature.api

import kotlinx.coroutines.test.runTest
import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.domain.usecase.model.DefaultApiError
import net.sfelabs.knox.core.feature.ui.model.ConfigurationOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPolicyTest {

    /** Scripted action: returns the queued results in order and counts invocations. */
    private class FakeActionPolicy(vararg results: ApiResult<Unit>) : ActionPolicy() {
        private val queue = ArrayDeque(results.toList())
        var executions = 0
            private set

        override suspend fun execute(): ApiResult<Unit> {
            executions++
            return queue.removeFirst()
        }
    }

    @Test
    fun `getState before any run returns default supported state without executing`() = runTest {
        val policy = FakeActionPolicy(ApiResult.Success(Unit))

        val state = policy.getState()

        assertEquals(ActionPolicyState(), state)
        assertTrue(state.isSupported)
        assertNull(state.lastRunSucceeded)
        assertEquals(0, policy.executions)
    }

    @Test
    fun `setState executes the action exactly once and returns its result`() = runTest {
        val policy = FakeActionPolicy(ApiResult.Success(Unit))

        val result = policy.setState(ActionPolicyState())

        assertTrue(result is ApiResult.Success)
        assertEquals(1, policy.executions)
    }

    @Test
    fun `successful run is reported by the next getState`() = runTest {
        val policy = FakeActionPolicy(ApiResult.Success(Unit))

        policy.setState(ActionPolicyState())
        val state = policy.getState()

        assertEquals(true, state.lastRunSucceeded)
        assertTrue(state.isSupported)
        assertNull(state.error)
        assertEquals("getState must not execute the action", 1, policy.executions)
    }

    @Test
    fun `NotSupported run marks the policy unsupported`() = runTest {
        val policy = FakeActionPolicy(ApiResult.NotSupported)

        val result = policy.setState(ActionPolicyState())
        val state = policy.getState()

        assertTrue(result is ApiResult.NotSupported)
        assertFalse(state.isSupported)
        assertEquals(false, state.lastRunSucceeded)
    }

    @Test
    fun `failed run carries the error into the state`() = runTest {
        val error = DefaultApiError.UnexpectedError("boom")
        val exception = IllegalStateException("boom")
        val policy = FakeActionPolicy(ApiResult.Error(error, exception))

        val result = policy.setState(ActionPolicyState())
        val state = policy.getState()

        assertTrue(result is ApiResult.Error)
        assertEquals(error, state.error)
        assertEquals(exception, state.exception)
        assertEquals(false, state.lastRunSucceeded)
        assertTrue("an error does not imply the API is missing", state.isSupported)
    }

    @Test
    fun `a later successful run clears an earlier failure`() = runTest {
        val policy = FakeActionPolicy(
            ApiResult.Error(DefaultApiError.UnexpectedError("first")),
            ApiResult.Success(Unit)
        )

        policy.setState(ActionPolicyState())
        policy.setState(ActionPolicyState())
        val state = policy.getState()

        assertEquals(true, state.lastRunSucceeded)
        assertNull(state.error)
        assertEquals(2, policy.executions)
    }

    @Test
    fun `state is always disabled and ignores withEnabled`() {
        val state = ActionPolicyState(lastRunSucceeded = true)

        assertFalse(state.isEnabled)
        assertEquals(state, state.withEnabled(true))
    }

    @Test
    fun `fromUiState ignores its inputs and exposes no configuration options`() {
        val policy = FakeActionPolicy()
        val options = listOf(ConfigurationOption.Toggle(key = "x", label = "X", isEnabled = true))

        assertEquals(ActionPolicyState(), policy.fromUiState(uiEnabled = true, options = options))
        assertEquals(ActionPolicyState(), policy.fromUiState(uiEnabled = false, options = emptyList()))
        assertTrue(policy.getConfigurationOptions(ActionPolicyState()).isEmpty())
    }
}
