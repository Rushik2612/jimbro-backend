package com.jimbro.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Value("${spring.datasource.url:}")
    private String defaultUrl;

    @Value("${spring.datasource.username:}")
    private String defaultUsername;

    @Value("${spring.datasource.password:}")
    private String defaultPassword;

    @Value("${spring.datasource.driver-class-name:}")
    private String defaultDriver;

    @Bean
    public javax.sql.DataSource dataSource() {
        String url;
        String username = defaultUsername;
        String password = defaultPassword;
        String driver = defaultDriver;

        String sourceUrl = (databaseUrl != null && !databaseUrl.isEmpty()) ? databaseUrl : defaultUrl;

        if (sourceUrl != null && (sourceUrl.startsWith("postgres://") || sourceUrl.startsWith("postgresql://"))) {
            logger.info("Detected Render PostgreSQL URL. Converting to JDBC format and extracting credentials...");
            
            // Remove prefix
            String cleanUrl = sourceUrl.replaceFirst("^(postgresql|postgres)://", "");
            
            // Extract credentials from URL if present (Render includes them in the URL)
            if (cleanUrl.contains("@")) {
                String[] parts = cleanUrl.split("@", 2);
                String credentials = parts[0];
                cleanUrl = parts[1]; // host:port/db
                
                if (credentials.contains(":")) {
                    String[] credParts = credentials.split(":", 2);
                    username = credParts[0];
                    password = credParts[1];
                } else {
                    username = credentials;
                }
            }

            url = "jdbc:postgresql://" + cleanUrl;
            
            // PostgreSQL on Render often requires SSL
            if (!url.contains("sslmode=")) {
                url += (url.contains("?") ? "&" : "?") + "sslmode=require";
            }
            
            driver = "org.postgresql.Driver";
            logger.info("Database URL parsed successfully. Setting up PG DataSource.");
        } else {
            // Local fallback
            url = defaultUrl;
            logger.info("Using local fallback database config.");
        }

        return org.springframework.boot.jdbc.DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driver)
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .build();
    }
}
