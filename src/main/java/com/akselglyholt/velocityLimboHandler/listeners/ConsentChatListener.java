package com.akselglyholt.velocityLimboHandler.listeners;

import com.akselglyholt.velocityLimboHandler.VelocityLimboHandler;
import com.akselglyholt.velocityLimboHandler.consent.ConsentManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.NotNull;

public class ConsentChatListener {

    @Subscribe
    public void onPlayerChat(@NotNull PlayerChatEvent event) {
        ConsentManager consentManager = VelocityLimboHandler.getConsentManager();
        if (consentManager == null) return;

        Player player = event.getPlayer();
        if (!consentManager.isConsentRequired(player)) return;

        String message = event.getMessage().trim();
        if (!message.equalsIgnoreCase("동의")) return;

        event.setResult(PlayerChatEvent.ChatResult.denied());
        consentManager.accept(player);
        consentManager.sendAccepted(player);
        VelocityLimboHandler.getPlayerManager().enqueuePlayer(player);
    }
}
