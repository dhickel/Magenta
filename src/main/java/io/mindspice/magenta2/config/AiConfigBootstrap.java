package io.mindspice.magenta2.config;

import java.io.IOException;
import java.nio.file.Path;

import io.mindspice.magenta2.config.ai.AiConfig;
import io.mindspice.magenta2.config.ai.ExternalAiConfigLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfigBootstrap {

    @Bean
    AiConfig aiConfig(@Value("${app.ai.config-path:./config/ai-config.example.json}") String configPath) throws IOException {
        return ExternalAiConfigLoader.load(Path.of(configPath));
    }
}
