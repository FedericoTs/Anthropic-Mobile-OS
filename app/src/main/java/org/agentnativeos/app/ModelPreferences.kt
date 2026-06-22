package org.agentnativeos.app

import android.content.Context
import org.agentnativeos.core.model.ModelCatalog
import org.agentnativeos.core.model.ModelId

/**
 * Remembers the user's chosen planner model. Not a secret (just a model id), so
 * plain SharedPreferences — distinct from the encrypted CredentialStore. Defaults
 * to the cheapest model ([ModelCatalog.DEFAULT]); an unknown/stale stored id
 * resolves back to that default rather than failing a run.
 */
class ModelPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var selected: ModelId
        get() = ModelCatalog.byId(prefs.getString(KEY, null)).id
        set(value) { prefs.edit().putString(KEY, value.name).apply() }

    private companion object {
        const val FILE = "agent_prefs"
        const val KEY = "selected_model"
    }
}
