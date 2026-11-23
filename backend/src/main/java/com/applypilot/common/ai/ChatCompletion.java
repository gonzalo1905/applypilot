package com.applypilot.common.ai;

/**
 * The model's answer plus what it cost.
 *
 * @param content the raw text the model produced; callers must validate it
 * @param model the model that answered, as reported by the provider
 * @param promptTokens tokens we sent (instructions + input); null if not reported
 * @param completionTokens tokens the model generated; null if not reported
 */
public record ChatCompletion(String content, String model, Integer promptTokens, Integer completionTokens) {
}
