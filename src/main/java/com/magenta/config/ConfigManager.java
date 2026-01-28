package com.magenta.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private static Config instance;

    public static Config load(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Config conf = mapper.readValue(new File(filePath), Config.class);
        conf.initializeReferences();
        return conf;
    }

    public static Config loadOrFail(String filePath) {
        try {
            return load(filePath);
        } catch (IOException e) { throw new RuntimeException("Failed to load config file, cannot proceed: " + e); }
    }

    public static Config get() {
        if (instance == null) {
            throw new IllegalStateException("Config not loaded. Call load() first.");
        }
        return instance;
    }
}
