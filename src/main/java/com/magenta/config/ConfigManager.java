package com.magenta.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static Config configInstance;
    private static Map<Arg, Arg.Value> argsInstance;

    private ConfigManager() {}

    public static void initialize(String[] args) throws IOException {
        // Parse arguments (defaults to config.json if not provided)
        argsInstance = Arg.parseAll(args);

        // Get config path from args
        String configPath = argsInstance.containsKey(Arg.CONFIG)
                ? argsInstance.get(Arg.CONFIG).getString()
                : "config.json";

        logger.info("Loading configuration from: {}", configPath);

        try {
            // Load and store config
            ObjectMapper mapper = new ObjectMapper();
            configInstance = mapper.readValue(new File(configPath), Config.class);
            configInstance.initializeReferences();
            logger.info("Configuration loaded successfully");
        } catch (IOException e) {
            logger.error("Failed to load or parse configuration from {}: {}", configPath, e.getMessage());
            throw e;
        }
    }


    public static Config config() {
        if (configInstance == null) {
            throw new IllegalStateException("Config not initialized. Call ConfigManager.initialize(args) first.");
        }
        return configInstance;
    }


    public static Map<Arg, Arg.Value> args() {
        if (argsInstance == null) {
            throw new IllegalStateException("Args not initialized. Call ConfigManager.initialize(args) first.");
        }
        return argsInstance;
    }


    public static Config loadForTest(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Config conf = mapper.readValue(new File(filePath), Config.class);
        conf.initializeReferences();
        configInstance = conf;
        return conf;
    }
}
