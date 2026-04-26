package io.mindspice.magenta2.ai.chat.tool.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import io.mindspice.magenta2.ai.config.user.AiConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentFileToolService {
    private static final int DEFAULT_MAX_ENTRIES = 200;
    private static final int MAX_ENTRIES = 1_000;
    private static final int DEFAULT_READ_LINES = 200;
    private static final int MAX_READ_LINES = 400;
    private static final int DEFAULT_MAX_MATCHES = 50;
    private static final int MAX_MATCHES = 100;
    private static final int MAX_CONTEXT_LINES = 5;
    private static final int HASH_LENGTH = 12;

    private final Path root;

    public AgentFileToolService(AiConfig aiConfig) throws IOException {
        if (aiConfig == null || aiConfig.dataRoot() == null) {
            throw new IllegalArgumentException("AI config dataRoot is required for file tools");
        }
        if (!Files.isDirectory(aiConfig.dataRoot())) {
            throw new IllegalArgumentException("AI config dataRoot must be an existing directory: " + aiConfig.dataRoot());
        }
        this.root = aiConfig.dataRoot().toRealPath();
    }

    AgentFileToolService(Path root) throws IOException {
        this.root = root.toRealPath();
    }

    public FileListResult list(String path, boolean recursive, Integer maxEntries) throws IOException {
        Path target = resolveExisting(path);
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return new FileListResult(displayPath(target), List.of(fileEntry(target)), false);
        }
        int limit = clamp(maxEntries, DEFAULT_MAX_ENTRIES, 1, MAX_ENTRIES);
        List<FileEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = recursive ? Files.walk(target) : Files.list(target)) {
            List<Path> paths = stream
                .filter(item -> !item.equals(target))
                .sorted(Comparator.comparing(item -> relativePath(item).toString()))
                .limit(limit + 1L)
                .toList();
            for (Path item : paths.stream().limit(limit).toList()) {
                entries.add(fileEntry(item));
            }
            return new FileListResult(displayPath(target), entries, paths.size() > limit);
        }
    }

    public FileReadResult read(String path, Integer startLine, Integer maxLines) throws IOException {
        Path target = resolveTextFile(path);
        List<String> lines = splitLines(Files.readString(target, StandardCharsets.UTF_8));
        int firstLine = clamp(startLine, 1, 1, Math.max(1, lines.size() + 1));
        int limit = clamp(maxLines, DEFAULT_READ_LINES, 1, MAX_READ_LINES);
        int fromIndex = Math.max(0, firstLine - 1);
        int toIndex = Math.min(lines.size(), fromIndex + limit);
        List<String> formattedLines = new ArrayList<>();
        for (int i = fromIndex; i < toIndex; i++) {
            formattedLines.add(formatLine(i + 1, lines.get(i)));
        }
        Integer nextStartLine = toIndex < lines.size() ? toIndex + 1 : null;
        int endLine = formattedLines.isEmpty() ? firstLine - 1 : toIndex;
        return new FileReadResult(displayPath(target), lines.size(), firstLine, endLine, nextStartLine, formattedLines);
    }

    public FileSearchResult search(
        String path,
        String query,
        boolean regex,
        boolean caseSensitive,
        Integer contextLines,
        Integer maxMatches
    ) throws IOException {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query is required");
        }
        Path target = resolveExisting(path);
        int context = clamp(contextLines, 0, 0, MAX_CONTEXT_LINES);
        int limit = clamp(maxMatches, DEFAULT_MAX_MATCHES, 1, MAX_MATCHES);
        Pattern pattern = compilePattern(query, regex, caseSensitive);
        List<Path> files = searchableFiles(target);
        List<SearchMatch> matches = new ArrayList<>();
        boolean truncated = false;
        for (Path file : files) {
            if (matches.size() >= limit) {
                truncated = true;
                break;
            }
            List<String> lines;
            try {
                lines = splitLines(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException ignored) {
                continue;
            }
            for (int i = 0; i < lines.size(); i++) {
                if (!pattern.matcher(lines.get(i)).find()) {
                    continue;
                }
                if (matches.size() >= limit) {
                    truncated = true;
                    break;
                }
                matches.add(new SearchMatch(
                    displayPath(file),
                    i + 1,
                    hashLine(lines.get(i)),
                    lines.get(i),
                    contextWindow(lines, Math.max(0, i - context), i),
                    contextWindow(lines, i + 1, Math.min(lines.size(), i + context + 1))
                ));
            }
        }
        return new FileSearchResult(matches, truncated);
    }

    public FileWriteResult write(String path, String content, boolean overwrite) throws IOException {
        Path target = resolveForWrite(path);
        boolean existed = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (existed && !overwrite) {
            throw new IllegalArgumentException("file already exists: " + displayPath(target));
        }
        if (existed && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("target is not a regular file: " + displayPath(target));
        }
        Files.createDirectories(target.getParent());
        String text = content == null ? "" : content;
        Files.writeString(target, text, StandardCharsets.UTF_8);
        return new FileWriteResult(displayPath(target), text.getBytes(StandardCharsets.UTF_8).length, !existed);
    }

    public FileReplaceResult replace(String path, String startAnchor, String endAnchor, String replacement) throws IOException {
        Path target = resolveTextFile(path);
        LineAnchor start = parseAnchor(startAnchor, "startAnchor");
        LineAnchor end = StringUtils.hasText(endAnchor) ? parseAnchor(endAnchor, "endAnchor") : start;
        if (end.lineNumber() < start.lineNumber()) {
            throw new IllegalArgumentException("endAnchor must not be before startAnchor");
        }

        String original = Files.readString(target, StandardCharsets.UTF_8);
        List<String> lines = splitLines(original);
        validateAnchor(start, lines, "startAnchor");
        validateAnchor(end, lines, "endAnchor");

        List<String> replacementLines = splitLines(replacement == null ? "" : replacement);
        List<String> updated = new ArrayList<>();
        updated.addAll(lines.subList(0, start.lineNumber() - 1));
        updated.addAll(replacementLines);
        updated.addAll(lines.subList(end.lineNumber(), lines.size()));

        String lineSeparator = detectLineSeparator(original);
        boolean finalNewline = endsWithNewline(original);
        String updatedText = String.join(lineSeparator, updated);
        if (finalNewline && !updatedText.isEmpty()) {
            updatedText += lineSeparator;
        }
        Files.writeString(target, updatedText, StandardCharsets.UTF_8);
        return new FileReplaceResult(
            displayPath(target),
            start.lineNumber(),
            end.lineNumber(),
            end.lineNumber() - start.lineNumber() + 1,
            replacementLines.size()
        );
    }

    private Path resolveExisting(String path) throws IOException {
        Path resolved = resolveNormalized(path);
        if (!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("path does not exist: " + displayPath(resolved));
        }
        Path real = resolved.toRealPath();
        if (!real.startsWith(root)) {
            throw new IllegalArgumentException("path escapes data root");
        }
        return real;
    }

    private Path resolveTextFile(String path) throws IOException {
        Path resolved = resolveExisting(path);
        if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("path is not a regular file: " + displayPath(resolved));
        }
        return resolved;
    }

    private Path resolveForWrite(String path) throws IOException {
        Path resolved = resolveNormalized(path);
        if (Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            Path real = resolved.toRealPath();
            if (!real.startsWith(root)) {
                throw new IllegalArgumentException("path escapes data root");
            }
            return real;
        }
        Path parent = resolved.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("path must have a parent directory");
        }
        Path existingParent = nearestExistingParent(parent);
        Path parentReal = existingParent.toRealPath();
        if (!parentReal.startsWith(root)) {
            throw new IllegalArgumentException("path escapes data root");
        }
        return resolved;
    }

    private Path resolveNormalized(String path) {
        String requested = StringUtils.hasText(path) ? path : ".";
        Path input = Path.of(requested);
        Path resolved = input.isAbsolute() ? input.normalize() : root.resolve(input).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("path escapes data root");
        }
        return resolved;
    }

    private Path nearestExistingParent(Path path) {
        Path current = path;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalArgumentException("path has no existing parent");
        }
        return current;
    }

    private List<Path> searchableFiles(Path target) throws IOException {
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return List.of(target);
        }
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(target)) {
            return stream
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .sorted(Comparator.comparing(path -> relativePath(path).toString()))
                .toList();
        }
    }

    private Pattern compilePattern(String query, boolean regex, boolean caseSensitive) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            return regex ? Pattern.compile(query, flags) : Pattern.compile(Pattern.quote(query), flags);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("invalid regex query: " + exception.getMessage(), exception);
        }
    }

    private List<String> contextWindow(List<String> lines, int fromInclusive, int toExclusive) {
        List<String> window = new ArrayList<>();
        for (int i = fromInclusive; i < toExclusive; i++) {
            window.add(formatLine(i + 1, lines.get(i)));
        }
        return window;
    }

    private void validateAnchor(LineAnchor anchor, List<String> lines, String name) {
        if (anchor.lineNumber() < 1 || anchor.lineNumber() > lines.size()) {
            throw new IllegalArgumentException(name + " line is out of range");
        }
        String actualHash = hashLine(lines.get(anchor.lineNumber() - 1));
        if (!actualHash.equals(anchor.hash())) {
            throw new IllegalArgumentException(name + " hash does not match current file content");
        }
    }

    private LineAnchor parseAnchor(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        String[] parts = value.trim().split(":", 2);
        if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            throw new IllegalArgumentException(name + " must use lineNumber:hash format");
        }
        try {
            return new LineAnchor(Integer.parseInt(parts[0]), parts[1].toLowerCase(Locale.ROOT));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " line number is invalid", exception);
        }
    }

    private String formatLine(int lineNumber, String content) {
        return lineNumber + ":" + hashLine(content) + "|" + content;
    }

    private String hashLine(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, HASH_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.of(normalized.split("\n", -1));
    }

    private String detectLineSeparator(String content) {
        if (content != null && content.contains("\r\n")) {
            return "\r\n";
        }
        return "\n";
    }

    private boolean endsWithNewline(String content) {
        return content != null && (content.endsWith("\n") || content.endsWith("\r"));
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        int actual = value == null ? defaultValue : value;
        return Math.min(max, Math.max(min, actual));
    }

    private Path relativePath(Path path) {
        return root.relativize(path.toAbsolutePath().normalize());
    }

    private String displayPath(Path path) {
        Path relative = relativePath(path);
        return relative.toString().isEmpty() ? "." : relative.toString();
    }

    private FileEntry fileEntry(Path path) throws IOException {
        return new FileEntry(
            displayPath(path),
            Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? "directory" : "file",
            Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ? Files.size(path) : null
        );
    }

    private record LineAnchor(int lineNumber, String hash) {
    }

    public record FileEntry(String path, String type, Long size) {
    }

    public record FileListResult(String path, List<FileEntry> entries, boolean truncated) {
    }

    public record FileReadResult(
        String path,
        int totalLines,
        int startLine,
        int endLine,
        Integer nextStartLine,
        List<String> lines
    ) {
    }

    public record SearchMatch(
        String path,
        int lineNumber,
        String hash,
        String line,
        List<String> before,
        List<String> after
    ) {
    }

    public record FileSearchResult(List<SearchMatch> matches, boolean truncated) {
    }

    public record FileWriteResult(String path, int bytesWritten, boolean created) {
    }

    public record FileReplaceResult(String path, int startLine, int endLine, int replacedLines, int newLines) {
    }
}
