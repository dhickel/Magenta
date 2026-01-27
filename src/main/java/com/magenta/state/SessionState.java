package com.magenta.state;

import com.magenta.config.Arg;
import com.magenta.config.Config;
import com.magenta.config.ConfigManager;

import java.util.Map;

public class SessionState {
    private Config config;
    private Map<Arg, Arg.Value> argMap;

    public SessionState(Map<Arg, Arg.Value> clArgs) {
        this.argMap = clArgs;
        argMap.computeIfAbsent(Arg.CONFIG, (_) -> Arg.Value.String.of("config.json"));
        initConfig();
    }


    private void initConfig() {
        this.config = ConfigManager.loadOrFail(argMap.get(Arg.CONFIG).getString());
    }


    public Config config() { return config; }

    public Config.AgentConfig getBaseAgent() {
        return config.baseAgent();
    }


}
