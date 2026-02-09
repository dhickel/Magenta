package com.magenta.tools;

import com.magenta.io.IOManager;
import dev.langchain4j.agent.tool.Tool;
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

    private final IOManager io;

    public GitTools(IOManager io) {
        this.io = io;
    }

    // ========== Read-Only Operations ==========

    @Tool("Get git repository status showing modified, staged, and untracked files")
    public String gitStatus() {
        return executeGitCommand("status");
    }

    @Tool("Show git diff for changes. Use 'staged' to show staged changes, or leave empty for unstaged")
    public String gitDiff(String scope) {
        if (scope != null && scope.equalsIgnoreCase("staged")) {
            return executeGitCommand("diff", "--cached");
        }
        return executeGitCommand("diff");
    }

    @Tool("View git commit history. Specify number of recent commits to display")
    public String gitLog(int limit) {
        if (limit <= 0) {
            return "Error: Limit must be a positive number. Provide a value greater than 0.";
        }
        return executeGitCommand("log", "-" + limit, "--oneline", "--decorate");
    }

    @Tool("Show the current git branch name")
    public String gitCurrentBranch() {
        return executeGitCommand("branch", "--show-current");
    }

    @Tool("List all git branches (local and remote)")
    public String gitListBranches() {
        return executeGitCommand("branch", "-a");
    }

    // ========== Write Operations ==========

    @Tool("Stage files for commit. Use '.' to stage all changes, or specify file pattern")
    public String gitAdd(String pathPattern) {
        if (pathPattern == null || pathPattern.trim().isEmpty()) {
            return "Error: Path pattern cannot be empty. Use '.' to stage all changes or specify a file path.";
        }
        return executeGitCommand("add", pathPattern);
    }

    @Tool("Unstage files. Use '.' to unstage all, or specify file pattern")
    public String gitReset(String pathPattern) {
        if (pathPattern == null || pathPattern.trim().isEmpty()) {
            return "Error: Path pattern cannot be empty. Use '.' to unstage all or specify a file path.";
        }
        return executeGitCommand("reset", pathPattern);
    }

    @Tool("Create a git commit with a message. Files must be staged first with gitAdd")
    public String gitCommit(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Error: Commit message cannot be empty. Provide a descriptive message for the commit.";
        }
        return executeGitCommand("commit", "-m", message);
    }

    @Tool("Create or switch to a git branch. Set create=true to create new branch")
    public String gitBranch(String branchName, boolean create) {
        if (branchName == null || branchName.trim().isEmpty()) {
            return "Error: Branch name cannot be empty. Provide a valid branch name.";
        }
        if (create) {
            return executeGitCommand("checkout", "-b", branchName);
        } else {
            return executeGitCommand("checkout", branchName);
        }
    }

    // ========== Helper Methods ==========

    /**
     * Execute a git command. Security filtering is handled centrally by StreamingChat.
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
