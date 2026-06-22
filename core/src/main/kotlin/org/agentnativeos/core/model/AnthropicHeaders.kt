package org.agentnativeos.core.model

/**
 * The exact HTTP headers a Messages API call needs for a given [AuthMode]. Kept
 * pure (no Android, no sockets) so the header contract is unit-tested rather than
 * only discovered at runtime against the live API.
 *
 * The two auth modes are mutually exclusive on the wire:
 *  - an **API key** goes on `x-api-key`;
 *  - a **subscription OAuth** token goes on `Authorization: Bearer` AND requires
 *    the `anthropic-beta: oauth-2025-04-20` opt-in — `/v1/messages` rejects an
 *    OAuth bearer without it.
 *
 * Sending an API key and a bearer token together makes the API answer 401, so we
 * deliberately emit exactly one credential header for one mode.
 */
object AnthropicHeaders {
    const val VERSION = "2023-06-01"
    const val OAUTH_BETA = "oauth-2025-04-20"

    fun forAuth(auth: AuthMode): Map<String, String> {
        val headers = linkedMapOf(
            "content-type" to "application/json",
            "anthropic-version" to VERSION,
        )
        when (auth) {
            is AuthMode.ApiKey -> headers["x-api-key"] = auth.key
            is AuthMode.SubscriptionOAuth -> {
                headers["authorization"] = "Bearer ${auth.accessToken}"
                headers["anthropic-beta"] = OAUTH_BETA
            }
        }
        return headers
    }
}
