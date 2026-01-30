package com.akselglyholt.velocityLimboHandler.storage;

import java.util.UUID;

public interface ConsentStore extends AutoCloseable {
    boolean hasConsent(UUID playerId);

    void setConsent(UUID playerId, boolean consented);

    @Override
    default void close() {
    }
}
