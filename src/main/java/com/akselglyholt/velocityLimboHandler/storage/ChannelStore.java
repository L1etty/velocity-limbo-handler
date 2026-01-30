package com.akselglyholt.velocityLimboHandler.storage;

import java.util.UUID;

public interface ChannelStore extends AutoCloseable {
    String getLastGroup(UUID playerId);

    void setLastGroup(UUID playerId, String groupName);

    void clearLastGroup(UUID playerId);

    int incrementChannelCount(String serverName);

    int decrementChannelCount(String serverName);

    int getChannelCount(String serverName);

    String getCurrentChannel(UUID playerId);

    void setCurrentChannel(UUID playerId, String serverName);

    void clearCurrentChannel(UUID playerId);

    void storeGroups(Iterable<String> groupNames);

    void storeGroupServers(String groupName, Iterable<String> servers);

    void storeGroupMaxPlayers(String groupName, int maxPlayers);

    @Override
    default void close() {
    }
}
