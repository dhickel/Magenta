package com.magenta.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private static Config instance;

    public static Config load(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        instance = mapper.readValue(new File(filePath), Config.class);
        return instance;
    }

    public static Config get() {
        if (instance == null) {
            throw new IllegalStateException("Config not loaded. Call load() first.");
        }
        return instance;
    }
}
