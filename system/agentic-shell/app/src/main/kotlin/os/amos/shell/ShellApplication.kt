package os.amos.shell

import android.app.Application
import os.amos.shell.agent.SampleAgents

/**
 * Process entry point. Seeds the default agents into the runtime so the agent
 * tray is populated from first boot. Future wiring (DI, persisted settings,
 * the on-device context store) hangs off here.
 */
class ShellApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SampleAgents.seed()
    }
}
