package com.nxtgen.statusreport.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtgen.statusreport.config.GroqProperties;
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
 * Calls Groq's OpenAI-compatible chat completions API and returns the model's
 * raw text reply. Like {@link ClaudeApiClient}, callers instruct the model to
 * respond with JSON only (via the system prompt) and parse the string
 * themselves, so this client stays generic.
 *
 * <p>Active when {@code llm.provider} is {@code groq} (the default).
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "groq", matchIfMissing = true)
public class GroqApiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GroqApiClient.class);

    private final GroqProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GroqApiClient(GroqProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** How many times to retry a request that was throttled by Groq's rate limits. */
    private static final int MAX_RATE_LIMIT_RETRIES = 5;

    /**
     * Longest wait we'll sleep through inline before retrying. Per-minute limits
     * resolve within ~60s; a per-day (TPD) limit can demand ~1h, which we surface
     * as an error instead of blocking the request.
     */
    private static final long MAX_BACKOFF_WAIT_MS = 90_000L;

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "GROQ_API_KEY is not set - the LLM fallback pass cannot run without it.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("max_tokens", properties.maxTokens());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl()))
                    .header("content-type", "application/json")
                    .header("authorization", "Bearer " + properties.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            for (int attempt = 1; ; attempt++) {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status / 100 == 2) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode choice = root.path("choices").path(0);
                    if ("length".equals(choice.path("finish_reason").asText())) {
                        log.warn("Groq reply hit the max-tokens limit ({}) and was truncated; "
                                + "raise groq.max-tokens if items are missing.", properties.maxTokens());
                    }
                    return choice.path("message").path("content").asText();
                }

                // Groq throttles per-minute (TPM/RPM); these are transient, so wait and retry.
                // A per-day (TPD) limit, by contrast, can require a ~1h wait - don't spin on it.
                if (isRateLimited(status, response.body()) && attempt <= MAX_RATE_LIMIT_RETRIES) {
                    long waitMs = retryAfterMillis(response, attempt);
                    if (waitMs <= MAX_BACKOFF_WAIT_MS) {
                        log.warn("Groq rate limit (status {}); waiting {} ms before retry {}/{}",
                                status, waitMs, attempt, MAX_RATE_LIMIT_RETRIES);
                        Thread.sleep(waitMs);
                        continue;
                    }
                    log.warn("Groq rate limit (status {}) needs a {} ms wait - too long to retry inline.",
                            status, waitMs);
                }

                throw new IllegalStateException(
                        "Groq API call failed with status " + status + ": " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling the Groq API", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call the Groq API: " + e.getMessage(), e);
        }
    }

    /** Groq signals throttling with HTTP 429, or 413 carrying a rate_limit_exceeded code. */
    private boolean isRateLimited(int status, String responseBody) {
        if (status == 429) {
            return true;
        }
        return status == 413 && responseBody != null && responseBody.contains("rate_limit_exceeded");
    }

    /**
     * Honours the {@code Retry-After} header (seconds) when present, otherwise
     * backs off exponentially. Capped so a single call can't stall indefinitely.
     */
    private long retryAfterMillis(HttpResponse<String> response, int attempt) {
        long fallback = Math.min(60_000L, (long) (Math.pow(2, attempt) * 1000));
        return response.headers().firstValue("retry-after")
                .map(value -> {
                    try {
                        // Honour the server's full wait (uncapped) so the caller can tell
                        // a transient per-minute limit from a long per-day one.
                        return (long) (Double.parseDouble(value.trim()) * 1000) + 500;
                    } catch (NumberFormatException ex) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }
}
