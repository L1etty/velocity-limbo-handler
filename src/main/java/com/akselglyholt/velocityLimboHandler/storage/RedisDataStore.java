package com.akselglyholt.velocityLimboHandler.storage;

import java.util.UUID;

@Deprecated
public class RedisDataStore implements DataStore {
    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("RedisDataStore is deprecated. Use ValioBungeeDataStore.");
    }

    @Override
    public boolean hasConsent(UUID playerId) {
        throw unsupported();
    }

    @Override
    public void setConsent(UUID playerId, boolean consented) {
        throw unsupported();
    }

    @Override
    public String getLastGroup(UUID playerId) {
        throw unsupported();
    }

    @Override
    public void setLastGroup(UUID playerId, String groupName) {
        throw unsupported();
    }

    @Override
    public void clearLastGroup(UUID playerId) {
        throw unsupported();
    }

    @Override
    public int incrementChannelCount(String serverName) {
        throw unsupported();
    }

    @Override
    public int decrementChannelCount(String serverName) {
        throw unsupported();
    }

    @Override
    public int getChannelCount(String serverName) {
        throw unsupported();
    }
}
