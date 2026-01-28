package com.magenta.session;

import com.magenta.config.Config.AgentConfig;
import com.magenta.model.ChatModel;
import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class AgentState {
    private final AgentSession session;
    private final AgentConfig agentConfig;
    private final ChatModel model;
    private final List<ChatMessage> conversationHistory;

    public AgentState(AgentSession session, AgentConfig agentConfig) {
        this.session = session;
        this.agentConfig = agentConfig;
        this.model = agentConfig.model().asStreamingChatModel();
        this.conversationHistory = new ArrayList<>();
    }

    public AgentSession session() { return session; }
    public AgentConfig agentConfig() { return agentConfig; }
    public ChatModel model() { return model; }
    public List<ChatMessage> conversationHistory() { return conversationHistory; }

    public void addMessage(ChatMessage message) {
        conversationHistory.add(message);
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public Integer getColor() {
        return agentConfig.color();
    }

    public String getSystemPrompt() {
        return agentConfig.systemPrompt();
    }
}
