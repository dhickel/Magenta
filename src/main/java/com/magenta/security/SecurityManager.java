package com.magenta.security;

import com.magenta.config.ConfigManager;
import com.magenta.config.Config.SecurityConfig;
import com.magenta.io.IOManager;

public class SecurityManager {

    private static IOManager io;

    public static void setIOManager(IOManager ioManager) {
        io = ioManager;
    }

    public static boolean requireApproval(String toolType, String command) {
        SecurityConfig config = ConfigManager.get().security;

        // 1. Blacklist (Blocking)
        if (config.blockedCommands() != null) {
            for (String blocked : config.blockedCommands()) {
                if (command.contains(blocked)) {
                    printBlocked(command, blocked);
                    return false;
                }
            }
        }

        // 2. Whitelist (Auto-Allow)
        if (config.alwaysAllowCommands() != null) {
            for (String allowed : config.alwaysAllowCommands()) {
                // strict check: command starts with allowed + space or is exact match
                if (command.equals(allowed) || command.startsWith(allowed + " ")) {
                    return true;
                }
            }
        }

        // 3. Approval Check
        if (config.approvalRequiredFor() != null && config.approvalRequiredFor().contains(toolType)) {
            return requestUserApproval(toolType, command);
        }

        // Default allow if not configured
        return true;
    }

    private static void printBlocked(String command, String blockedRule) {
        if (io != null) {
            io.securityAlert("[SECURITY] AUTOMATICALLY BLOCKED: " + command + " (Matches rule: " + blockedRule + ")");
        } else {
            System.out.println("[SECURITY] AUTOMATICALLY BLOCKED: " + command + " (Matches rule: " + blockedRule + ")");
        }
    }

    private static synchronized boolean requestUserApproval(String toolType, String command) {
        if (io != null) {
            io.securityAlert("[SECURITY ALERT] Agent wants to execute:");
            io.println("Tool:    " + toolType);
            io.println("Command: " + command);
            String response = io.readLine("Allow? [y/N]: ");
            return response != null &&
                    (response.equalsIgnoreCase("y") || response.equalsIgnoreCase("yes"));
        } else {
            // Fallback if IOManager not set
            System.out.println("\n[SECURITY ALERT] Agent wants to execute:");
            System.out.println("Tool:    " + toolType);
            System.out.println("Command: " + command);
            System.out.print("Allow this? [y/N]: ");

            java.util.Scanner scanner = new java.util.Scanner(System.in);
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                return input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes");
            }
            return false;
        }
    }
}
