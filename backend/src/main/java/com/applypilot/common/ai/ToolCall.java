package com.applypilot.common.ai;

/**
 * A request from the model to run one of the tools it was offered. The model only asks; our
 * code decides whether and how to run it.
 *
 * @param id provider id of the call; the result must be sent back with the same id
 * @param name the tool the model wants to run
 * @param arguments the arguments as a JSON object string, untrusted and to be validated
 */
public record ToolCall(String id, String name, String arguments) {
}
