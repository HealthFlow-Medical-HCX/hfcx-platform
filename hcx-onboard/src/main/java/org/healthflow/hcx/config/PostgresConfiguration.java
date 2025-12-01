package org.healthflow.hcx.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.healthflow.common.exception.ClientException;
import org.healthflow.postgresql.IDatabaseService;
import org.healthflow.postgresql.PostgreSQLClient;

import java.sql.SQLException;

@Configuration
public class PostgresConfiguration {

    @Value("${postgres.url}")
    private String postgresUrl;

    @Value("${postgres.user}")
    private String postgresUser;

    @Value("${postgres.password}")
    private String postgresPassword;

    @Bean
    public IDatabaseService postgreSQLClient() throws ClientException, SQLException {
        return new PostgreSQLClient(postgresUrl, postgresUser, postgresPassword);
    }

}
