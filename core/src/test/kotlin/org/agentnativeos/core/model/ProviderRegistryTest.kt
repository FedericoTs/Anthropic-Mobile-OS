package org.agentnativeos.core.model

import org.agentnativeos.core.action.AgentAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    private fun provider() = ScriptedModelProvider(listOf(AgentAction.Done("x")))

    @Test
    fun swapTakesEffectAtNextTask_notMidTask() {
        val p1 = provider()
        val reg = ProviderRegistry(p1, ModelId("m1"))
        val p2 = provider()
        reg.requestSwap { p2 to ModelId("m2") }

        // Mid-task: still on the old provider until the task boundary.
        assertSame(p1, reg.current)
        assertEquals(ModelId("m1"), reg.activeModel)
        assertTrue(reg.hasPendingSwap)

        val outcome = reg.beginTask()
        assertTrue(outcome is ProviderRegistry.SwapOutcome.Swapped)
        assertSame(p2, reg.current)
        assertEquals(ModelId("m2"), reg.activeModel)
        assertFalse(reg.hasPendingSwap)
    }

    @Test
    fun swapFailureIsNonFatal_keepsCurrentProvider() {
        val p1 = provider()
        val reg = ProviderRegistry(p1, ModelId("m1"))
        reg.requestSwap { throw IllegalStateException("OAuth token expired") }

        val outcome = reg.beginTask()
        assertTrue(outcome is ProviderRegistry.SwapOutcome.Failed)
        assertEquals("OAuth token expired", (outcome as ProviderRegistry.SwapOutcome.Failed).reason)
        // Current provider and model are unchanged — a live run never hard-fails.
        assertSame(p1, reg.current)
        assertEquals(ModelId("m1"), reg.activeModel)
    }

    @Test
    fun beginTaskWithoutPendingSwapIsNoChange() {
        val reg = ProviderRegistry(provider(), ModelId("m1"))
        assertTrue(reg.beginTask() is ProviderRegistry.SwapOutcome.NoChange)
    }
}
