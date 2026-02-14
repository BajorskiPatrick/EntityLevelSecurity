package com.els.config;

import com.els.domain.AccessType;
import com.els.strategies.AccessStrategy;
import com.els.strategies.BlacklistStrategy;
import com.els.strategies.WhitelistStrategy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for ELS Library.
 * Provides the active AccessStrategy bean based on the global configuration.
 */
@Configuration
@EnableConfigurationProperties(ElsProperties.class)
public class ElsAutoConfiguration {

    @Bean
    @Primary
    public AccessStrategy activeAccessStrategy(ElsProperties properties,
            WhitelistStrategy whitelistStrategy,
            BlacklistStrategy blacklistStrategy) {
        if (properties.getAccessType() == AccessType.BLACKLIST) {
            return blacklistStrategy;
        }
        return whitelistStrategy;
    }
}
