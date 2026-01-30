package com.akselglyholt.velocityLimboHandler.storage;

import com.imaginarycode.minecraft.redisbungee.AbstractRedisBungeeAPI;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

public class ValioBungeeChannelStore implements ChannelStore {
    private static final String LAST_GROUP_PREFIX = "player:last-group:";
    private static final String CHANNEL_COUNT_PREFIX = "channel:count:";
    private static final String CURRENT_CHANNEL_PREFIX = "player:current-channel:";
    private static final String GROUP_SET_KEY = "server-groups";
    private static final String GROUP_LIST_PREFIX = "server-groups:";
    private static final String GROUP_META_SUFFIX = ":meta";

    private final AbstractRedisBungeeAPI api;
    private final String keyPrefix;
    private final Logger logger;

    public ValioBungeeChannelStore(String keyPrefix, Logger logger) {
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

    private Object getSummoner() {
        return invoke(api, "getSummoner", new Class<?>[]{});
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

    private boolean deleteKey(Object jedis, String key) {
        if (jedis == null || key == null) return false;
        try {
            Method[] methods = jedis.getClass().getMethods();
            for (Method method : methods) {
                if (!method.getName().equals("del")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1) continue;
                if (params[0] == String.class) {
                    method.invoke(jedis, key);
                    return true;
                }
                if (params[0].isArray() && params[0].getComponentType() == String.class) {
                    method.invoke(jedis, new Object[]{new String[]{key}});
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            logger.warning("ValioBungee Redis call failed (del): " + e.getMessage());
            return false;
        }
    }

    private Object obtainResource() {
        Object summoner = getSummoner();
        if (summoner == null) {
            logger.warning("ValioBungee summoner is null.");
            return null;
        }
        return invoke(summoner, "obtainResource", new Class<?>[]{});
    }

    private void closeResource(Object resource) {
        if (resource == null) return;
        if (resource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                logger.warning("Failed to close Redis resource: " + e.getMessage());
            }
            return;
        }
        invoke(resource, "close", new Class<?>[]{});
    }

    private Object withResource(Function<Object, Object> fn) {
        Object resource = obtainResource();
        if (resource == null) return null;
        try {
            return fn.apply(resource);
        } finally {
            closeResource(resource);
        }
    }

    @Override
    public String getLastGroup(UUID playerId) {
        Object result = withResource(jedis -> invoke(jedis, "get", new Class<?>[]{String.class},
                key(LAST_GROUP_PREFIX + playerId)));
        return result == null ? null : String.valueOf(result);
    }

    @Override
    public void setLastGroup(UUID playerId, String groupName) {
        withResource(jedis -> {
            if (groupName == null || groupName.isBlank()) {
                deleteKey(jedis, key(LAST_GROUP_PREFIX + playerId));
            } else {
                invoke(jedis, "set", new Class<?>[]{String.class, String.class},
                        key(LAST_GROUP_PREFIX + playerId), groupName);
            }
            return null;
        });
    }

    @Override
    public void clearLastGroup(UUID playerId) {
        withResource(jedis -> {
            deleteKey(jedis, key(LAST_GROUP_PREFIX + playerId));
            return null;
        });
    }

    @Override
    public int incrementChannelCount(String serverName) {
        Object result = withResource(jedis -> invoke(jedis, "incr", new Class<?>[]{String.class},
                key(CHANNEL_COUNT_PREFIX + serverName)));
        if (result instanceof Number) {
            return ((Number) result).intValue();
        }
        return 0;
    }

    @Override
    public int decrementChannelCount(String serverName) {
        Object result = withResource(jedis -> invoke(jedis, "decr", new Class<?>[]{String.class},
                key(CHANNEL_COUNT_PREFIX + serverName)));
        int value = result instanceof Number ? ((Number) result).intValue() : 0;
        if (value < 0) {
            withResource(jedis -> {
                invoke(jedis, "set", new Class<?>[]{String.class, String.class},
                        key(CHANNEL_COUNT_PREFIX + serverName), "0");
                return null;
            });
            return 0;
        }
        return value;
    }

    @Override
    public int getChannelCount(String serverName) {
        Object result = withResource(jedis -> invoke(jedis, "get", new Class<?>[]{String.class},
                key(CHANNEL_COUNT_PREFIX + serverName)));
        if (result == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(result));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public String getCurrentChannel(UUID playerId) {
        Object result = withResource(jedis -> invoke(jedis, "get", new Class<?>[]{String.class},
                key(CURRENT_CHANNEL_PREFIX + playerId)));
        return result == null ? null : String.valueOf(result);
    }

    @Override
    public void setCurrentChannel(UUID playerId, String serverName) {
        withResource(jedis -> {
            if (serverName == null || serverName.isBlank()) {
                deleteKey(jedis, key(CURRENT_CHANNEL_PREFIX + playerId));
            } else {
                invoke(jedis, "set", new Class<?>[]{String.class, String.class},
                        key(CURRENT_CHANNEL_PREFIX + playerId), serverName);
            }
            return null;
        });
    }

    @Override
    public void clearCurrentChannel(UUID playerId) {
        withResource(jedis -> {
            deleteKey(jedis, key(CURRENT_CHANNEL_PREFIX + playerId));
            return null;
        });
    }

    @Override
    public void storeGroups(Iterable<String> groupNames) {
        withResource(jedis -> {
            deleteKey(jedis, key(GROUP_SET_KEY));
            if (groupNames != null) {
                for (String groupName : groupNames) {
                    if (groupName == null || groupName.isBlank()) continue;
                    invoke(jedis, "sadd", new Class<?>[]{String.class, String[].class},
                            key(GROUP_SET_KEY), new String[]{groupName});
                }
            }
            return null;
        });
    }

    @Override
    public void storeGroupServers(String groupName, Iterable<String> servers) {
        if (groupName == null || groupName.isBlank()) return;
        withResource(jedis -> {
            String listKey = key(GROUP_LIST_PREFIX + groupName);
            deleteKey(jedis, listKey);
            if (servers != null) {
                for (String server : servers) {
                    if (server == null || server.isBlank()) continue;
                    invoke(jedis, "rpush", new Class<?>[]{String.class, String[].class},
                            listKey, new String[]{server});
                }
            }
            return null;
        });
    }

    @Override
    public void storeGroupMaxPlayers(String groupName, int maxPlayers) {
        if (groupName == null || groupName.isBlank()) return;
        withResource(jedis -> {
            String metaKey = key(GROUP_LIST_PREFIX + groupName + GROUP_META_SUFFIX);
            invoke(jedis, "hset", new Class<?>[]{String.class, String.class, String.class},
                    metaKey, "max-player", String.valueOf(maxPlayers));
            return null;
        });
    }
}
