package os.amos.shell.agent

/** Lifecycle state of a background agent, surfaced in the agent tray. */
enum class AgentStatus { IDLE, RUNNING, PAUSED }

/**
 * A long-lived agent: a goal the OS pursues over time using capabilities, with
 * its activity always visible (transparent agency). In Phase 1 agents gain a
 * real execution policy + schedule; here they model the lifecycle and surface.
 */
data class Agent(
    val id: String,
    val name: String,
    val description: String,
    val status: AgentStatus = AgentStatus.IDLE,
    /** Human-readable description of the most recent thing this agent did. */
    val lastAction: String? = null,
)
