package com.applypilot.common.ai;

import java.util.List;

/**
 * A multi-turn request with tools. There is no response schema: some providers (Groq among
 * them) do not combine tool calling with strict structured output in one request, so callers
 * validate the final answer themselves or ask for it in a separate structured call.
 *
 * @param systemPrompt our instructions, never built from user input
 * @param messages the conversation so far, oldest first
 * @param tools the tools the model may ask for; empty means none
 */
public record ConversationRequest(String systemPrompt, List<ConversationMessage> messages, List<ToolSpec> tools) {

	public ConversationRequest {
		messages = List.copyOf(messages);
		tools = tools == null ? List.of() : List.copyOf(tools);
	}

}
