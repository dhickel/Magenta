package com.magenta.agent;

import com.magenta.tools.*;
import com.magenta.memory.VectorStoreService;
import com.magenta.domain.TodoService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

import java.time.Duration;

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
                .timeout(Duration.ofSeconds(120)) // Longer timeout for sub-tasks
                .temperature(0.0)
                .build();
    }

    public static SubAgent createSpecializedAgent(String roleDescription) {
        ChatLanguageModel model = createModel();
        
        // For simplicity, sub-agents get a basic set of tools or none. 
        // In a full system, we might inject specific tools based on role.
        // Here we give them FileSystem and Shell tools to be useful "workers".
        FileSystemTools fileSystemTools = new FileSystemTools();
        ShellTools shellTools = new ShellTools();

        return AiServices.builder(SubAgent.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(fileSystemTools, shellTools)
                .systemMessageProvider(chatMemoryId -> "You are a specialized assistant. Role: " + roleDescription)
                .build();
    }
}
