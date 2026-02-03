package com.magenta.tools;

import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SearchTools {

    private static final int MAX_RESULTS = 100;
    private final Path projectRoot;

    public SearchTools() {
        this.projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    @Tool("Search for text pattern in project files. Use regex pattern and optional file glob (e.g., '*.java')")
    public String searchText(String pattern, String fileGlob) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return "Error: Search pattern cannot be empty.";
        }

        Pattern regex;
        try {
            regex = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return "Error: Invalid regex pattern: " + e.getMessage();
        }

        PathMatcher matcher = null;
        if (fileGlob != null && !fileGlob.trim().isEmpty()) {
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + fileGlob);
        }

        List<SearchResult> results = new ArrayList<>();
        PathMatcher finalMatcher = matcher;

        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                private int resultCount = 0;

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (resultCount >= MAX_RESULTS) {
                        return FileVisitResult.TERMINATE;
                    }

                    // Skip if file glob specified and doesn't match
                    if (finalMatcher != null && !finalMatcher.matches(file.getFileName())) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Skip binary files, hidden files, and common directories to ignore
                    if (shouldSkipFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        List<String> lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            if (regex.matcher(line).find()) {
                                Path relative = projectRoot.relativize(file);
                                results.add(new SearchResult(relative.toString(), i + 1, line.trim()));
                                resultCount++;
                                if (resultCount >= MAX_RESULTS) {
                                    return FileVisitResult.TERMINATE;
                                }
                            }
                        }
                    } catch (IOException | OutOfMemoryError e) {
                        // Skip files that can't be read or are too large
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (shouldSkipDirectory(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }

        if (results.isEmpty()) {
            return "No matches found for pattern: " + pattern;
        }

        StringBuilder output = new StringBuilder();
        output.append("Found ").append(results.size()).append(" matches");
        if (results.size() >= MAX_RESULTS) {
            output.append(" (limited to ").append(MAX_RESULTS).append(")");
        }
        output.append(":\n\n");

        for (SearchResult result : results) {
            output.append(result.file).append(":").append(result.lineNumber)
                  .append(": ").append(result.line).append("\n");
        }

        return output.toString();
    }

    @Tool("Search for files by name pattern (e.g., '*Controller.java' or 'test*')")
    public String searchFiles(String namePattern) {
        if (namePattern == null || namePattern.trim().isEmpty()) {
            return "Error: Name pattern cannot be empty.";
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + namePattern);
        List<String> results = new ArrayList<>();

        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= MAX_RESULTS) {
                        return FileVisitResult.TERMINATE;
                    }

                    if (matcher.matches(file.getFileName())) {
                        Path relative = projectRoot.relativize(file);
                        results.add(relative.toString());
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (shouldSkipDirectory(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }

        if (results.isEmpty()) {
            return "No files found matching pattern: " + namePattern;
        }

        StringBuilder output = new StringBuilder();
        output.append("Found ").append(results.size()).append(" files");
        if (results.size() >= MAX_RESULTS) {
            output.append(" (limited to ").append(MAX_RESULTS).append(")");
        }
        output.append(":\n\n");

        for (String file : results) {
            output.append(file).append("\n");
        }

        return output.toString();
    }

    @Tool("Semantic search in knowledge base using natural language query. Returns top 5 relevant results")
    public String semanticSearch(String query) {
        return "Semantic search is currently disabled due to missing VectorStoreService.";
    }

    // ========== Helper Methods ========== 

    private boolean shouldSkipFile(Path file) {
        String fileName = file.getFileName().toString();

        // Skip hidden files
        if (fileName.startsWith(".")) {
            return true;
        }

        // Skip common binary/compiled file extensions
        String lower = fileName.toLowerCase();
        return lower.endsWith(".class") || lower.endsWith(".jar") || lower.endsWith(".war") ||
               lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz") ||
               lower.endsWith(".exe") || lower.endsWith(".dll") || lower.endsWith(".so") ||
               lower.endsWith(".dylib") || lower.endsWith(".pdf") || lower.endsWith(".png") ||
               lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") ||
               lower.endsWith(".ico") || lower.endsWith(".svg");
    }

    private boolean shouldSkipDirectory(Path dir) {
        String dirName = dir.getFileName().toString();

        // Skip hidden directories
        if (dirName.startsWith(".")) {
            return true;
        }

        // Skip common build/dependency directories
        return dirName.equals("target") || dirName.equals("build") || dirName.equals("node_modules") ||
               dirName.equals("dist") || dirName.equals("out") || dirName.equals("bin");
    }

    // Simple record to hold search results
    private record SearchResult(String file, int lineNumber, String line) {}
}