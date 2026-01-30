package com.akselglyholt.velocityLimboHandler.consent;

import com.akselglyholt.velocityLimboHandler.storage.ConsentStore;
import com.velocitypowered.api.proxy.Player;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.route.Route;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConsentManager {
    private static final long DEFAULT_PROMPT_COOLDOWN_MS = 5000L;

    private final ConsentStore consentStore;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final String promptMessage;
    private final String acceptedMessage;
    private final String alreadyAcceptedMessage;
    private final long promptCooldownMs;
    private final Map<UUID, Long> lastPromptTimes = new ConcurrentHashMap<>();

    public ConsentManager(ConsentStore consentStore, YamlDocument messageConfig, long promptCooldownSeconds) {
        this.consentStore = consentStore;
        this.promptMessage = messageConfig.getString(Route.from("privacyConsentPrompt"));
        this.acceptedMessage = messageConfig.getString(Route.from("privacyConsentAccepted"));
        this.alreadyAcceptedMessage = messageConfig.getString(Route.from("privacyConsentAlready"));
        this.promptCooldownMs = Math.max(0, promptCooldownSeconds) * 1000L;
    }

    public boolean hasConsent(Player player) {
        return consentStore.hasConsent(player.getUniqueId());
    }

    public boolean isConsentRequired(Player player) {
        return !hasConsent(player);
    }

    public void accept(Player player) {
        consentStore.setConsent(player.getUniqueId(), true);
    }

    public void sendPrompt(Player player) {
        long now = System.currentTimeMillis();
        long last = lastPromptTimes.getOrDefault(player.getUniqueId(), 0L);
        long cooldown = promptCooldownMs > 0 ? promptCooldownMs : DEFAULT_PROMPT_COOLDOWN_MS;
        if (now - last < cooldown) return;
        lastPromptTimes.put(player.getUniqueId(), now);
        player.sendMessage(miniMessage.deserialize(promptMessage));
    }

    public void sendAccepted(Player player) {
        player.sendMessage(miniMessage.deserialize(acceptedMessage));
    }

    public void sendAlreadyAccepted(Player player) {
        player.sendMessage(miniMessage.deserialize(alreadyAcceptedMessage));
    }
}
