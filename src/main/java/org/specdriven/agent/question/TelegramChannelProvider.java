package org.specdriven.agent.question;

import org.specdriven.agent.vault.SecretVault;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link MobileChannelProvider} for Telegram.
 * Resolves the bot token from the vault at creation time and assembles
 * a {@link TelegramDeliveryChannel} + {@link TelegramReplyCollector} pair.
 */
public class TelegramChannelProvider implements MobileChannelProvider {

    private final QuestionRuntime runtime;
    private final SecretVault vault;

    public TelegramChannelProvider(QuestionRuntime runtime, SecretVault vault) {
        this.runtime = runtime;
        this.vault = vault;
    }

    @Override
    public MobileChannelHandle create(MobileChannelConfig config) {
        String chatId = config.overrides().get("chatId");
        if (chatId == null || chatId.isBlank()) {
            throw new MobileAdapterException("telegram", "Missing required override: chatId");
        }

        String botToken = vault.get(config.vaultKey() + ".token");
        String callbackBaseUrl = config.overrides().getOrDefault("callbackBaseUrl", "");

        ConcurrentMap<Long, String> messageMap = new ConcurrentHashMap<>();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        TelegramReplyCollector collector = new TelegramReplyCollector(
                runtime, botToken, callbackBaseUrl, messageMap);
        TelegramDeliveryChannel channel = new TelegramDeliveryChannel(
                botToken, chatId, httpClient, messageMap);

        return new MobileChannelHandle(channel, collector);
    }
}
