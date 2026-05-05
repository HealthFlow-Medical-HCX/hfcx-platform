package org.healthflow.redis.cache;

import org.healthflow.common.exception.ErrorCodes;
import org.healthflow.common.exception.ServerException;
import redis.clients.jedis.Jedis;


public class RedisCache {

    private String redisHost;
    private int redisPort;

    public RedisCache(String redisHost, int redisPort) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    public Jedis getConnection() throws Exception {
        try {
            return new Jedis(redisHost, redisPort);
        } catch (Exception e) {
            throw new ServerException(ErrorCodes.INTERNAL_SERVER_ERROR, "Error connecting to redis server " + e);
        }
    }

    public void set(String key, String value, int ttl) throws Exception {
        Jedis jedis = getConnection();
        try {
            jedis.setex(key, ttl, value);
        } catch (Exception e) {
            throw new ServerException(ErrorCodes.INTERNAL_SERVER_ERROR, "Exception Occurred While Saving Data to Redis Cache for Key : " + key + "| Exception is:" + e);
        } finally {
            jedis.close();
        }
    }

    public String get(String key) throws Exception {
        Jedis jedis = getConnection();
        try {
            return jedis.get(key);
        } catch (Exception e) {
            throw new ServerException(ErrorCodes.INTERNAL_SERVER_ERROR, "Exception Occurred While Fetching Data from Redis Cache for Key : " + key + "| Exception is:" + e);
        } finally {
            jedis.close();
        }
    }

    public boolean isExists(String key) throws Exception {
        Jedis jedis = getConnection();
        try {
            return jedis.exists(key);
        } catch (Exception e) {
            throw new ServerException(ErrorCodes.INTERNAL_SERVER_ERROR, "Exception occurred while checking key exist or not in Redis Cache: " + key + "| Exception is:" + e);
        } finally {
            jedis.close();
        }
    }

    public Long delete(String key) throws Exception {
        Jedis jedis = getConnection();
        try {
            return jedis.del(key);
        } catch (Exception e) {
            throw new ServerException(ErrorCodes.INTERNAL_SERVER_ERROR, "Exception occurred while deleting the record in redis cache for Key : " + key + "| Exception is:" + e);
        } finally {
            jedis.close();
        }
    }

    public boolean isHealthy() {
        try {
            getConnection().get("test-key");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Atomic set-if-not-exists with a TTL. Used by the gateway's replay-protection
     * filter to record an X-HCX-API-Call-ID exactly once and reject duplicates without
     * a TOCTOU window between {@code exists} and {@code setex}.
     *
     * <p>Implemented against the Jedis 2.9.x API: {@code SET key value NX EX ttl}.
     * Returns {@code true} when the key was newly stored, {@code false} when the key
     * already existed (the caller should treat this as a duplicate).
     *
     * @param key        Redis key
     * @param value      Redis value (typically a constant like {@code "1"})
     * @param ttlSeconds key expiry in seconds (must be &gt; 0)
     */
    public boolean setIfAbsent(String key, String value, int ttlSeconds) throws Exception {
        Jedis jedis = getConnection();
        try {
            String result = jedis.set(key, value, "NX", "EX", ttlSeconds);
            return "OK".equals(result);
        } catch (Exception e) {
            throw new ServerException(ErrorCodes.INTERNAL_SERVER_ERROR,
                    "Exception occurred while atomic set-if-absent in Redis Cache for Key : "
                            + key + " | Exception is:" + e);
        } finally {
            jedis.close();
        }
    }
}
