package com.magenta.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class ConfigManager {
    private static Config configInstance;
    private static Map<Arg, Arg.Value> argsInstance;


    public static void initialize(String[] args) throws IOException {
        // Parse arguments (defaults to config.json if not provided)
        argsInstance = Arg.parseAll(args);

        // Get config path from args
        String configPath = argsInstance.containsKey(Arg.CONFIG)
                ? argsInstance.get(Arg.CONFIG).getString()
                : "config.json";

        // Load and store config
        ObjectMapper mapper = new ObjectMapper();
        configInstance = mapper.readValue(new File(configPath), Config.class);
        configInstance.initializeReferences();
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
