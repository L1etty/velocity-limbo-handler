package com.akselglyholt.velocityLimboHandler.listeners;

import com.akselglyholt.velocityLimboHandler.VelocityLimboHandler;
import com.akselglyholt.velocityLimboHandler.consent.ConsentManager;
import com.akselglyholt.velocityLimboHandler.group.ChannelGroup;
import com.akselglyholt.velocityLimboHandler.group.ChannelGroupRegistry;
import com.akselglyholt.velocityLimboHandler.misc.Utility;
import com.akselglyholt.velocityLimboHandler.storage.ChannelStore;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;

public class ConnectionListener {

    @Subscribe
    public void onPlayerPreConnect(@NotNull ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer intendedServer = event.getOriginalServer();
        RegisteredServer limbo = VelocityLimboHandler.getLimboServer();
        ChannelGroupRegistry groupRegistry = VelocityLimboHandler.getChannelGroupRegistry();

        ConsentManager consentManager = VelocityLimboHandler.getConsentManager();
        if (consentManager != null && consentManager.isConsentRequired(player)) {
            if (groupRegistry != null && intendedServer != null) {
                ChannelGroup group = groupRegistry.getGroupForServer(intendedServer.getServerInfo().getName());
                if (group != null) {
                    VelocityLimboHandler.getPlayerManager().setIntendedServer(player, intendedServer);
                }
            }
            if (limbo != null && !Utility.doServerNamesMatch(intendedServer, limbo)) {
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(limbo));
            }
            consentManager.sendPrompt(player);
            return;
        }

        RegisteredServer targetServer = intendedServer;
        if (player.getCurrentServer().isEmpty()) {
            ChannelStore channelStore = VelocityLimboHandler.getChannelStore();
            if (channelStore != null && groupRegistry != null) {
                String lastGroup = channelStore.getLastGroup(player.getUniqueId());
                if (lastGroup == null) {
                    lastGroup = VelocityLimboHandler.getDefaultGroupName();
                }
                if (lastGroup != null) {
                    String excludedServer = player.getCurrentServer()
                            .map(ServerConnection::getServer)
                            .map(srv -> srv.getServerInfo().getName())
                            .orElse(channelStore.getCurrentChannel(player.getUniqueId()));
                    Optional<RegisteredServer> selectedServer = groupRegistry
                            .selectServerForGroup(lastGroup, channelStore, excludedServer)
                            .flatMap(name -> VelocityLimboHandler.getProxyServer().getServer(name));
                    if (selectedServer.isPresent()) {
                        targetServer = selectedServer.get();
                    }
                }
            }
        }

        if (targetServer != intendedServer) {
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(targetServer));
        }

        // Don't reroute if they are already going to Limbo
        if (limbo != null && Utility.doServerNamesMatch(targetServer, limbo)) {
            return;
        }

        // If the plugin is the one moving the player, let them pass!
        if (VelocityLimboHandler.getPlayerManager().isPlayerConnecting(player)) {
            return;
        }

        // Always send group targets to Limbo so they queue and wait when servers are offline
        if (groupRegistry != null && targetServer != null) {
            ChannelGroup group = groupRegistry.getGroupForServer(targetServer.getServerInfo().getName());
            if (group != null && limbo != null) {
                VelocityLimboHandler.getPlayerManager().setIntendedServer(player, targetServer);
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(limbo));
                VelocityLimboHandler.getLogger().info(String.format("Rerouting %s to Limbo (Group %s target %s)",
                        player.getUsername(), group.getName(), targetServer.getServerInfo().getName()));
                return;
            }
        }

        // Check if the server has a queue (for incidental joins)
        if (VelocityLimboHandler.getPlayerManager().hasQueuedPlayers(targetServer)) {
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(limbo));

            VelocityLimboHandler.getLogger().info(String.format("Rerouting %s to Limbo (Server %s is queued)",
                    player.getUsername(), targetServer.getServerInfo().getName()));
        }
    }

    @Subscribe
    public void onPlayerPostConnect(@NotNull ServerPostConnectEvent event) {
        Player player = event.getPlayer();

        RegisteredServer limbo = VelocityLimboHandler.getLimboServer();
        RegisteredServer currentServer = player.getCurrentServer().map(ServerConnection::getServer).orElse(null);
        RegisteredServer previousServer = event.getPreviousServer();

        if (currentServer == null) {
            VelocityLimboHandler.getLogger().severe(String.format("Current server was null for %s.", player.getUsername()));
            return;
        }

        ChannelStore channelStore = VelocityLimboHandler.getChannelStore();
        ChannelGroupRegistry groupRegistry = VelocityLimboHandler.getChannelGroupRegistry();

        ChannelGroup previousGroup = null;
        ChannelGroup currentGroup = null;

        if (channelStore != null && groupRegistry != null) {
            if (previousServer != null) {
                previousGroup = groupRegistry.getGroupForServer(previousServer.getServerInfo().getName());
                if (!Utility.doServerNamesMatch(previousServer, currentServer) && previousGroup != null) {
                    channelStore.decrementChannelCount(previousServer.getServerInfo().getName());
                }
            }

            currentGroup = groupRegistry.getGroupForServer(currentServer.getServerInfo().getName());
            if (currentGroup != null) {
                channelStore.incrementChannelCount(currentServer.getServerInfo().getName());
            }

            channelStore.setCurrentChannel(player.getUniqueId(), currentServer.getServerInfo().getName());

            if (previousGroup != null && limbo != null && Utility.doServerNamesMatch(currentServer, limbo)) {
                channelStore.setLastGroup(player.getUniqueId(), previousGroup.getName());
            } else if (currentGroup != null) {
                channelStore.setLastGroup(player.getUniqueId(), currentGroup.getName());
            }
        }

        // Remove player from queue if they left Limbo and joined another server
        if (previousServer != null && limbo != null && Utility.doServerNamesMatch(previousServer, limbo)) {
            VelocityLimboHandler.getPlayerManager().removePlayer(player);
            return;
        }

        // Handle players who just joined Limbo
        if (limbo != null && Utility.doServerNamesMatch(currentServer, limbo)) {
            // Determine intended server from forced host if available
            String virtualHost = player.getVirtualHost().map(InetSocketAddress::getHostName).orElse(null);

            RegisteredServer intendedTarget = null;

            if (virtualHost != null) {
                List<String> forcedServers = VelocityLimboHandler.getProxyServer()
                        .getConfiguration()
                        .getForcedHosts()
                        .get(virtualHost);

                if (forcedServers != null && !forcedServers.isEmpty()) {
                    intendedTarget = VelocityLimboHandler.getProxyServer()
                            .getServer(forcedServers.get(0))
                            .orElse(null);
                }
            }

            if (intendedTarget == null) {
                intendedTarget = VelocityLimboHandler.getPlayerManager().consumeIntendedServer(player);
            }

            // Fallback to previous server or default
            if (intendedTarget == null) {
                if (previousServer != null) {
                    if (channelStore != null && groupRegistry != null) {
                        ChannelGroup previousGroupResolved = previousGroup != null
                                ? previousGroup
                                : groupRegistry.getGroupForServer(previousServer.getServerInfo().getName());
                        if (previousGroupResolved != null) {
                            String excludedServer = channelStore.getCurrentChannel(player.getUniqueId());
                            if (excludedServer == null) {
                                excludedServer = previousServer.getServerInfo().getName();
                            }
                            Optional<RegisteredServer> selectedServer = groupRegistry
                                    .selectServerForGroup(previousGroupResolved.getName(), channelStore, excludedServer)
                                    .flatMap(name -> VelocityLimboHandler.getProxyServer().getServer(name));

                            if (selectedServer.isPresent()) {
                                intendedTarget = selectedServer.get();
                            }
                        }
                    }

                    if (intendedTarget == null) {
                        intendedTarget = previousServer;
                    }
                } else {
                    if (channelStore != null && groupRegistry != null) {
                        String lastGroup = channelStore.getLastGroup(player.getUniqueId());
                        if (lastGroup == null) {
                            lastGroup = VelocityLimboHandler.getDefaultGroupName();
                        }
                        if (lastGroup != null) {
                            String excludedServer = channelStore.getCurrentChannel(player.getUniqueId());
                            Optional<RegisteredServer> selectedServer = groupRegistry
                                    .selectServerForGroup(lastGroup, channelStore, excludedServer)
                                    .flatMap(name -> VelocityLimboHandler.getProxyServer().getServer(name));

                            if (selectedServer.isPresent()) {
                                intendedTarget = selectedServer.get();
                            }
                        }
                    }

                    if (intendedTarget == null) {
                        intendedTarget = VelocityLimboHandler.getDirectConnectServer();
                    }
                }
            }

            VelocityLimboHandler.getPlayerManager().addPlayer(player, intendedTarget);
            VelocityLimboHandler.getPlayerManager().enqueuePlayer(player);
            ConsentManager consentManager = VelocityLimboHandler.getConsentManager();
            if (consentManager != null && consentManager.isConsentRequired(player)) {
                consentManager.sendPrompt(player);
            }
        }
    }

    @Subscribe
    public void onDisconnect(@NotNull DisconnectEvent event) {
        Player player = event.getPlayer();
        ChannelStore channelStore = VelocityLimboHandler.getChannelStore();
        ChannelGroupRegistry groupRegistry = VelocityLimboHandler.getChannelGroupRegistry();

        if (channelStore != null && groupRegistry != null) {
            RegisteredServer currentServer = player.getCurrentServer()
                    .map(ServerConnection::getServer)
                    .orElse(null);
            if (currentServer != null) {
                ChannelGroup group = groupRegistry.getGroupForServer(currentServer.getServerInfo().getName());
                if (group != null) {
                    channelStore.decrementChannelCount(currentServer.getServerInfo().getName());
                    channelStore.setLastGroup(player.getUniqueId(), group.getName());
                }
            }
            channelStore.clearCurrentChannel(player.getUniqueId());
        }

        VelocityLimboHandler.getPlayerManager().removePlayer(player);
        VelocityLimboHandler.getPlayerManager().removePlayerIssue(player);
        VelocityLimboHandler.getReconnectBlocker().unblock(player.getUniqueId());
    }
}
