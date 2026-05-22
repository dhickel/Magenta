package io.mindspice.magenta2.ai.chat.tool.avatar;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AvatarAssistantToolConfiguration {

    @Bean
    public ToolCallbackProvider avatarAssistantToolCallbackProvider(AvatarAssistantTools avatarAssistantTools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(avatarAssistantTools)
            .build();
    }
}
