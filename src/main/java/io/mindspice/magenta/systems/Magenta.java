package io.mindspice.magenta.systems;

import io.mindspice.magenta.config.Config;

public class Magenta {
    private final Config config;

    public Magenta(Config config) {
        this.config = config;
    }

    public Config config() {
        return config;
    }
}
