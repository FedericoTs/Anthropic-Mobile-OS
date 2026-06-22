package os.amos.shell.capability

import android.content.Context
import org.json.JSONObject
import os.amos.shell.ai.ToolCall
import os.amos.shell.ai.ToolSpec

/** Outcome of running a capability, surfaced to the agent and the Trust Center. */
data class CapabilityResult(
    val ok: Boolean,
    /** Short, model-readable summary fed back into the conversation. */
    val summary: String,
)

/**
 * A typed action the agent can take on the device — "open an app", "place a
 * call", "create a calendar event". Capabilities are how an LLM tool call
 * becomes a real Android action.
 *
 * Each capability exposes itself to the model as a [ToolSpec]; when the model
 * calls it, [run] executes with the parsed arguments.
 */
interface Capability {
    val name: String
    val description: String

    /** JSON Schema for this capability's input. */
    val inputSchemaJson: String

    fun toToolSpec(): ToolSpec = ToolSpec(name, description, inputSchemaJson)

    /** Execute the action. Must not throw; report failures via [CapabilityResult]. */
    fun run(context: Context, args: JSONObject): CapabilityResult
}

/**
 * Registry + dispatcher. Publishes the available tools to the provider and
 * routes the model's [ToolCall]s to the matching [Capability].
 */
class CapabilityBus(private val capabilities: List<Capability>) {

    fun toolSpecs(): List<ToolSpec> = capabilities.map { it.toToolSpec() }

    fun execute(context: Context, call: ToolCall): CapabilityResult {
        val capability = capabilities.firstOrNull { it.name == call.name }
            ?: return CapabilityResult(ok = false, summary = "Unknown capability: ${call.name}")
        val args = runCatching { JSONObject(call.argumentsJson) }.getOrDefault(JSONObject())
        return runCatching { capability.run(context, args) }
            .getOrElse { CapabilityResult(ok = false, summary = "Capability failed: ${it.message}") }
    }
}
