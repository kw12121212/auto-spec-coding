package org.specdriven.agent.question;

import org.specdriven.agent.vault.SecretVault;

import java.net.http.HttpClient;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link MobileChannelProvider} for Discord.
 * Resolves the webhook URL and secret from the vault at creation time and assembles
 * a {@link DiscordDeliveryChannel} + {@link DiscordReplyCollector} pair.
 */
public class DiscordChannelProvider implements MobileChannelProvider {

    private final QuestionRuntime runtime;
    private final SecretVault vault;

    public DiscordChannelProvider(QuestionRuntime runtime, SecretVault vault) {
        this.runtime = runtime;
        this.vault = vault;
    }

    @Override
    public MobileChannelHandle create(MobileChannelConfig config) {
        String callbackBaseUrl = config.overrides().get("callbackBaseUrl");
        if (callbackBaseUrl == null || callbackBaseUrl.isBlank()) {
            throw new MobileAdapterException("discord", "Missing required override: callbackBaseUrl");
        }

        String webhookUrl = vault.get(config.vaultKey() + ".webhookUrl");
        String webhookSecret = vault.get(config.vaultKey() + ".secret");

        ConcurrentMap<String, String> messageMap = new ConcurrentHashMap<>();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        DiscordReplyCollector collector = new DiscordReplyCollector(
                runtime, webhookSecret, callbackBaseUrl, messageMap);
        DiscordDeliveryChannel channel = new DiscordDeliveryChannel(
                webhookUrl, httpClient, messageMap);

        return new MobileChannelHandle(channel, collector);
    }
}
