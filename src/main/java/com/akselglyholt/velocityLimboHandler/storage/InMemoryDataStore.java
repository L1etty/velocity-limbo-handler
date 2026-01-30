package com.akselglyholt.velocityLimboHandler.storage;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryDataStore implements DataStore {
    private final Set<UUID> consentedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> lastGroups = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> channelCounts = new ConcurrentHashMap<>();

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

    @Override
    public String getLastGroup(UUID playerId) {
        return lastGroups.get(playerId);
    }

    @Override
    public void setLastGroup(UUID playerId, String groupName) {
        if (groupName == null) {
            lastGroups.remove(playerId);
            return;
        }
        lastGroups.put(playerId, groupName);
    }

    @Override
    public void clearLastGroup(UUID playerId) {
        lastGroups.remove(playerId);
    }

    @Override
    public int incrementChannelCount(String serverName) {
        return channelCounts.computeIfAbsent(serverName, key -> new AtomicInteger(0)).incrementAndGet();
    }

    @Override
    public int decrementChannelCount(String serverName) {
        AtomicInteger counter = channelCounts.get(serverName);
        if (counter == null) return 0;
        int next = counter.decrementAndGet();
        if (next < 0) {
            counter.set(0);
            return 0;
        }
        return next;
    }

    @Override
    public int getChannelCount(String serverName) {
        AtomicInteger counter = channelCounts.get(serverName);
        return counter == null ? 0 : counter.get();
    }
}
