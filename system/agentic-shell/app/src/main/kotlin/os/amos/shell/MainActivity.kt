package os.amos.shell

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import os.amos.shell.agent.AgentRuntime
import os.amos.shell.agent.AgentService
import os.amos.shell.agent.SampleAgents
import os.amos.shell.ui.HomeScreen
import os.amos.shell.ui.theme.AmosTheme

/**
 * The launcher entry point. Registered for the HOME intent category (see the
 * manifest), so when AMOS is the default Home app this Activity *is* the phone's
 * home screen — the conversational agentic surface.
 */
class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bring the Agent Runtime online and mark one sample agent active so the
        // background-agents pillar is visible immediately.
        SampleAgents.seed()
        AgentRuntime.start(SampleAgents.morningBrief.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        AgentService.start(this)

        enableEdgeToEdge()
        setContent {
            AmosTheme {
                HomeScreen()
            }
        }
    }
}
