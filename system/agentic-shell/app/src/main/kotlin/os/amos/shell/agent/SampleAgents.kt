package os.amos.shell.agent

/**
 * The agents AMOS ships with so the tray is populated on first boot and the
 * "background agents" pillar is visible immediately. These are illustrative
 * descriptors; Phase 1 wires them to real schedules and the Capability Bus.
 */
object SampleAgents {

    val morningBrief = Agent(
        id = "morning_brief",
        name = "Morning Brief",
        description = "Each morning, summarise your day: calendar, weather, and unread priorities.",
        lastAction = "Waiting for tomorrow 7:00 AM.",
    )

    val packageWatcher = Agent(
        id = "package_watcher",
        name = "Package Watcher",
        description = "Track incoming deliveries and notify you when one is out for delivery.",
        lastAction = "No active shipments.",
    )

    val all = listOf(morningBrief, packageWatcher)

    /** Register the defaults if the runtime is empty. */
    fun seed() = all.forEach(AgentRuntime::register)
}
