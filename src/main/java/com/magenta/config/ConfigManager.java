package com.magenta.config;

import com.magenta.io.IOManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Stateless utility for loading configuration and parsing arguments.
 * No global state - returns Config directly.
 */
public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private ConfigManager() {}

    /**
     * Load configuration from command-line arguments.
     * Parses args, loads config file, resolves references.
     *
     * @param args Command-line arguments
     * @return Loaded and initialized Config
     */
    public static Config load(String[] args) throws IOException {
        Map<Arg, Arg.Value> parsedArgs = Arg.parseAll(args == null ? new String[0] : args);

        String configPath = parsedArgs.containsKey(Arg.CONFIG)
                ? parsedArgs.get(Arg.CONFIG).getString()
                : "config.json";

        logger.info("Loading configuration from: {}", configPath);

        try {
            ObjectMapper mapper = new ObjectMapper();
            Config config = mapper.readValue(new File(configPath), Config.class);
            config.initializeReferences();
            logger.info("Configuration loaded successfully");
            return config;
        } catch (IOException e) {
            logger.error("Failed to load or parse configuration from {}: {}", configPath, e.getMessage());
            throw e;
        }
    }

    /**
     * Parse command-line arguments.
     *
     * @param args Command-line arguments
     * @return Parsed argument map
     */
    public static Map<Arg, Arg.Value> parseArgs(String[] args) {
        return Arg.parseAll(args == null ? new String[0] : args);
    }

    /**
     * Load config from a specific file path (for testing).
     */
    public static Config loadFromFile(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Config conf = mapper.readValue(new File(filePath), Config.class);
        conf.initializeReferences();
        return conf;
    }

    // === Display methods for config ===

    public static void printSummary(IOManager io, Config config) {
        io.print("Configuration Summary:\n");
        io.print("─".repeat(60) + "\n");
        io.print("Agents:     " + config.agents.size() + "\n");
        io.print("Models:     " + config.models.size() + "\n");
        io.print("Endpoints:  " + config.endpoints.size() + "\n");
        io.print("Securities: " + config.securities.size() + "\n");
        io.print("Colors:     " + config.colorConfigs.size() + "\n");
        io.print("Tasks:      " + config.taskTemplates().size() + " templates\n");
        io.print("\n");
        io.print("Config file: " + System.getProperty("user.dir") + "/config.json\n");
        io.print("─".repeat(60) + "\n");
        io.print("Use '/config show <section>' to view details\n");
        io.print("Sections: agents, models, endpoints, securities, colors, tasks\n");
    }

    public static void printSection(IOManager io, Config config, String section) {
        switch (section.toLowerCase()) {
            case "agents" -> printAgents(io, config);
            case "models" -> printModels(io, config);
            case "endpoints" -> printEndpoints(io, config);
            case "securities" -> printSecurities(io, config);
            case "colors" -> printColors(io, config);
            case "tasks" -> printTasks(io, config);
            default -> {
                io.print("Unknown section: " + section + "\n");
                io.print("Available: agents, models, endpoints, securities, colors, tasks\n");
            }
        }
    }

    private static void printAgents(IOManager io, Config config) {
        io.print("Agents Configuration:\n");
        io.print("─".repeat(60) + "\n");

        for (var entry : config.agents.entrySet()) {
            var agent = entry.getValue();
            io.print(entry.getKey() + ":\n");
            io.print("  Model: " + agent.model().modelName() + "\n");
            io.print("  Tools: " + (agent.tools() != null ? String.join(", ", agent.tools()) : "none") + "\n");
            io.print("  Security: " + agent.security().approvalRequiredFor() + "\n");
            if (agent.colors() != null) {
                io.print("  Colors: configured\n");
            }
            io.print("  Cursor: \"" + agent.cursor() + "\"\n\n");
        }
    }

    private static void printModels(IOManager io, Config config) {
        io.print("Models Configuration:\n");
        io.print("─".repeat(60) + "\n");

        for (var entry : config.models.entrySet()) {
            var model = entry.getValue();
            io.print(entry.getKey() + ":\n");
            io.print("  Model Name: " + model.modelName() + "\n");
            io.print("  Max Context: " + model.maxContext() + " tokens\n");
            io.print("  Compact Threshold: " + model.compactThreshold() + " tokens\n\n");
        }
    }

    private static void printEndpoints(IOManager io, Config config) {
        io.print("Endpoints Configuration:\n");
        io.print("─".repeat(60) + "\n");

        for (var entry : config.endpoints.entrySet()) {
            io.print(entry.getKey() + ": " + entry.getValue().getClass().getSimpleName() + "\n");
        }
    }

    private static void printSecurities(IOManager io, Config config) {
        io.print("Security Configurations:\n");
        io.print("─".repeat(60) + "\n");

        for (var entry : config.securities.entrySet()) {
            var security = entry.getValue();
            io.print(entry.getKey() + ":\n");
            io.print("  Blocked: " + (security.blockedCommands() != null ? String.join(", ", security.blockedCommands()) : "none") + "\n");
            io.print("  Allowed: " + (security.alwaysAllowCommands() != null ? String.join(", ", security.alwaysAllowCommands()) : "none") + "\n");
            io.print("  Approval required for: " + (security.approvalRequiredFor() != null ? String.join(", ", security.approvalRequiredFor()) : "none") + "\n\n");
        }
    }

    private static void printColors(IOManager io, Config config) {
        io.print("Color Configurations:\n");
        io.print("─".repeat(60) + "\n");

        for (var entry : config.colorConfigs.entrySet()) {
            io.print(entry.getKey() + ": configured\n");
        }
    }

    private static void printTasks(IOManager io, Config config) {
        io.print("Task Templates:\n");
        io.print("─".repeat(60) + "\n");

        var templates = config.taskTemplates();
        if (templates.isEmpty()) {
            io.print("(no task templates configured)\n");
        } else {
            for (var entry : templates.entrySet()) {
                io.print("  " + entry.getKey() + "\n");
            }
        }
    }
}
