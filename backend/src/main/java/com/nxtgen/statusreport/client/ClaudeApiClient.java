package com.nxtgen.statusreport.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtgen.statusreport.config.AnthropicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the Anthropic Messages API and returns the model's raw text reply.
 * Callers are expected to instruct the model (via the system prompt) to
 * respond with JSON only, and to parse the returned string themselves -
 * this client stays generic rather than coupling to one response shape.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic")
public class ClaudeApiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeApiClient.class);

    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ClaudeApiClient(AnthropicProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY is not set - the LLM fallback pass cannot run without it.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("max_tokens", properties.maxTokens());
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl()))
                    .header("content-type", "application/json")
                    .header("x-api-key", properties.apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "Claude API call failed with status " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if ("max_tokens".equals(root.path("stop_reason").asText())) {
                log.warn("Claude reply hit the max-tokens limit ({}) and was truncated; "
                        + "raise anthropic.max-tokens if items are missing.", properties.maxTokens());
            }
            JsonNode contentArray = root.path("content");
            StringBuilder text = new StringBuilder();
            for (JsonNode block : contentArray) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            return text.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call the Claude API: " + e.getMessage(), e);
        }
    }
}
