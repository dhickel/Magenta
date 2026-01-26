package com.magenta.tools;

import dev.langchain4j.agent.tool.Tool;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class FileSystemTools {

    private final Path projectRoot;

    public FileSystemTools() {
        this.projectRoot = Paths.get(System.getProperty("user.dir"));
    }

    private Path resolvePath(String relativePath) {
        Path resolved = projectRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(projectRoot)) {
            throw new IllegalArgumentException("Operation outside project root is not allowed: " + relativePath);
        }
        return resolved;
    }

    @Tool("Read the content of a file given its relative path from the project root. Returns the file content as a string.")
    public String readFile(String relativePath) {
        try {
            Path filePath = resolvePath(relativePath);
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return "Error: File not found or is not a regular file: " + relativePath;
            }
            return Files.readString(filePath);
        } catch (IOException | IllegalArgumentException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool("Write content to a file. If the file exists, its content will be overwritten. Provide the relative path from the project root and the content as a string.")
    public String writeFile(String relativePath, String content) {
        try {
            Path filePath = resolvePath(relativePath);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.writeString(filePath, content);
            return "Successfully wrote to file: " + relativePath;
        } catch (IOException | IllegalArgumentException e) {
            return "Error writing to file: " + e.getMessage();
        }
    }

    @Tool("List the contents (files and directories) of a directory, given its relative path from the project root. Returns a string with each item on a new line.")
    public String listDirectory(String relativePath) {
        try {
            Path dirPath = resolvePath(relativePath);
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                return "Error: Directory not found or is not a directory: " + relativePath;
            }
            try (Stream<Path> paths = Files.list(dirPath)) {
                StringBuilder sb = new StringBuilder("Contents of " + relativePath + ":\n");
                paths.forEach(p -> sb.append(projectRoot.relativize(p).toString()).append("\n"));
                return sb.toString();
            }
        } catch (IOException | IllegalArgumentException e) {
            return "Error listing directory: " + e.getMessage();
        }
    }

    @Tool("Create a new directory at the specified relative path from the project root. Will create any necessary but nonexistent parent directories.")
    public String createDirectory(String relativePath) {
        try {
            Path dirPath = resolvePath(relativePath);
            Files.createDirectories(dirPath);
            return "Successfully created directory: " + relativePath;
        } catch (IOException | IllegalArgumentException e) {
            return "Error creating directory: " + e.getMessage();
        }
    }

    @Tool("Delete a file or an empty directory at the specified relative path from the project root. To delete a non-empty directory, use deleteDirectoryRecursive.")
    public String deleteFile(String relativePath) {
        try {
            Path targetPath = resolvePath(relativePath);
            if (!Files.exists(targetPath)) {
                return "Error: File or directory not found: " + relativePath;
            }
            Files.delete(targetPath);
            return "Successfully deleted: " + relativePath;
        } catch (IOException | IllegalArgumentException e) {
            return "Error deleting file/directory: " + e.getMessage();
        }
    }

    @Tool("Recursively delete a directory and all its contents (files and subdirectories) at the specified relative path from the project root. Use with caution!")
    public String deleteDirectoryRecursive(String relativePath) {
        try {
            Path targetPath = resolvePath(relativePath);
            if (!Files.exists(targetPath) || !Files.isDirectory(targetPath)) {
                return "Error: Directory not found or is not a directory: " + relativePath;
            }
            try (Stream<Path> walk = Files.walk(targetPath)) {
                walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
            return "Successfully deleted directory and its contents: " + relativePath;
        } catch (IOException | IllegalArgumentException e) {
            return "Error recursively deleting directory: " + e.getMessage();
        }
    }
}
