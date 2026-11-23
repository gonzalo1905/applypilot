package com.applypilot.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publishes the {@code @Tool} methods to the MCP server, which lists and runs them. */
@Configuration
public class McpToolsConfig {

	@Bean
	ToolCallbackProvider applyPilotMcpToolProvider(ApplyPilotMcpTools tools) {
		return MethodToolCallbackProvider.builder().toolObjects(tools).build();
	}

}
