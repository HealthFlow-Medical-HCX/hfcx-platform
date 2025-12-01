package org.healthflow.commonscheduler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.healthflow.common.service.RegistryService;
import org.healthflow.common.utils.NotificationUtils;

import java.io.IOException;

@Configuration
public class GenericConfiguration {

    @Value("${registry.basePath}")
    private String registryUrl;

    @Value("${notification.networkPath:networkNotifications.yaml}")
    private String networkPath;

    @Value("${notification.participantPath:participantNotifications.yaml}")
    private String participantPath;

    @Value("${notification.workflowPath:workflowNotifications.yaml}")
    private String workflowPath;

    @Bean
    public RegistryService registryService(){
        return new RegistryService(registryUrl);
    }

    @Bean
    public NotificationUtils notificationUtils() throws IOException {
        return new NotificationUtils(networkPath, participantPath, workflowPath);
    }
}
