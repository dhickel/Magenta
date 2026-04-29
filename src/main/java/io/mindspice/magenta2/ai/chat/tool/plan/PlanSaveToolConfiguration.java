package io.mindspice.magenta2.ai.chat.tool.plan;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlanSaveToolConfiguration {

    @Bean
    public ToolCallbackProvider planSaveToolCallbackProvider(PlanSaveTools planSaveTools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(planSaveTools)
            .build();
    }
}
