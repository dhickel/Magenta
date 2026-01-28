package com.magenta.session;

import com.magenta.config.Arg;
import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.io.IOManager;

import java.util.Map;
import java.util.Optional;

public class SessionState {
    private Config config;
    private Map<Arg, Arg.Value> args;
    private IOManager ioManager;
    private boolean awaitExit = false;

    public SessionState(Map<Arg, Arg.Value> clArgs) {
        this.args = clArgs;
        args.computeIfAbsent(Arg.CONFIG, (_) -> Arg.Value.String.of("config.json"));
        initConfig();
    }

    private void initConfig() {
        this.config = ConfigManager.loadOrFail(args.get(Arg.CONFIG).getString());
    }


    public Optional<Arg.Value> checkArg(Arg arg) {
        return Optional.ofNullable(args.get(arg));
    }


    public Config config() {
        return config;
    }

    public Config.AgentConfig getBaseAgent() {
        return config.baseAgent();
    }

    public IOManager getIOManager() {
        return ioManager;
    }

    public void setIOManager(IOManager ioManager) {
        this.ioManager = ioManager;
    }

    public boolean shouldExit() {
        return awaitExit;
    }

    public void setExit(boolean shouldExit) {
        this.awaitExit = shouldExit;
    }
}
