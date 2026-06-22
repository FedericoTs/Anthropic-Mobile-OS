package os.amos.shell.capability

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONObject
import os.amos.shell.apps.AppRepository

/**
 * The starter set of device capabilities. Each wraps an Android intent so the
 * agent can act through the same surfaces a person would. Add to this list and
 * the new tool automatically appears to the model via [CapabilityBus].
 */
object BuiltInCapabilities {
    fun all(appRepository: AppRepository): List<Capability> = listOf(
        LaunchAppCapability(appRepository),
        WebSearchCapability(),
        OpenSettingsCapability(),
        DialCapability(),
    )
}

/** Open an installed app by fuzzy name. */
class LaunchAppCapability(private val apps: AppRepository) : Capability {
    override val name = "launch_app"
    override val description =
        "Open an installed app by name. Use when the user wants to go to or start an app."
    override val inputSchemaJson = """
        {"type":"object",
         "properties":{"query":{"type":"string","description":"App name, e.g. Maps"}},
         "required":["query"]}
    """.trimIndent()

    override fun run(context: Context, args: JSONObject): CapabilityResult {
        val query = args.optString("query")
        val app = apps.resolve(query)
            ?: return CapabilityResult(false, "No installed app matched \"$query\".")
        val intent = apps.launchIntentFor(app.packageName)
            ?: return CapabilityResult(false, "${app.label} can't be launched.")
        return startSafely(context, intent, "Opened ${app.label}.")
    }
}

/** Run a web search via the default browser. */
class WebSearchCapability : Capability {
    override val name = "web_search"
    override val description = "Search the web for a query in the user's browser."
    override val inputSchemaJson = """
        {"type":"object",
         "properties":{"query":{"type":"string","description":"What to search for"}},
         "required":["query"]}
    """.trimIndent()

    override fun run(context: Context, args: JSONObject): CapabilityResult {
        val query = args.optString("query").ifBlank {
            return CapabilityResult(false, "No search query provided.")
        }
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startSafely(context, intent, "Searched the web for \"$query\".")
    }
}

/** Open the system Settings app. */
class OpenSettingsCapability : Capability {
    override val name = "open_settings"
    override val description = "Open the device system Settings."
    override val inputSchemaJson = """{"type":"object","properties":{}}"""

    override fun run(context: Context, args: JSONObject): CapabilityResult {
        val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startSafely(context, intent, "Opened Settings.")
    }
}

/** Pre-fill the dialer with a number (the user still presses call). */
class DialCapability : Capability {
    override val name = "dial_number"
    override val description =
        "Open the phone dialer pre-filled with a number. The user confirms the call."
    override val inputSchemaJson = """
        {"type":"object",
         "properties":{"number":{"type":"string","description":"Phone number to dial"}},
         "required":["number"]}
    """.trimIndent()

    override fun run(context: Context, args: JSONObject): CapabilityResult {
        val number = args.optString("number").ifBlank {
            return CapabilityResult(false, "No number provided.")
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startSafely(context, intent, "Opened the dialer for $number.")
    }
}

/** Shared intent launch with uniform failure reporting for the Trust Center. */
private fun startSafely(context: Context, intent: Intent, success: String): CapabilityResult =
    try {
        context.startActivity(intent)
        CapabilityResult(true, success)
    } catch (e: ActivityNotFoundException) {
        CapabilityResult(false, "Nothing on this device can handle that action.")
    }
