package com.magenta;

import com.magenta.config.Arg;
import com.magenta.io.IOManager;
import com.magenta.security.SecurityManager;
import com.magenta.session.SessionState;
import com.magenta.session.AgentSession;

import java.io.IOException;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Map<Arg, Arg.Value> arguments = Arg.parseAll(args);
        SessionState sessionState = new SessionState(arguments);

        try (IOManager io = new IOManager()) {
            sessionState.setIOManager(io);
            SecurityManager.setIOManager(io);

            if (sessionState.config().colors != null) {
                io.setColorsConfig(sessionState.config().colors);
            }

            io.info("Starting Magenta...");
            AgentSession agent = new AgentSession(sessionState);
            agent.run();

            io.info("Exiting...");
        } catch (IOException e) {
            System.err.println("Error creating terminal: " + e.getMessage());
        }
    }
}
