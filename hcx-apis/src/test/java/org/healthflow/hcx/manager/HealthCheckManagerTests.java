package org.healthflow.hcx.manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.healthflow.common.dto.Response;
import org.healthflow.hcx.managers.HealthCheckManager;
import org.healthflow.kafka.client.IEventService;
import org.healthflow.postgresql.IDatabaseService;
import org.healthflow.redis.cache.RedisCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { HealthCheckManager.class })
public class HealthCheckManagerTests {

  @MockBean
  IEventService kafkaClient;

  @MockBean
  RedisCache redisCache;

  @MockBean
  IDatabaseService postgreSQLClient;

  @Autowired
  HealthCheckManager healthCheckManager;

  @Test
  void checkAllSystemHealth_test() {
    when(kafkaClient.isHealthy()).thenReturn(true);
    when(postgreSQLClient.isHealthy()).thenReturn(true);
    when(redisCache.isHealthy()).thenReturn(true);
    Response resp = healthCheckManager.checkAllSystemHealth();
    assertEquals(true, resp.get("healthy"));
  }

}