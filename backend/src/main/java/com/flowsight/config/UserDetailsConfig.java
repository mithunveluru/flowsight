package com.flowsight.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration
public class UserDetailsConfig {

    // placeholder; replaced by UserService. Breaks the cycle
    // SecurityConfig -> JwtAuthFilter -> UserDetailsService -> SecurityConfig
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public UserDetailsService placeholderUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("User not found: " + username);
        };
    }
}
