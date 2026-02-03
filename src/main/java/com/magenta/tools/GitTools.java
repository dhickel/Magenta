package com.magenta.tools;

import com.magenta.io.IOManager;
import com.magenta.security.SecurityManager;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GitTools {
    private static final Logger logger = LoggerFactory.getLogger(GitTools.class);

    private final SecurityManager securityManager;
    private final IOManager io;

    public GitTools(SecurityManager securityManager, IOManager io) {
        this.securityManager = securityManager;
        this.io = io;
    }

    // ========== Read-Only Operations ==========

    @Tool("Get git repository status showing modified, staged, and untracked files")
    public String gitStatus() {
        return executeGitCommandWithSecurity("git status", "status");
    }

    @Tool("Show git diff for changes. Use 'staged' to show staged changes, or leave empty for unstaged")
    public String gitDiff(String scope) {
        if (scope != null && scope.equalsIgnoreCase("staged")) {
            return executeGitCommandWithSecurity("git diff --cached", "diff", "--cached");
        }
        return executeGitCommandWithSecurity("git diff", "diff");
    }

    @Tool("View git commit history. Specify number of recent commits to display")
    public String gitLog(int limit) {
        if (limit <= 0) {
            return "Error: Limit must be a positive number. Provide a value greater than 0.";
        }
        return executeGitCommandWithSecurity("git log -" + limit, "log", "-" + limit, "--oneline", "--decorate");
    }

    @Tool("Show the current git branch name")
    public String gitCurrentBranch() {
        return executeGitCommandWithSecurity("git branch --show-current", "branch", "--show-current");
    }

    @Tool("List all git branches (local and remote)")
    public String gitListBranches() {
        return executeGitCommandWithSecurity("git branch -a", "branch", "-a");
    }

    // ========== Write Operations ==========

    @Tool("Stage files for commit. Use '.' to stage all changes, or specify file pattern")
    public String gitAdd(String pathPattern) {
        if (pathPattern == null || pathPattern.trim().isEmpty()) {
            return "Error: Path pattern cannot be empty. Use '.' to stage all changes or specify a file path.";
        }
        return executeGitCommandWithSecurity("git add " + pathPattern, "add", pathPattern);
    }

    @Tool("Unstage files. Use '.' to unstage all, or specify file pattern")
    public String gitReset(String pathPattern) {
        if (pathPattern == null || pathPattern.trim().isEmpty()) {
            return "Error: Path pattern cannot be empty. Use '.' to unstage all or specify a file path.";
        }
        return executeGitCommandWithSecurity("git reset " + pathPattern, "reset", pathPattern);
    }

    @Tool("Create a git commit with a message. Files must be staged first with gitAdd")
    public String gitCommit(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Error: Commit message cannot be empty. Provide a descriptive message for the commit.";
        }
        return executeGitCommandWithSecurity("git commit -m \"" + message + "\"", "commit", "-m", message);
    }

    @Tool("Create or switch to a git branch. Set create=true to create new branch")
    public String gitBranch(String branchName, boolean create) {
        if (branchName == null || branchName.trim().isEmpty()) {
            return "Error: Branch name cannot be empty. Provide a valid branch name.";
        }
        if (create) {
            return executeGitCommandWithSecurity("git checkout -b " + branchName, "checkout", "-b", branchName);
        } else {
            return executeGitCommandWithSecurity("git checkout " + branchName, "checkout", branchName);
        }
    }

    // ========== Helper Methods ==========

    /**
     * Execute a git command with security filtering.
     * The displayCommand is used for security approval prompts, while args are executed.
     */
    private String executeGitCommandWithSecurity(String displayCommand, String... args) {
        // Create ToolExecutionRequest for security filtering
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name("git")
            .arguments(displayCommand)
            .build();

        // Apply security filter via SecurityManager - Optional.empty() = allowed
        var blocked = securityManager.createFilter(io).toolFilter().apply(request, io);

        if (blocked.isPresent()) {
            logger.warn("Git command blocked by security: {}", displayCommand);
            return "Error: Operation blocked by security policy - " + blocked.get();
        }

        // Execute the command if approved
        logger.debug("Executing git command: {}", displayCommand);
        return executeGitCommand(args);
    }

    /**
     * Execute a git command directly without security checks (called after approval).
     */
    private String executeGitCommand(String... args) {
        // Validate git repository exists
        if (!isGitRepository()) {
            String currentDir = System.getProperty("user.dir");
            logger.warn("Git command failed: Not a git repository in {}", currentDir);
            return String.format(
                    "Error: Not a git repository. Current directory '%s' and its parent directories do not contain a .git folder. " +
                    "Initialize a git repository with 'git init' or navigate to an existing git repository.",
                    currentDir
            );
        }

        List<String> command = new ArrayList<>();
        command.add("git");
        for (String arg : args) {
            command.add(arg);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                logger.warn("Git command failed with exit code {}: {}", exitCode, String.join(" ", args));
                return String.format(
                        "Git command failed (exit code %d):\n%s\nCommand: git %s",
                        exitCode, output.toString().trim(), String.join(" ", args)
                );
            }

            logger.debug("Git command succeeded: {}", String.join(" ", args));
            return output.toString().trim();

        } catch (IOException e) {
            logger.error("IO error executing git command: {}", e.getMessage());
            return String.format(
                    "Error executing git command: %s. Check that git is installed and accessible in your PATH.",
                    e.getMessage()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Git command interrupted: {}", String.join(" ", args));
            return "Git command interrupted: " + e.getMessage();
        }
    }

    /**
     * Check if the current directory is a git repository.
     */
    private boolean isGitRepository() {
        File currentDir = new File(System.getProperty("user.dir"));
        while (currentDir != null) {
            File gitDir = new File(currentDir, ".git");
            if (gitDir.exists() && gitDir.isDirectory()) {
                return true;
            }
            currentDir = currentDir.getParentFile();
        }
        return false;
    }
}
