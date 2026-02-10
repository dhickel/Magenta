package com.magenta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try (MagentaApp app = new MagentaApp(args)) {
            app.run();
        } catch (Exception e) {
            logger.error("Failed to run session: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
