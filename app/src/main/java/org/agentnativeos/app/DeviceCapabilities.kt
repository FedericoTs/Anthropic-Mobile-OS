package org.agentnativeos.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.agentnativeos.core.model.Capability
import org.agentnativeos.core.model.Json

/**
 * The "scan the phone" layer: at run-start, probe which fast-path capabilities this
 * specific device can actually handle (a stock ROM may have no mail app, no maps…)
 * and advertise only those to the planner. Resolution is cheap (a handful of
 * resolveActivity calls), so we re-probe each run — always correct, no stale cache —
 * and persist the latest profile so it's inspectable and ready for a richer scan
 * (app deep-links) later.
 *
 * NOTE: every probed intent must be declared in the manifest <queries> or Android 11+
 * package visibility returns null and a working capability would be wrongly dropped.
 */
object DeviceCapabilities {

    private const val TAG = "AGENT_CAP"

    /** Capabilities this device resolves a handler for, in catalog order. */
    fun discover(context: Context): List<Capability> {
        val pm = context.packageManager
        val resolvable = Capabilities.CATALOG.mapNotNull { cap ->
            val plan = Capabilities.probePlan(cap.name) ?: return@mapNotNull null
            val intent = Intent(plan.action).apply { plan.data?.let { data = Uri.parse(it) } }
            val handlerExists = pm.resolveActivity(intent, 0) != null
            // App-specific capabilities also require their app to actually be installed
            // (an https deep link would otherwise "resolve" to the browser).
            val requiredApp = Capabilities.requiredPackage(cap.name)
            val appInstalled = requiredApp == null || pm.getLaunchIntentForPackage(requiredApp) != null
            if (handlerExists && appInstalled) cap.name else null
        }.toSet()
        DeviceProfile(context).save(resolvable)
        Log.i(TAG, "device capabilities: $resolvable")
        return Capabilities.availableFrom(resolvable)
    }
}

/** The cached device capability profile (plain prefs; not a secret). */
class DeviceProfile(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(capabilities: Set<String>) {
        prefs.edit().putString(KEY, Json.encode(capabilities.sorted())).apply()
    }

    fun load(): Set<String> =
        (Json.parse(prefs.getString(KEY, "[]") ?: "[]") as? List<*>)
            ?.mapNotNull { it as? String }?.toSet().orEmpty()

    private companion object {
        const val FILE = "agent_device_profile"
        const val KEY = "capabilities"
    }
}
