package os.amos.shell.ai

/**
 * Placeholder for driving Claude through a Claude.ai **subscription**.
 *
 * This is a first-class seam in the architecture (the user asked for it) but a
 * documented stub: there is currently **no officially supported way** for a
 * third-party OS to use a consumer Claude subscription programmatically, and we
 * will not ship a fragile or ToS-violating hack. When a sanctioned interface
 * exists, fill in [send] here — nothing else in AMOS needs to change, because
 * everything depends only on [ClaudeProvider].
 *
 * See docs/ROADMAP.md → "Subscription connectivity track".
 */
class SubscriptionProvider : ClaudeProvider {

    override val kind = ProviderKind.SUBSCRIPTION
    override val displayName = "Claude subscription (not yet available)"

    override suspend fun send(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        system: String?,
    ): ProviderReply = throw ProviderException(
        "Subscription sign-in isn't available yet. Use an Anthropic API key, or " +
            "switch to Offline (Mock) in Settings.",
    )
}
