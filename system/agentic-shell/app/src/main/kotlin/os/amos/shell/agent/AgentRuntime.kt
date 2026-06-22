package os.amos.shell.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the set of live agents and their state. A process-wide singleton so the
 * UI (agent tray) and the [AgentService] observe one source of truth.
 *
 * In a full AMOS build this is promoted to a privileged system service; here it
 * is an in-process registry exposing a reactive [agents] stream.
 */
object AgentRuntime {

    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    val agents: StateFlow<List<Agent>> = _agents.asStateFlow()

    fun register(agent: Agent) = _agents.update { current ->
        if (current.any { it.id == agent.id }) current else current + agent
    }

    fun start(id: String) = mutate(id) { it.copy(status = AgentStatus.RUNNING) }

    fun pause(id: String) = mutate(id) { it.copy(status = AgentStatus.PAUSED) }

    fun recordAction(id: String, action: String) =
        mutate(id) { it.copy(lastAction = action) }

    fun runningCount(): Int = _agents.value.count { it.status == AgentStatus.RUNNING }

    private fun mutate(id: String, transform: (Agent) -> Agent) = _agents.update { list ->
        list.map { if (it.id == id) transform(it) else it }
    }
}
