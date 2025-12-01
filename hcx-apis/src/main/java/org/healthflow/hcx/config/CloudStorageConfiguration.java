package org.healthflow.hcx.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.healthflow.CloudStorageClient;
import org.healthflow.ICloudService;

@Configuration
class CloudStorageConfiguration {

    @Value("${certificates.accesskey}")
    private String accesskey;

    @Value("${certificates.secretKey}")
    private String secretkey;

    @Bean
    public ICloudService cloudStorageClient() {
        return new CloudStorageClient(accesskey, secretkey);
    }

}
