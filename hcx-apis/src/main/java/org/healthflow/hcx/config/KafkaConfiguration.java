package org.healthflow.hcx.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.healthflow.kafka.client.IEventService;
import org.healthflow.kafka.client.KafkaClient;

@Configuration
public class KafkaConfiguration {

    @Value("${bootstrap-servers}")
    private String kafkaServerUrl;

    @Bean
    public IEventService kafkaClient() {
        return new KafkaClient(kafkaServerUrl);
    }

}
