package com.akselglyholt.velocityLimboHandler.group;

import com.akselglyholt.velocityLimboHandler.storage.ChannelStore;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class ChannelGroupRegistry {
    private final Map<String, ChannelGroup> groups;
    private final Map<String, String> serverToGroup;

    private ChannelGroupRegistry(Map<String, ChannelGroup> groups, Map<String, String> serverToGroup) {
        this.groups = groups;
        this.serverToGroup = serverToGroup;
    }

    public static ChannelGroupRegistry fromConfig(YamlDocument config, Logger logger) {
        Map<String, ChannelGroup> groups = new HashMap<>();
        Map<String, String> serverToGroup = new HashMap<>();

        Optional<Section> optionalSection = config.getOptionalSection("group");
        if (optionalSection.isEmpty()) {
            return new ChannelGroupRegistry(Collections.emptyMap(), Collections.emptyMap());
        }

        Section groupSection = optionalSection.get();
        for (Object key : groupSection.getKeys()) {
            String groupName = String.valueOf(key);
            Section section = groupSection.getSection(groupName);
            List<String> servers = section.getStringList("servers");
            int maxPlayers = section.getInt("max-player", 0);

            if (servers == null || servers.isEmpty()) {
                logger.warning("Group '" + groupName + "' has no servers configured.");
                continue;
            }

            ChannelGroup group = new ChannelGroup(groupName, servers, maxPlayers);
            groups.put(groupName, group);

            for (String server : servers) {
                serverToGroup.put(server, groupName);
            }
        }

        return new ChannelGroupRegistry(groups, serverToGroup);
    }

    public ChannelGroup getGroup(String name) {
        return groups.get(name);
    }

    public ChannelGroup getGroupForServer(String serverName) {
        String groupName = serverToGroup.get(serverName);
        return groupName == null ? null : groups.get(groupName);
    }

    public String getGroupNameForServer(String serverName) {
        return serverToGroup.get(serverName);
    }

    public java.util.Map<String, ChannelGroup> getGroups() {
        return java.util.Collections.unmodifiableMap(groups);
    }

    public Optional<String> selectServerForGroup(String groupName, ChannelStore store, String excludedServer) {
        ChannelGroup group = groups.get(groupName);
        if (group == null) return Optional.empty();
        return group.selectServer(store, excludedServer);
    }
}
