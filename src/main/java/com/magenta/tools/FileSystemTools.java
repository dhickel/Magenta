package com.magenta.tools;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    @Tool("Read the content of a file given its relative path from the project root. Returns the file content as a string. Optionally specify startLine and endLine (1-based) to read chunks.")
    public String readFile(String relativePath,
                           @P(value = "Start line number (1-based, inclusive)", required = false) Integer startLine,
                           @P(value = "End line number (1-based, inclusive)", required = false) Integer endLine) {
        try {
            Path filePath = resolvePath(relativePath);
            if (!Files.exists(filePath)) {
                return String.format("Error: File not found at '%s'.", relativePath);
            }
            if (!Files.isRegularFile(filePath)) {
                return String.format("Error: '%s' is not a regular file.", relativePath);
            }

            // Check file size for advice
            long fileSize = Files.size(filePath);
            if (startLine == null && endLine == null) {
                if (fileSize > 100_000) { // 100KB threshold
                    long lineCount = Files.lines(filePath).count();
                    return String.format(
                        "File is large (%d bytes, ~%d lines). Use startLine/endLine to read chunks.\n" +
                        "Example: readFile(\"%s\", 1, 100) for first 100 lines",
                        fileSize, lineCount, relativePath
                    );
                }
                logger.debug("Reading file: {}", relativePath);
                return Files.readString(filePath);
            }

            // Chunked read
            List<String> lines = Files.readAllLines(filePath);
            int totalLines = lines.size();

            if (totalLines == 0) {
                return "File is empty: " + relativePath;
            }

            int start = (startLine != null ? startLine : 1) - 1; // Convert to 0-indexed
            int end = endLine != null ? endLine : totalLines;

            if (start < 0 || start >= totalLines) {
                return String.format("Error: startLine %d out of range (valid: 1-%d)", startLine, totalLines);
            }
            end = Math.min(end, totalLines);
            if (end <= start) {
                return String.format("Error: endLine (%d) must be greater than startLine (%d)", endLine, startLine);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("=== %s (lines %d-%d of %d) ===\n", relativePath, start + 1, end, totalLines));
            for (int i = start; i < end; i++) {
                result.append(String.format("%4d | %s\n", i + 1, lines.get(i)));
            }
            return result.toString();

        } catch (IllegalArgumentException e) {
            logger.warn("Access denied reading file: {}", relativePath);
            return "Error: " + e.getMessage();
        } catch (IOException e) {
            logger.error("IO error reading file {}: {}", relativePath, e.getMessage());
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool("Search and replace text in a file. Returns diff preview and requires confirmation.")
    public String searchReplace(String relativePath,
                                String searchPattern,
                                String replacement,
                                @P("Use regex for search pattern") Boolean regex,
                                @P("Confirm application of changes") Boolean confirm) {
        try {
            Path filePath = resolvePath(relativePath);
            if (!Files.exists(filePath)) return "Error: File not found: " + relativePath;

            String content = Files.readString(filePath);
            String newContent;
            int replacements;

            if (Boolean.TRUE.equals(regex)) {
                Pattern pattern = Pattern.compile(searchPattern);
                Matcher matcher = pattern.matcher(content);
                newContent = matcher.replaceAll(replacement);
                replacements = (int) matcher.results().count();
            } else {
                newContent = content.replace(searchPattern, replacement);
                replacements = (content.length() - newContent.length()) / searchPattern.length();
            }

            if (content.equals(newContent)) {
                return "No matches found for: " + searchPattern;
            }

            String diff = generateDiff(relativePath, content, newContent);

            if (!Boolean.TRUE.equals(confirm)) {
                return String.format(
                    "Found match(es). Preview:\n\n%s\n\n" +
                    "To apply, call: searchReplace(\"%s\", \"%s\", \"%s\", %s, true)",
                    diff, relativePath, searchPattern, replacement, regex
                );
            }

            Files.writeString(filePath, newContent);
            return String.format("Applied changes to %s", relativePath);

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Generate a unified diff between file versions or compare two files")
    public String diff(String pathA, @P("Optional second file path") String pathB) {
        try {
            Path fileA = resolvePath(pathA);
            if (!Files.exists(fileA)) return "Error: File not found: " + pathA;
            String contentA = Files.readString(fileA);

            if (pathB == null) {
                return gitDiffIfAvailable(pathA);
            }

            Path fileB = resolvePath(pathB);
            if (!Files.exists(fileB)) return "Error: File not found: " + pathB;
            String contentB = Files.readString(fileB);

            return generateDiff(pathA + " <-> " + pathB, contentA, contentB);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String generateDiff(String filename, String oldContent, String newContent) {
        List<String> oldLines = oldContent.lines().toList();
        List<String> newLines = newContent.lines().toList();
        Patch<String> patch = DiffUtils.diff(oldLines, newLines);
        List<String> diff = UnifiedDiffUtils.generateUnifiedDiff(filename, filename, oldLines, patch, 3);
        return String.join("\n", diff);
    }

    private String gitDiffIfAvailable(String path) {
        if (!Files.exists(projectRoot.resolve(".git"))) {
             return "Error: Second file path not provided and not in a git repository.";
        }
        try {
            Process process = new ProcessBuilder("git", "diff", "HEAD", path)
                .directory(projectRoot.toFile())
                .start();
            String output = new String(process.getInputStream().readAllBytes());
            if (output.isBlank()) return "No differences found vs HEAD.";
            return output;
        } catch (Exception e) {
            return "Error running git diff: " + e.getMessage();
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
