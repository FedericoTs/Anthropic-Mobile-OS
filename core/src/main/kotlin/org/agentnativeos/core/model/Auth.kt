package org.agentnativeos.core.model

/**
 * Chooses the auth mode from whatever credentials are available. Pure and
 * testable. Prefers a user-supplied API key, else a subscription OAuth token,
 * else null (not configured). Blank/whitespace credentials count as absent.
 */
object Auth {

    fun choose(apiKey: String?, oauthToken: String?): AuthMode? {
        apiKey?.trim()?.takeIf { it.isNotEmpty() }?.let { return AuthMode.ApiKey(it) }
        oauthToken?.trim()?.takeIf { it.isNotEmpty() }?.let { return AuthMode.SubscriptionOAuth(it) }
        return null
    }

    fun isConfigured(apiKey: String?, oauthToken: String?): Boolean =
        choose(apiKey, oauthToken) != null
}
