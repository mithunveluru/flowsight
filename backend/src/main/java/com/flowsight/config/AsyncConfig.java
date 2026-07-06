package com.flowsight.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Enables @Async (report generation, email dispatch) and @Scheduled
// (refresh-token and rate-limit housekeeping).
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
