package com.jimbro.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @Primary
    public DataSourceProperties dataSourceProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        
        // Render provides DATABASE_URL in postgresql://... format
        // Spring Boot requires jdbc:postgresql://...
        String rawUrl = System.getenv("DATABASE_URL");
        
        if (rawUrl != null && !rawUrl.isEmpty()) {
            logger.info("Detected DATABASE_URL from environment");
            
            if (rawUrl.startsWith("postgresql://")) {
                String jdbcUrl = "jdbc:" + rawUrl;
                // PostgreSQL on Render often requires SSL
                if (!jdbcUrl.contains("sslmode=")) {
                    jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=require";
                }
                logger.info("Converted to JDBC URL: {}", jdbcUrl.replaceAll(":.*@", ":***@"));
                properties.setUrl(jdbcUrl);
            } else {
                properties.setUrl(rawUrl);
            }
            
            // On Render, these are usually part of the URL, but Spring might need them explicitly
            String username = System.getenv("DB_USERNAME");
            String password = System.getenv("DB_PASSWORD");
            
            if (username != null) properties.setUsername(username);
            if (password != null) properties.setPassword(password);
            
            properties.setDriverClassName("org.postgresql.Driver");
        } else {
            // Local fallback uses application.properties values
            logger.info("No DATABASE_URL found, using application.properties defaults");
        }
        
        return properties;
    }
}
