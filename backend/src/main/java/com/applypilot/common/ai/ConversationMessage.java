package com.applypilot.common.ai;

import java.util.List;

/**
 * One message of a multi-turn conversation with the model, used for tool calling. The system
 * prompt is not a message here: it is a separate field of {@link ConversationRequest}, so it
 * can never be built from user input by accident.
 */
public sealed interface ConversationMessage {

	/** Our request, which may contain untrusted text. */
	record User(String text) implements ConversationMessage {
	}

	/** An earlier answer of the model, replayed so it remembers which tools it asked for. */
	record Assistant(String text, List<ToolCall> toolCalls) implements ConversationMessage {

		public Assistant {
			toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
		}

	}

	/** What our code returned for one tool call. */
	record ToolResult(String toolCallId, String toolName, String content) implements ConversationMessage {
	}

}
