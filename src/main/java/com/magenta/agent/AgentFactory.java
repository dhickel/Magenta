package com.magenta.agent;

import com.magenta.io.IOManager;
import com.magenta.tools.ToolProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

import java.time.Duration;
import java.util.List;

public class AgentFactory {

    private static final String OLLAMA_BASE_URL = "http://192.168.1.232:11434";
    private static final String MODEL_NAME = "devstral2";

    public interface SubAgent {
        @SystemMessage("You are a specialized assistant. {{it}}")
        String chat(String userMessage);
    }

    private static ChatLanguageModel createModel() {
        return OllamaChatModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .modelName(MODEL_NAME)
                .timeout(Duration.ofSeconds(120))
                .temperature(0.0)
                .build();
    }

    public static SubAgent createSpecializedAgent(
            String roleDescription,
            List<String> toolNames,
            IOManager io,
            ToolProvider toolProvider
    ) {
        ChatLanguageModel model = createModel();

        // Create tools from configuration with IOManager context
        // Security is enforced within individual tools via requireToolApproval()
        Object[] tools = toolProvider.createTools(toolNames, io);

        return AiServices.builder(SubAgent.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tools)
                .systemMessageProvider(chatMemoryId -> "You are a specialized assistant. Role: " + roleDescription)
                .build();
    }
}
