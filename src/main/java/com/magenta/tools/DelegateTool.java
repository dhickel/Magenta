package com.magenta.tools;

import com.magenta.agent.AgentFactory;
import dev.langchain4j.agent.tool.Tool;

public class DelegateTool {

    @Tool("Delegate a complex task to a specialized sub-agent. Provide the task description and the role/specialty of the agent (e.g., 'Java Coder', 'Researcher'). Returns the sub-agent's result.")
    public String delegateTask(String taskDescription, String role) {
        try {
            System.out.println("[System] Spawning sub-agent with role: " + role);
            AgentFactory.SubAgent worker = AgentFactory.createSpecializedAgent(role);
            String result = worker.chat(taskDescription);
            return "Sub-Agent Result:\n" + result;
        } catch (Exception e) {
            return "Error interacting with sub-agent: " + e.getMessage();
        }
    }
}
