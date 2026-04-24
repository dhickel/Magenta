package io.mindspice.magenta2.core;

import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.core.util.Option;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class DataService {
    Path dataRoot;

    public DataService(AiConfig aiConfig) {
        if (!Files.exists(aiConfig.dataRoot())) {
            throw new IllegalStateException(
                    "Must provide a valid root data directory, provided one does not exist: " + aiConfig.dataRoot()
            );
        }
        this.dataRoot = aiConfig.dataRoot();
    }

    public Path getAgentFileStore(String agentName) throws IOException {
        Path agentPath = resolveAgentPath(agentName);
        return Files.createDirectory(agentPath);
    }

    private Path pathWithAgent(Option<String> agentName) {
        return agentName.mapOr(n -> dataRoot.resolve(n), dataRoot);
    }

    private Path resolveAgentPath(String agentName) {
        return dataRoot.resolve(agentName);
    }
}
