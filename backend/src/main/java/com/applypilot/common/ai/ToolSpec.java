package com.applypilot.common.ai;

/**
 * A tool the model may ask to call: a name, a description the model reads to decide when to
 * use it, and a JSON Schema for its arguments.
 */
public record ToolSpec(String name, String description, String parametersSchema) {
}
