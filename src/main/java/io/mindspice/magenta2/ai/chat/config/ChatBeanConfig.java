package io.mindspice.magenta2.ai.chat.config;

import io.mindspice.magenta2.ai.chat.repository.RepositoryBackedChatMemory;
import io.mindspice.magenta2.ai.chat.service.ContextManagementAdvisor;
import io.mindspice.magenta2.ai.chat.service.ContextUsageTracker;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatBeanConfig {

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return new RepositoryBackedChatMemory(chatMemoryRepository);
    }

    @Bean
    TokenCountEstimator tokenCountEstimator() {
        return new JTokkitTokenCountEstimator();
    }

    @Bean
    ContextManagementAdvisor contextManagementAdvisor(
        ChatMemoryRepository chatMemoryRepository,
        AiConfig aiConfig,
        ChatModel chatModel,
        TokenCountEstimator tokenCountEstimator,
        ContextUsageTracker usageTracker
    ) {
        return new ContextManagementAdvisor(
            chatMemoryRepository,
            aiConfig,
            ChatClient.builder(chatModel).build(),
            tokenCountEstimator,
            usageTracker
        );
    }

    @Bean
    ChatClient chatClient(ChatModel chatModel, ContextManagementAdvisor contextManagementAdvisor) {
        return ChatClient.builder(chatModel)
            .defaultAdvisors(contextManagementAdvisor)
            .build();
    }
}
