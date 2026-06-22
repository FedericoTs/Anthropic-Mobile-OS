package os.amos.shell.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** A launchable app the OS knows about. */
data class InstalledApp(
    val label: String,
    val packageName: String,
)

/**
 * Enumerates launchable apps and resolves fuzzy names ("open my notes") to a
 * concrete package, so both the app grid and the agent's `launch_app` capability
 * share one source of truth.
 */
class AppRepository(private val context: Context) {

    fun installedApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null // hide AMOS itself
                InstalledApp(
                    label = info.loadLabel(pm).toString(),
                    packageName = pkg,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Best-effort fuzzy resolve of a spoken/typed app name to an installed app. */
    fun resolve(query: String): InstalledApp? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val apps = installedApps()
        return apps.firstOrNull { it.label.equals(q, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(q) }
            ?: apps.firstOrNull { it.label.lowercase().contains(q) }
            ?: apps.firstOrNull { q.contains(it.label.lowercase()) }
    }

    fun launchIntentFor(packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
