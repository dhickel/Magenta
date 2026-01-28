package com.magenta;

import com.magenta.session.AgentSession;

public class Main {

    public static void main(String[] args) {
        try (AgentSession session = AgentSession.fromConfig(args)) {
            session.io().println("Starting Magenta...");
            session.run();
            session.io().println("Exiting...");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
