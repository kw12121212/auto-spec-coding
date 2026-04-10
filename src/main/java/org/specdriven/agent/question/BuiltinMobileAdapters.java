package org.specdriven.agent.question;

import org.specdriven.agent.vault.SecretVault;

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * Register all built-in providers with retry wrapping, assemble handles from the given configs,
     * wrap each delivery channel with {@link RetryingDeliveryChannel}, and register collectors
     * with the callback router.
     *
     * @param registry       the registry to populate
     * @param runtime        the question runtime for reply forwarding
     * @param vault          the secret vault for credential resolution
     * @param callbackRouter the router to register collectors with
     * @param configs        the channel configurations to assemble
     * @param logStore       the delivery log store for attempt persistence
     * @param retryConfig    the retry configuration
     * @return list of handles with retry-wrapped delivery channels
     */
    public static List<MobileChannelHandle> registerAll(MobileChannelRegistry registry,
                                   QuestionRuntime runtime,
                                   SecretVault vault,
                                   ReplyCallbackRouter callbackRouter,
                                   List<MobileChannelConfig> configs,
                                   DeliveryLogStore logStore,
                                   RetryConfig retryConfig) {
        Objects.requireNonNull(callbackRouter, "callbackRouter");
        Objects.requireNonNull(configs, "configs");
        Objects.requireNonNull(logStore, "logStore");
        Objects.requireNonNull(retryConfig, "retryConfig");
        registerAll(registry, runtime, vault);
        List<MobileChannelHandle> handles = registry.assembleAll(configs);
        List<MobileChannelHandle> wrappedHandles = new ArrayList<>();
        for (int i = 0; i < handles.size(); i++) {
            MobileChannelConfig config = configs.get(i);
            String channelType = config.channelType();
            MobileChannelHandle original = handles.get(i);

            RetryingDeliveryChannel retryingChannel = new RetryingDeliveryChannel(
                    original.channel(), channelType, retryConfig, logStore, runtime.eventBus());

            MobileChannelHandle wrapped = new MobileChannelHandle(retryingChannel, original.collector());
            wrappedHandles.add(wrapped);

            String webhookSecret = resolveWebhookSecret(config, vault);
            callbackRouter.register(channelType, original.collector(), webhookSecret);
        }
        return Collections.unmodifiableList(wrappedHandles);
    }

    private static String resolveWebhookSecret(MobileChannelConfig config, SecretVault vault) {
        try {
            return vault.get(config.vaultKey() + ".secret");
        } catch (Exception e) {
            return null;
        }
    }
}
