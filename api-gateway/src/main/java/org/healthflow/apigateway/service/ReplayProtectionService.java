package org.healthflow.apigateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.healthflow.apigateway.exception.ClientException;
import org.healthflow.apigateway.exception.ErrorCodes;
import org.healthflow.redis.cache.RedisCache;

/**
 * Replay-protection / API-Call-ID idempotency check at the gateway.
 *
 * <p>Implements integration-guide §26.3: every accepted request must have a
 * unique X-HCX-API-Call-ID. The gateway records each accepted call-ID in Redis
 * with a TTL slightly longer than the timestamp window so that a captured
 * request cannot be replayed within that window.
 *
 * <p>Atomic — the underlying {@code SET key value NX EX ttl} guarantees no
 * TOCTOU race between checking and storing.
 *
 * <p>If Redis is unavailable, the call propagates the underlying exception so
 * the request fails closed (rather than silently disabling replay protection).
 */
@Service
public class ReplayProtectionService {

    private static final Logger logger = LoggerFactory.getLogger(ReplayProtectionService.class);

    @Autowired
    private RedisCache redisCache;

    /** TTL in seconds. Defaults to 70 (10 s timestamp window + 60 s safety). Configurable. */
    @Value("${replay.protection.ttl:70}")
    private int replayTtlSeconds;

    /** Redis key prefix to namespace these entries away from rate-limit counters. */
    @Value("${replay.protection.keyPrefix:hcx:api_call_id:}")
    private String keyPrefix;

    /**
     * Record the apiCallId in Redis. Throws {@link ClientException} with
     * {@link ErrorCodes#ERR_INVALID_PAYLOAD} if the call-ID has already been
     * seen within the TTL window (a duplicate / replay).
     *
     * <p>If apiCallId is null or blank this is a no-op — header validation
     * will reject the request through a different path before this is reached
     * in the production flow, but defending here keeps the unit tests simple.
     */
    public void checkAndRecord(String apiCallId) throws Exception {
        if (apiCallId == null || apiCallId.trim().isEmpty()) {
            return;
        }
        String key = keyPrefix + apiCallId;
        boolean stored = redisCache.setIfAbsent(key, "1", replayTtlSeconds);
        if (!stored) {
            logger.warn("Replay detected — duplicate X-HCX-API-Call-ID within window: {}", apiCallId);
            throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
                    "Duplicate API call ID — possible replay");
        }
    }
}
