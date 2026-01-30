package com.akselglyholt.velocityLimboHandler.storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryChannelStore implements ChannelStore {
    private final Map<UUID, String> lastGroups = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> channelCounts = new ConcurrentHashMap<>();
    private final Map<UUID, String> currentChannels = new ConcurrentHashMap<>();
    private final Map<String, String[]> groupServers = new ConcurrentHashMap<>();
    private final Map<String, Integer> groupMaxPlayers = new ConcurrentHashMap<>();

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

    @Override
    public String getCurrentChannel(UUID playerId) {
        return currentChannels.get(playerId);
    }

    @Override
    public void setCurrentChannel(UUID playerId, String serverName) {
        if (serverName == null) {
            currentChannels.remove(playerId);
            return;
        }
        currentChannels.put(playerId, serverName);
    }

    @Override
    public void clearCurrentChannel(UUID playerId) {
        currentChannels.remove(playerId);
    }

    @Override
    public void storeGroups(Iterable<String> groupNames) {
        groupServers.keySet().retainAll(asSet(groupNames));
        groupMaxPlayers.keySet().retainAll(asSet(groupNames));
    }

    @Override
    public void storeGroupServers(String groupName, Iterable<String> servers) {
        groupServers.put(groupName, toArray(servers));
    }

    @Override
    public void storeGroupMaxPlayers(String groupName, int maxPlayers) {
        groupMaxPlayers.put(groupName, maxPlayers);
    }

    private java.util.Set<String> asSet(Iterable<String> values) {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (values == null) return set;
        for (String value : values) {
            if (value != null) set.add(value);
        }
        return set;
    }

    private String[] toArray(Iterable<String> values) {
        if (values == null) return new String[0];
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String value : values) {
            if (value != null) list.add(value);
        }
        return list.toArray(new String[0]);
    }
}
