package com.akselglyholt.velocityLimboHandler.commands;

import com.akselglyholt.velocityLimboHandler.VelocityLimboHandler;
import com.akselglyholt.velocityLimboHandler.consent.ConsentManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

public class ConsentCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            return;
        }

        String[] args = invocation.arguments();
        if (args.length > 0) {
            String arg = args[0].toLowerCase();
            if (!arg.equals("agree") && !arg.equals("동의")) {
                return;
            }
        }

        ConsentManager consentManager = VelocityLimboHandler.getConsentManager();
        if (consentManager == null) return;

        if (consentManager.hasConsent(player)) {
            consentManager.sendAlreadyAccepted(player);
            return;
        }

        consentManager.accept(player);
        consentManager.sendAccepted(player);
        VelocityLimboHandler.getPlayerManager().enqueuePlayer(player);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}
