package com.akselglyholt.velocityLimboHandler.group;

import com.akselglyholt.velocityLimboHandler.storage.ChannelStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ChannelGroup {
    private final String name;
    private final List<String> servers;
    private final int maxPlayers;

    public ChannelGroup(String name, List<String> servers, int maxPlayers) {
        this.name = name;
        this.servers = Collections.unmodifiableList(new ArrayList<>(servers));
        this.maxPlayers = maxPlayers;
    }

    public String getName() {
        return name;
    }

    public List<String> getServers() {
        return servers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public Optional<String> selectServer(ChannelStore store, String excludedServer) {
        if (servers.isEmpty()) return Optional.empty();
        int limit = maxPlayers > 0 ? maxPlayers : Integer.MAX_VALUE;
        String best = null;
        int bestCount = Integer.MAX_VALUE;

        for (String server : servers) {
            if (excludedServer != null && servers.size() > 1 && server.equalsIgnoreCase(excludedServer)) {
                continue;
            }
            int count = store.getChannelCount(server);
            if (count >= limit) continue;
            if (count < bestCount) {
                best = server;
                bestCount = count;
            }
        }

        if (best == null) {
            for (String server : servers) {
                if (excludedServer == null || !server.equalsIgnoreCase(excludedServer)) {
                    return Optional.of(server);
                }
            }
            return Optional.of(servers.get(0));
        }

        return Optional.of(best);
    }
}
