package org.specdriven.agent.http;

import java.util.List;

/**
 * Response from listing available tools.
 */
public record ToolsListResponse(
        List<ToolInfo> tools) {

    public ToolsListResponse {
        if (tools == null) {
            tools = List.of();
        }
    }
}
