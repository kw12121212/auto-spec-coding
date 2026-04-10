package org.specdriven.agent.question;

import org.specdriven.agent.vault.SecretVault;

import java.util.List;
import java.util.Objects;

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

    /**
     * Register all built-in providers, assemble handles from the given configs,
     * and register each handle's collector with the callback router.
     *
     * @param registry       the registry to populate
     * @param runtime        the question runtime for reply forwarding
     * @param vault          the secret vault for credential resolution
     * @param callbackRouter the router to register collectors with
     * @param configs        the channel configurations to assemble
     */
    public static void registerAll(MobileChannelRegistry registry,
                                   QuestionRuntime runtime,
                                   SecretVault vault,
                                   ReplyCallbackRouter callbackRouter,
                                   List<MobileChannelConfig> configs) {
        Objects.requireNonNull(callbackRouter, "callbackRouter");
        Objects.requireNonNull(configs, "configs");
        registerAll(registry, runtime, vault);
        List<MobileChannelHandle> handles = registry.assembleAll(configs);
        for (int i = 0; i < handles.size(); i++) {
            MobileChannelConfig config = configs.get(i);
            String webhookSecret = resolveWebhookSecret(config, vault);
            callbackRouter.register(config.channelType(), handles.get(i).collector(), webhookSecret);
        }
    }

    private static String resolveWebhookSecret(MobileChannelConfig config, SecretVault vault) {
        try {
            return vault.get(config.vaultKey() + ".secret");
        } catch (Exception e) {
            return null;
        }
    }
}
