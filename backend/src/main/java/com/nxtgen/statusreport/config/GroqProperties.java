package com.nxtgen.statusreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "groq")
public record GroqProperties(
        String apiKey,
        String model,
        String baseUrl,
        int maxTokens
) {
}
