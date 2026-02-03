package com.magenta.tools;

import com.magenta.agent.AgentMessage;
import com.magenta.agent.AgentNetwork;
import com.magenta.config.ConfigManager;
import com.magenta.task.TaskWorkflow;
import com.magenta.task.WorkflowTaskTemplate;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tools for inter-agent communication and task delegation.
 * Enables agents to autonomously send messages, check for messages,
 * broadcast, and delegate tasks to other agents.
 */
public class AgentTools {
    private final String currentAgentAlias;
    private final AgentNetwork network;

    public AgentTools(String currentAgentAlias) {
        this.currentAgentAlias = currentAgentAlias;
        this.network = AgentNetwork.getInstance();
    }

    @Tool("Send a direct message to another agent")
    public String sendMessage(String targetAgent, String message) {
        try {
            network.sendMessage(currentAgentAlias, targetAgent, message);
            return "Message sent to " + targetAgent;
        } catch (Exception e) {
            return "Error sending message: " + e.getMessage();
        }
    }

    @Tool("Check for messages from other agents")
    public String checkMessages() {
        try {
            List<AgentMessage> messages = network.getMessages(currentAgentAlias);

            if (messages.isEmpty()) {
                return "No messages.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("You have ").append(messages.size()).append(" message(s):\n\n");

            for (AgentMessage msg : messages) {
                sb.append("From: ").append(msg.from()).append("\n");
                sb.append("Type: ").append(msg.type()).append("\n");
                sb.append("Content: ").append(msg.content()).append("\n");

                if (msg instanceof AgentMessage.Delegation delegation) {
                    sb.append("Task: ").append(delegation.task().name()).append("\n");
                    sb.append("Description: ").append(delegation.task().description()).append("\n");
                }

                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error checking messages: " + e.getMessage();
        }
    }

    @Tool("Broadcast a message to all agents in the network")
    public String broadcastMessage(String message) {
        try {
            network.broadcast(currentAgentAlias, message);
            return "Message broadcast to all agents";
        } catch (Exception e) {
            return "Error broadcasting: " + e.getMessage();
        }
    }

    @Tool("List all available agents in the network")
    public String listAgents() {
        try {
            var agents = network.listRegisteredAgents();

            if (agents.isEmpty()) {
                return "No agents registered in network.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Registered agents:\n");
            for (var meta : agents) {
                String alias = meta.sessionAlias().value();
                String marker = alias.equals(currentAgentAlias) ? " (you)" : "";
                int msgCount = network.getMessageCount(alias);
                String msgInfo = msgCount > 0 ? " [" + msgCount + " messages]" : "";
                sb.append("  - ").append(alias).append(marker).append(msgInfo).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error listing agents: " + e.getMessage();
        }
    }

    @Tool("Delegate a workflow task to another agent")
    public String delegateTask(String targetAgent, String taskTemplateKey) {
        try {
            var templates = ConfigManager.config().taskTemplates();
            WorkflowTaskTemplate template = templates.get(taskTemplateKey);
            if (template == null) {
                return "Error: Template not found: " + taskTemplateKey;
            }

            String id = UUID.randomUUID().toString().substring(0, 8);
            TaskWorkflow task = template.instantiate(id, Map.of());

            network.delegateTask(currentAgentAlias, targetAgent, task);

            return "Task '" + task.name() + "' delegated to " + targetAgent;
        } catch (Exception e) {
            return "Error delegating task: " + e.getMessage();
        }
    }
}
