package com.nxtgen.statusreport.client;

/**
 * Abstraction over the LLM provider used by the extraction and comparison
 * fallback passes. Implementations send the given system + user prompts to
 * their backend and return the model's raw text reply. Callers instruct the
 * model (via the system prompt) to respond with JSON only and parse the
 * returned string themselves, so this contract stays provider-agnostic.
 *
 * <p>Exactly one implementation is active at runtime, selected by the
 * {@code llm.provider} property (see application.yml).
 */
public interface LlmClient {

    String complete(String systemPrompt, String userPrompt);
}
