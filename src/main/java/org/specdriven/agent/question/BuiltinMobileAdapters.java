package org.specdriven.agent.question;

import org.specdriven.agent.vault.SecretVault;

/**
 * Auto-registration utility for built-in mobile channel providers.
 * Registers Telegram and Discord providers into a {@link MobileChannelRegistry}.
 */
public final class BuiltinMobileAdapters {

    public static final String TELEGRAM = "telegram";
    public static final String DISCORD = "discord";

    private BuiltinMobileAdapters() {}

    /**
     * Register all built-in mobile channel providers into the given registry.
     *
     * @param registry the registry to populate
     * @param runtime  the question runtime for reply forwarding
     * @param vault    the secret vault for credential resolution
     */
    public static void registerAll(MobileChannelRegistry registry,
                                   QuestionRuntime runtime,
                                   SecretVault vault) {
        registry.registerProvider(TELEGRAM, new TelegramChannelProvider(runtime, vault));
        registry.registerProvider(DISCORD, new DiscordChannelProvider(runtime, vault));
    }
}
