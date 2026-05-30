package com.flowsight.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration
public class UserDetailsConfig {

    // Phase 2's UserService will implement UserDetailsService, automatically replacing this.
    // @ConditionalOnMissingBean ensures this placeholder is not registered when a real
    // implementation exists, avoiding the circular dependency:
    // SecurityConfig -> JwtAuthFilter -> UserDetailsService -> SecurityConfig
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public UserDetailsService placeholderUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("User not found: " + username);
        };
    }
}
