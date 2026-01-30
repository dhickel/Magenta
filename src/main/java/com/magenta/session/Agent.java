package com.magenta.session;

import com.magenta.config.Config.AgentConfig;
import com.magenta.model.ChatModel;

public record Agent(AgentConfig config, ChatModel model) {
    public Agent(AgentConfig config) {
        this(config, config.model().asStreamingChatModel());
    }
}