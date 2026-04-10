package org.specdriven.agent.question;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes incoming mobile webhook callbacks to the correct {@link QuestionReplyCollector}.
 * Registers assembled collector instances by channel type and dispatches inbound payloads
 * with channel-specific signature verification.
 */
public class ReplyCallbackRouter {

    private static final String TELEGRAM_SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";
    private static final String DISCORD_SIGNATURE_HEADER = "X-Signature-256";

    private final Map<String, RegisteredCollector> collectors = new ConcurrentHashMap<>();

    /**
     * Register a collector for a channel type.
     *
     * @param channelType   the channel type name (e.g. "telegram", "discord")
     * @param collector     the assembled reply collector
     * @param webhookSecret the webhook secret for signature verification (used for Telegram);
     *                      null if verification is handled by the collector itself (Discord)
     * @throws IllegalArgumentException if the channel type is already registered
     */
    public void register(String channelType, QuestionReplyCollector collector, String webhookSecret) {
        Objects.requireNonNull(channelType, "channelType");
        Objects.requireNonNull(collector, "collector");
        RegisteredCollector existing = collectors.putIfAbsent(
                channelType, new RegisteredCollector(collector, webhookSecret));
        if (existing != null) {
            throw new IllegalArgumentException("Channel already registered: " + channelType);
        }
    }

    /**
     * Dispatch an incoming callback payload to the registered collector.
     *
     * @param channelType the channel type from the URL path
     * @param payload     the raw request body
     * @param headers     the relevant HTTP headers
     * @throws IllegalArgumentException   if the channel type is unknown
     * @throws MobileAdapterException     if signature verification fails
     */
    public void dispatch(String channelType, String payload, Map<String, String> headers) {
        RegisteredCollector entry = collectors.get(channelType);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown channel type: " + channelType);
        }

        switch (channelType) {
            case "telegram" -> {
                if (entry.webhookSecret != null) {
                    String secret = headers.get(TELEGRAM_SECRET_HEADER);
                    if (secret == null || !entry.webhookSecret.equals(secret)) {
                        throw new MobileAdapterException("telegram", "Invalid webhook secret token");
                    }
                }
                ((TelegramReplyCollector) entry.collector).processCallback(payload);
            }
            case "discord" -> {
                String signature = headers.get(DISCORD_SIGNATURE_HEADER);
                ((DiscordReplyCollector) entry.collector).processCallback(payload, signature);
            }
            default ->
                throw new IllegalArgumentException("Unsupported channel type: " + channelType);
        }
    }

    /**
     * Returns the set of registered channel type names.
     */
    public Set<String> registeredChannels() {
        return Collections.unmodifiableSet(collectors.keySet());
    }

    private record RegisteredCollector(QuestionReplyCollector collector, String webhookSecret) {}
}
