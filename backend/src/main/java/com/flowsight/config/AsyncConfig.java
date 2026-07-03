package com.flowsight.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Enables @Async (report generation, email dispatch).
@Configuration
@EnableAsync
public class AsyncConfig {
}
