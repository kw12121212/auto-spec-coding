package org.specdriven.agent.question;

import java.util.*;

/**
 * Registry for named mobile channel providers.
 * Supports programmatic registration and config-driven assembly of channel handles.
 */
public class MobileChannelRegistry {

    private final Map<String, MobileChannelProvider> providers = new HashMap<>();

    public void registerProvider(String name, MobileChannelProvider provider) {
        if (providers.containsKey(name)) {
            throw new IllegalArgumentException("Provider already registered: " + name);
        }
        providers.put(name, provider);
    }

    public MobileChannelProvider provider(String name) {
        MobileChannelProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown channel provider: " + name);
        }
        return provider;
    }

    public Set<String> registeredProviders() {
        return Collections.unmodifiableSet(providers.keySet());
    }

    public List<MobileChannelHandle> assembleAll(List<MobileChannelConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return Collections.emptyList();
        }
        List<MobileChannelHandle> handles = new ArrayList<>(configs.size());
        for (MobileChannelConfig config : configs) {
            MobileChannelProvider provider = provider(config.channelType());
            handles.add(provider.create(config));
        }
        return Collections.unmodifiableList(handles);
    }
}
