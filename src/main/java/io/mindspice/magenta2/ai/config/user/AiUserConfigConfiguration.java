package io.mindspice.magenta2.ai.config.user;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiUserConfigConfiguration {

    @Bean
    AiConfig aiConfig(@Value("${app.ai.config-path:./config/ai-config.example.json}") String configPath) throws IOException {
        return ExternalAiConfigLoader.load(Path.of(configPath));
    }
}
