package com.akselglyholt.velocityLimboHandler.storage;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryConsentStore implements ConsentStore {
    private final Set<UUID> consentedPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public boolean hasConsent(UUID playerId) {
        return consentedPlayers.contains(playerId);
    }

    @Override
    public void setConsent(UUID playerId, boolean consented) {
        if (consented) {
            consentedPlayers.add(playerId);
        } else {
            consentedPlayers.remove(playerId);
        }
    }
}
