package com.magenta.tools;

import com.magenta.agent.AgentFactory;
import com.magenta.io.IOManager;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;

public class DelegateTool {

    private final IOManager io;
    private final ToolProvider toolProvider;

    public DelegateTool(IOManager io, ToolProvider toolProvider) {
        this.io = io;
        this.toolProvider = toolProvider;
    }

    @Tool("Delegate a complex task to a specialized sub-agent. Provide the task description and the role/specialty of the agent (e.g., 'Java Coder', 'Researcher'). Returns the sub-agent's result.")
    public String delegateTask(String taskDescription, String role) {
        try {
            System.out.println("[System] Spawning sub-agent with role: " + role);

            // Sub-agents get basic tools: shell and filesystem
            List<String> subAgentTools = List.of("shell", "filesystem");

            AgentFactory.SubAgent worker = AgentFactory.createSpecializedAgent(
                    role,
                    subAgentTools,
                    io,
                    toolProvider
            );

            String result = worker.chat(taskDescription);
            return "Sub-Agent Result:\n" + result;
        } catch (Exception e) {
            return "Error interacting with sub-agent: " + e.getMessage();
        }
    }
}
