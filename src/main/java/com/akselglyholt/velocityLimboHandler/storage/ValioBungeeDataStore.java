package com.akselglyholt.velocityLimboHandler.storage;

import com.imaginarycode.minecraft.redisbungee.AbstractRedisBungeeAPI;
import com.imaginarycode.minecraft.redisbungee.api.summoners.Summoner;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

public class ValioBungeeDataStore implements DataStore {
    private static final String CONSENT_SET = "consent";
    private static final String LAST_GROUP_PREFIX = "player:last-group:";
    private static final String CHANNEL_COUNT_PREFIX = "channel:count:";

    private final AbstractRedisBungeeAPI api;
    private final String keyPrefix;
    private final Logger logger;

    public ValioBungeeDataStore(String keyPrefix, Logger logger) {
        this.logger = logger;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
        this.api = AbstractRedisBungeeAPI.getAbstractRedisBungeeAPI();
        if (this.api == null) {
            throw new IllegalStateException("ValioBungee API not available.");
        }
    }

    private String key(String raw) {
        if (keyPrefix.isBlank()) {
            return raw;
        }
        return keyPrefix + ":" + raw;
    }

    private Object getJedis() {
        Summoner<?> summoner = api.getSummoner();
        return summoner.obtainResource();
    }

    private Object invoke(Object jedis, String method, Class<?>[] types, Object... args) {
        try {
            Method m = jedis.getClass().getMethod(method, types);
            return m.invoke(jedis, args);
        } catch (Exception e) {
            logger.warning("ValioBungee Redis call failed (" + method + "): " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean hasConsent(UUID playerId) {
        Object jedis = getJedis();
        Object result = invoke(jedis, "sismember", new Class<?>[]{String.class, String.class},
                key(CONSENT_SET), playerId.toString());
        return result instanceof Boolean && (Boolean) result;
    }

    @Override
    public void setConsent(UUID playerId, boolean consented) {
        Object jedis = getJedis();
        if (consented) {
            invoke(jedis, "sadd", new Class<?>[]{String.class, String[].class},
                    key(CONSENT_SET), new String[]{playerId.toString()});
        } else {
            invoke(jedis, "srem", new Class<?>[]{String.class, String[].class},
                    key(CONSENT_SET), new String[]{playerId.toString()});
        }
    }

    @Override
    public String getLastGroup(UUID playerId) {
        Object jedis = getJedis();
        Object result = invoke(jedis, "get", new Class<?>[]{String.class},
                key(LAST_GROUP_PREFIX + playerId));
        return result == null ? null : String.valueOf(result);
    }

    @Override
    public void setLastGroup(UUID playerId, String groupName) {
        Object jedis = getJedis();
        if (groupName == null || groupName.isBlank()) {
            invoke(jedis, "del", new Class<?>[]{String[].class}, new String[]{key(LAST_GROUP_PREFIX + playerId)});
        } else {
            invoke(jedis, "set", new Class<?>[]{String.class, String.class},
                    key(LAST_GROUP_PREFIX + playerId), groupName);
        }
    }

    @Override
    public void clearLastGroup(UUID playerId) {
        Object jedis = getJedis();
        invoke(jedis, "del", new Class<?>[]{String[].class}, new String[]{key(LAST_GROUP_PREFIX + playerId)});
    }

    @Override
    public int incrementChannelCount(String serverName) {
        Object jedis = getJedis();
        Object result = invoke(jedis, "incr", new Class<?>[]{String.class},
                key(CHANNEL_COUNT_PREFIX + serverName));
        if (result instanceof Number) {
            return ((Number) result).intValue();
        }
        return 0;
    }

    @Override
    public int decrementChannelCount(String serverName) {
        Object jedis = getJedis();
        Object result = invoke(jedis, "decr", new Class<?>[]{String.class},
                key(CHANNEL_COUNT_PREFIX + serverName));
        int value = result instanceof Number ? ((Number) result).intValue() : 0;
        if (value < 0) {
            invoke(jedis, "set", new Class<?>[]{String.class, String.class},
                    key(CHANNEL_COUNT_PREFIX + serverName), "0");
            return 0;
        }
        return value;
    }

    @Override
    public int getChannelCount(String serverName) {
        Object jedis = getJedis();
        Object result = invoke(jedis, "get", new Class<?>[]{String.class},
                key(CHANNEL_COUNT_PREFIX + serverName));
        if (result == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(result));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
