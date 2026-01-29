package com.magenta.session;

import com.magenta.config.Config.AgentConfig;
import com.magenta.model.ChatModel;
import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an AI agent with its configuration, model, and conversation history.
 * Extracted from AgentState to remove circular dependencies.
 */
public class Agent {
    private final AgentConfig agentConfig;
    private final ChatModel model;
    private final List<ChatMessage> conversationHistory;

    public Agent(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
        this.model = agentConfig.model().asStreamingChatModel();
        this.conversationHistory = new ArrayList<>();
    }

    public AgentConfig config() {
        return agentConfig;
    }

    public ChatModel model() {
        return model;
    }

    public List<ChatMessage> conversationHistory() {
        return conversationHistory;
    }

    public void addMessage(ChatMessage message) {
        conversationHistory.add(message);
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public Integer getColor() {
        // Prefer colors config's agent color, fall back to direct color field
        var colors = agentConfig.colors();
        if (colors != null && colors.agent() != null) {
            return colors.agent();
        }
        return agentConfig.color();
    }

    public String getSystemPrompt() {
        return agentConfig.systemPrompt();
    }
}
