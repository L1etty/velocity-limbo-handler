package com.akselglyholt.velocityLimboHandler.storage;

import java.util.UUID;

public interface DataStore extends AutoCloseable {
    boolean hasConsent(UUID playerId);

    void setConsent(UUID playerId, boolean consented);

    String getLastGroup(UUID playerId);

    void setLastGroup(UUID playerId, String groupName);

    void clearLastGroup(UUID playerId);

    int incrementChannelCount(String serverName);

    int decrementChannelCount(String serverName);

    int getChannelCount(String serverName);

    @Override
    default void close() {
    }
}
