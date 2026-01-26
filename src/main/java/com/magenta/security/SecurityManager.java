package com.magenta.security;

import com.magenta.config.ConfigManager;
import com.magenta.config.Config.SecurityConfig;
import java.util.Scanner;

public class SecurityManager {
    
    public static boolean requireApproval(String toolType, String command) {
        SecurityConfig config = ConfigManager.get().security;
        
        // 1. Blacklist (Blocking)
        if (config.blockedCommands != null) {
            for (String blocked : config.blockedCommands) {
                if (command.contains(blocked)) {
                    System.out.println("[SECURITY] AUTOMATICALLY BLOCKED: " + command + " (Matches rule: " + blocked + ")");
                    return false;
                }
            }
        }

        // 2. Whitelist (Auto-Allow)
        if (config.alwaysAllowCommands != null) {
            for (String allowed : config.alwaysAllowCommands) {
                // strict check: command starts with allowed + space or is exact match
                if (command.equals(allowed) || command.startsWith(allowed + " ")) {
                    return true;
                }
            }
        }

        // 3. Approval Check
        if (config.approvalRequiredFor != null && config.approvalRequiredFor.contains(toolType)) {
            return requestUserApproval(toolType, command);
        }

        // Default allow if not configured
        return true;
    }

    private static synchronized boolean requestUserApproval(String toolType, String command) {
        System.out.println("\n[SECURITY ALERT] Agent wants to execute:");
        System.out.println("Tool:    " + toolType);
        System.out.println("Command: " + command);
        System.out.print("Allow this? [y/N]: ");
        
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextLine()) {
            String input = scanner.nextLine().trim();
            return input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes");
        }
        return false;
    }
}
