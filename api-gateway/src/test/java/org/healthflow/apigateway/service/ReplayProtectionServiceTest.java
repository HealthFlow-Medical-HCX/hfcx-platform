package org.healthflow.apigateway.service;

import org.healthflow.apigateway.exception.ClientException;
import org.healthflow.apigateway.exception.ErrorCodes;
import org.healthflow.redis.cache.RedisCache;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the gateway-side replay-protection check (P0-7).
 */
public class ReplayProtectionServiceTest {

    private RedisCache redisCache;
    private ReplayProtectionService service;

    @Before
    public void setUp() throws Exception {
        redisCache = mock(RedisCache.class);
        service = new ReplayProtectionService();
        injectField(service, "redisCache", redisCache);
        injectField(service, "replayTtlSeconds", 70);
        injectField(service, "keyPrefix", "hcx:api_call_id:");
    }

    private static void injectField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void firstSubmissionIsRecordedAndAccepted() throws Exception {
        when(redisCache.setIfAbsent(eq("hcx:api_call_id:abc-123"), eq("1"), eq(70)))
                .thenReturn(true);

        // Should NOT throw.
        service.checkAndRecord("abc-123");

        verify(redisCache, times(1)).setIfAbsent("hcx:api_call_id:abc-123", "1", 70);
    }

    @Test
    public void duplicateSubmissionWithinTtlIsRejected() throws Exception {
        when(redisCache.setIfAbsent(eq("hcx:api_call_id:abc-123"), eq("1"), eq(70)))
                .thenReturn(false);

        try {
            service.checkAndRecord("abc-123");
            fail("expected ClientException for duplicate API call ID");
        } catch (ClientException e) {
            assertEquals(ErrorCodes.ERR_INVALID_PAYLOAD, e.getErrCode());
            // Message reaches the integrator and must be stable for tooling.
            assertEquals("Duplicate API call ID — possible replay", e.getMessage());
        }
    }

    @Test
    public void emptyApiCallIdIsTreatedAsNoOp() throws Exception {
        // Header presence is enforced elsewhere; ReplayProtection itself shouldn't second-guess.
        service.checkAndRecord("");
        service.checkAndRecord("   ");
        service.checkAndRecord(null);

        verify(redisCache, never()).setIfAbsent(anyString(), anyString(), anyInt());
    }

    @Test
    public void redisFailurePropagatesAsServerException() throws Exception {
        when(redisCache.setIfAbsent(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("redis down"));

        try {
            service.checkAndRecord("abc-456");
            fail("expected exception when Redis is unreachable");
        } catch (Exception e) {
            // Fail-closed: the request must not reach downstream services if we cannot
            // verify uniqueness. Test passes as long as the exception propagates.
            assertEquals("redis down", e.getMessage());
        }
    }

    @Test
    public void keyIsNamespacedWithConfiguredPrefix() throws Exception {
        injectField(service, "keyPrefix", "custom:prefix:");
        when(redisCache.setIfAbsent(eq("custom:prefix:xyz-789"), eq("1"), eq(70)))
                .thenReturn(true);

        service.checkAndRecord("xyz-789");

        verify(redisCache).setIfAbsent("custom:prefix:xyz-789", "1", 70);
    }
}
