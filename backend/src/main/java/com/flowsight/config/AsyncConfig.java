package com.flowsight.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables @Async across the application — used by intelligence-report generation
 * so the controller returns immediately and the PDF is built in the background.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
