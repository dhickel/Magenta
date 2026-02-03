package com.magenta.tools;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class FileSystemTools {
    private static final Logger logger = LoggerFactory.getLogger(FileSystemTools.class);

    private final Path projectRoot;

    public FileSystemTools() {
        this.projectRoot = Paths.get(System.getProperty("user.dir"));
    }

    private Path resolvePath(String relativePath) {
        Path resolved = projectRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(projectRoot)) {
            String message = String.format(
                    "Access denied: Path '%s' is outside project root '%s'. " +
                    "Only paths within the project directory are allowed for security reasons.",
                    relativePath, projectRoot
            );
            throw new IllegalArgumentException(message);
        }
        return resolved;
    }

    @Tool("Read the content of a file given its relative path from the project root. Returns the file content as a string.")
    public String readFile(String relativePath) {
        try {
            Path filePath = resolvePath(relativePath);
            if (!Files.exists(filePath)) {
                return String.format(
                        "Error: File not found at '%s'. Check that the path is correct and the file exists.",
                        relativePath
                );
            }
            if (!Files.isRegularFile(filePath)) {
                return String.format(
                        "Error: '%s' is not a regular file (it may be a directory). Use listDirectory to view directory contents.",
                        relativePath
                );
            }
            logger.debug("Reading file: {}", relativePath);
            return Files.readString(filePath);
        } catch (IllegalArgumentException e) {
            logger.warn("Access denied reading file: {}", relativePath);
            return "Error: " + e.getMessage();
        } catch (IOException e) {
            logger.error("IO error reading file {}: {}", relativePath, e.getMessage());
            return String.format(
                    "Error reading file '%s': %s. Check file permissions and that the file is readable.",
                    relativePath, e.getMessage()
            );
        }
    }

    @Tool("Write content to a file. If the file exists, its content will be overwritten. Provide the relative path from the project root and the content as a string.")
    public String writeFile(String relativePath, String content) {
        try {
            Path filePath = resolvePath(relativePath);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                logger.debug("Creating parent directories for: {}", relativePath);
                Files.createDirectories(parentDir);
            }
            logger.info("Writing file: {} ({} bytes)", relativePath, content.length());
            Files.writeString(filePath, content);
            return String.format("Successfully wrote %d bytes to file: %s", content.length(), relativePath);
        } catch (IllegalArgumentException e) {
            logger.warn("Access denied writing file: {}", relativePath);
            return "Error: " + e.getMessage();
        } catch (IOException e) {
            logger.error("IO error writing file {}: {}", relativePath, e.getMessage());
            return String.format(
                    "Error writing to file '%s': %s. Check that the directory exists and you have write permissions.",
                    relativePath, e.getMessage()
            );
        }
    }

    @Tool("List the contents (files and directories) of a directory, given its relative path from the project root. Returns a string with each item on a new line.")
    public String listDirectory(String relativePath) {
        try {
            Path dirPath = resolvePath(relativePath);
            if (!Files.exists(dirPath)) {
                return String.format(
                        "Error: Directory not found at '%s'. Check that the path is correct.",
                        relativePath
                );
            }
            if (!Files.isDirectory(dirPath)) {
                return String.format(
                        "Error: '%s' is not a directory (it may be a file). Use readFile to view file contents.",
                        relativePath
                );
            }
            logger.debug("Listing directory: {}", relativePath);
            try (Stream<Path> paths = Files.list(dirPath)) {
                StringBuilder sb = new StringBuilder("Contents of " + relativePath + ":\n");
                paths.forEach(p -> sb.append(projectRoot.relativize(p).toString()).append("\n"));
                return sb.toString();
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Access denied listing directory: {}", relativePath);
            return "Error: " + e.getMessage();
        } catch (IOException e) {
            logger.error("IO error listing directory {}: {}", relativePath, e.getMessage());
            return String.format(
                    "Error listing directory '%s': %s. Check directory permissions.",
                    relativePath, e.getMessage()
            );
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
                return String.format(
                        "Error: File or directory not found at '%s'. Nothing to delete.",
                        relativePath
                );
            }
            logger.warn("Deleting: {}", relativePath);
            Files.delete(targetPath);
            return "Successfully deleted: " + relativePath;
        } catch (IllegalArgumentException e) {
            logger.warn("Access denied deleting: {}", relativePath);
            return "Error: " + e.getMessage();
        } catch (IOException e) {
            logger.error("IO error deleting {}: {}", relativePath, e.getMessage());
            return String.format(
                    "Error deleting '%s': %s. If this is a non-empty directory, use deleteDirectoryRecursive instead.",
                    relativePath, e.getMessage()
            );
        }
    }

    @Tool("Recursively delete a directory and all its contents (files and subdirectories) at the specified relative path from the project root. Use with caution!")
    public String deleteDirectoryRecursive(String relativePath) {
        try {
            Path targetPath = resolvePath(relativePath);
            if (!Files.exists(targetPath)) {
                return String.format(
                        "Error: Directory not found at '%s'. Nothing to delete.",
                        relativePath
                );
            }
            if (!Files.isDirectory(targetPath)) {
                return String.format(
                        "Error: '%s' is not a directory. Use deleteFile for regular files.",
                        relativePath
                );
            }
            logger.warn("Recursively deleting directory and all contents: {}", relativePath);
            try (Stream<Path> walk = Files.walk(targetPath)) {
                walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
            return "Successfully deleted directory and all contents: " + relativePath;
        } catch (IllegalArgumentException e) {
            logger.warn("Access denied deleting directory: {}", relativePath);
            return "Error: " + e.getMessage();
        } catch (IOException e) {
            logger.error("IO error recursively deleting directory {}: {}", relativePath, e.getMessage());
            return String.format(
                    "Error recursively deleting directory '%s': %s. Some files may not have been deleted.",
                    relativePath, e.getMessage()
            );
        }
    }
}
