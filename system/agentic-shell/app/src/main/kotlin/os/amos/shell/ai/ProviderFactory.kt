package os.amos.shell.ai

/**
 * Chooses and builds the active [ClaudeProvider] from a [ProviderConfig].
 *
 * The "no config required" guarantee lives here: if a caller asks for the API
 * but supplied no key, we fall back to [MockProvider] so the OS still boots.
 */
object ProviderFactory {

    fun create(config: ProviderConfig): ClaudeProvider = when (config.kind) {
        ProviderKind.ANTHROPIC_API ->
            if (config.apiKey.isBlank()) MockProvider() else AnthropicApiProvider(config)
        ProviderKind.SUBSCRIPTION -> SubscriptionProvider()
        ProviderKind.MOCK -> MockProvider()
    }

    /**
     * Default startup config: use the API key baked in at build time if present
     * (see app/build.gradle.kts reading local.properties), otherwise Mock.
     */
    fun defaultConfig(apiKey: String): ProviderConfig {
        val kind = if (apiKey.isBlank()) ProviderKind.MOCK else ProviderKind.ANTHROPIC_API
        return ProviderConfig(kind = kind, apiKey = apiKey)
    }
}
