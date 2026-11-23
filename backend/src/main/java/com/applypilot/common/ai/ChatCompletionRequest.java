package com.applypilot.common.ai;

/**
 * One request to the model.
 *
 * @param systemPrompt our instructions: who the model is, the rules, the output format.
 * Written by us, never built from user input
 * @param userPrompt the material to work on; may contain untrusted text
 * @param jsonSchema JSON Schema the answer must follow (structured output)
 */
public record ChatCompletionRequest(String systemPrompt, String userPrompt, String jsonSchema) {
}
