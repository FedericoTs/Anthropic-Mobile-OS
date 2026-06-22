package org.agentnativeos.core.model

import org.agentnativeos.core.action.AgentAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeModelProviderTest {

    private fun providerReturning(response: HttpResponse): ClaudeModelProvider {
        val transport = HttpTransport { _, _, _ -> response }
        return ClaudeModelProvider(AnthropicClient(AuthMode.ApiKey("k"), transport = transport))
    }

    private fun ctx() = PlanningContext(intent = "open battery", untrustedScreen = "[button] Battery", stepIndex = 0)

    @Test
    fun parsesModelReplyIntoTypedAction() {
        val provider = providerReturning(
            HttpResponse(200, """{"content":[{"type":"text","text":"{\"action\":\"done\",\"summary\":\"ok\"}"}]}"""),
        )
        assertEquals(AgentAction.Done("ok"), provider.nextAction(ctx()))
    }

    @Test
    fun abortsOnHttpError() {
        val action = providerReturning(HttpResponse(500, "boom")).nextAction(ctx())
        assertTrue("expected Abort, got $action", action is AgentAction.Abort)
    }

    @Test
    fun abortsOnUnparseableReply() {
        val action = providerReturning(
            HttpResponse(200, """{"content":[{"type":"text","text":"sorry, no idea"}]}"""),
        ).nextAction(ctx())
        assertTrue("expected Abort, got $action", action is AgentAction.Abort)
    }
}
