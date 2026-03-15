package io.mindspice.magenta.runtime.tools.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mindspice.magenta.runtime.tools.ToolExecutionSettings;
import io.mindspice.magenta.runtime.tools.ToolPathSupport;
import io.mindspice.magenta.runtime.tools.ToolPayloads;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public final class FileTools {

    private static final ObjectMapper MAPPER = ToolPayloads.mapper();
    private static final Pattern ANCHOR_PATTERN = Pattern.compile("^(\\d+):([0-9a-z]{2})$");
    private static final int DEFAULT_MAX_GREP_MATCHES = 200;
    private static final int DEFAULT_MAX_LIST_ENTRIES = 200;

    private final ToolExecutionSettings settings;

    public FileTools(ToolExecutionSettings settings) {
        this.settings = settings;
    }

    public ToolResult readFile(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String pathText = readFirstString(args, List.of("path", "filePath", "targetPath"));
        if (isBlank(pathText)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: path", null, true);
        }

        int startLine = intValue(args.get("startLine"), 1);
        int endLine = intValue(args.get("endLine"), Integer.MAX_VALUE);
        if (startLine <= 0 || endLine <= 0 || endLine < startLine) {
            return ToolPayloads.failure(request, "validation_error", "Invalid line range", null, true);
        }

        Path path;
        try {
            path = ToolPathSupport.resolveWorkspacePath(settings.workspaceRoot(), settings.enforceWorkspaceRoot(), pathText);
        } catch (IllegalArgumentException e) {
            return ToolPayloads.failure(request, "validation_error", e.getMessage(), null, true);
        }

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ToolPayloads.failure(
                    request,
                    "not_found",
                    "File not found: " + ToolPathSupport.displayPath(settings.workspaceRoot(), path),
                    null,
                    true
            );
        }

        try {
            String normalized = normalizedFileContent(path);
            List<String> lines = normalizedLines(normalized);
            int totalLines = lines.size();

            int from = Math.max(1, startLine);
            int requestedEnd = Math.min(endLine, totalLines);
            int cappedEnd = Math.min(requestedEnd, from + settings.maxFileReadLines() - 1);
            if (from > totalLines) {
                from = totalLines + 1;
                cappedEnd = totalLines;
            }

            boolean truncated = requestedEnd > cappedEnd;

            ArrayNode lineNodes = MAPPER.createArrayNode();
            StringBuilder renderedText = new StringBuilder();
            for (int lineNo = from; lineNo <= cappedEnd && lineNo <= totalLines; lineNo++) {
                String line = lines.get(lineNo - 1);
                String hash = hashLineToken(line);
                if (!renderedText.isEmpty()) {
                    renderedText.append('\n');
                }
                renderedText.append(line);

                ObjectNode lineNode = MAPPER.createObjectNode();
                lineNode.put("lineNumber", lineNo);
                lineNode.put("hash", hash);
                lineNode.put("anchor", anchorFor(lineNo, hash));
                lineNode.put("text", line);
                lineNodes.add(lineNode);
            }

            ObjectNode data = MAPPER.createObjectNode();
            data.put("path", ToolPathSupport.displayPath(settings.workspaceRoot(), path));
            data.put("snapshotId", snapshotId(normalized));
            data.put("totalLines", totalLines);
            data.put("returnedLines", lineNodes.size());
            data.put("returnedStartLine", lineNodes.isEmpty() ? 0 : from);
            data.put("returnedEndLine", lineNodes.isEmpty() ? 0 : cappedEnd);
            data.put("bytesRead", renderedText.toString().getBytes(StandardCharsets.UTF_8).length);
            data.put("truncated", truncated);
            data.set("lines", lineNodes);
            return ToolPayloads.success(request, "File read completed", data);
        } catch (Exception e) {
            return ToolPayloads.failure(request, "io_error", "Failed to read file: " + e.getMessage(), null, true);
        }
    }

    public ToolResult listDirectory(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String pathText = readFirstString(args, List.of("path", "rootPath", "targetPath"));
        if (isBlank(pathText)) {
            pathText = ".";
        }
        int maxEntries = intValue(args.get("maxEntries"), DEFAULT_MAX_LIST_ENTRIES);
        if (maxEntries <= 0) {
            return ToolPayloads.failure(request, "validation_error", "maxEntries must be > 0", null, true);
        }
        maxEntries = Math.min(maxEntries, DEFAULT_MAX_LIST_ENTRIES);
        boolean includeHidden = boolValue(args.get("includeHidden"), false);

        Path path;
        try {
            path = ToolPathSupport.resolveWorkspacePath(settings.workspaceRoot(), settings.enforceWorkspaceRoot(), pathText);
        } catch (IllegalArgumentException e) {
            return ToolPayloads.failure(request, "validation_error", e.getMessage(), null, true);
        }

        if (!Files.exists(path)) {
            return ToolPayloads.failure(
                    request,
                    "not_found",
                    "Path not found: " + ToolPathSupport.displayPath(settings.workspaceRoot(), path),
                    null,
                    true
            );
        }
        if (!Files.isDirectory(path)) {
            return ToolPayloads.failure(
                    request,
                    "validation_error",
                    "Path is not a directory: " + ToolPathSupport.displayPath(settings.workspaceRoot(), path),
                    null,
                    true
            );
        }

        ArrayNode entries = MAPPER.createArrayNode();
        boolean truncated = false;

        try (var stream = Files.list(path)) {
            List<Path> listed = stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
            for (Path entry : listed) {
                String name = entry.getFileName() == null ? entry.toString() : entry.getFileName().toString();
                if (!includeHidden && name.startsWith(".")) {
                    continue;
                }
                if (entries.size() >= maxEntries) {
                    truncated = true;
                    break;
                }

                ObjectNode node = MAPPER.createObjectNode();
                node.put("name", name);
                node.put("path", ToolPathSupport.displayPath(settings.workspaceRoot(), entry));
                node.put("directory", Files.isDirectory(entry));
                node.put("regularFile", Files.isRegularFile(entry));
                node.put("symbolicLink", Files.isSymbolicLink(entry));
                node.put("sizeBytes", safeSize(entry));
                node.put("lastModifiedMs", safeLastModifiedMs(entry));
                entries.add(node);
            }
        } catch (Exception e) {
            return ToolPayloads.failure(request, "io_error", "Failed to list directory: " + e.getMessage(), null, true);
        }

        ObjectNode data = MAPPER.createObjectNode();
        data.put("path", ToolPathSupport.displayPath(settings.workspaceRoot(), path));
        data.put("entryCount", entries.size());
        data.put("truncated", truncated);
        data.set("entries", entries);
        return ToolPayloads.success(request, "Directory listed", data);
    }

    public ToolResult fileMetadata(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String pathText = readFirstString(args, List.of("path", "filePath", "targetPath"));
        if (isBlank(pathText)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: path", null, true);
        }

        Path path;
        try {
            path = ToolPathSupport.resolveWorkspacePath(settings.workspaceRoot(), settings.enforceWorkspaceRoot(), pathText);
        } catch (IllegalArgumentException e) {
            return ToolPayloads.failure(request, "validation_error", e.getMessage(), null, true);
        }

        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return ToolPayloads.failure(
                    request,
                    "not_found",
                    "Path not found: " + ToolPathSupport.displayPath(settings.workspaceRoot(), path),
                    null,
                    true
            );
        }

        try {
            ObjectNode data = MAPPER.createObjectNode();
            data.put("path", ToolPathSupport.displayPath(settings.workspaceRoot(), path));
            data.put("directory", Files.isDirectory(path));
            data.put("regularFile", Files.isRegularFile(path));
            data.put("symbolicLink", Files.isSymbolicLink(path));
            data.put("sizeBytes", safeSize(path));
            data.put("lastModifiedMs", safeLastModifiedMs(path));
            data.put("readable", Files.isReadable(path));
            data.put("writable", Files.isWritable(path));
            data.put("executable", Files.isExecutable(path));
            if (Files.isSymbolicLink(path)) {
                Path target = Files.readSymbolicLink(path);
                data.put("symlinkTarget", target.toString());
            }
            return ToolPayloads.success(request, "Metadata collected", data);
        } catch (Exception e) {
            return ToolPayloads.failure(request, "io_error", "Failed to read metadata: " + e.getMessage(), null, true);
        }
    }

    public ToolResult grepFiles(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String patternText = readFirstString(args, List.of("pattern", "query"));
        if (isBlank(patternText)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: pattern", null, true);
        }

        String rootPathText = readFirstString(args, List.of("rootPath", "path"));
        if (isBlank(rootPathText)) {
            rootPathText = ".";
        }

        Path rootPath;
        try {
            rootPath = ToolPathSupport.resolveWorkspacePath(settings.workspaceRoot(), settings.enforceWorkspaceRoot(), rootPathText);
        } catch (IllegalArgumentException e) {
            return ToolPayloads.failure(request, "validation_error", e.getMessage(), null, true);
        }

        if (!Files.isDirectory(rootPath)) {
            return ToolPayloads.failure(
                    request,
                    "validation_error",
                    "rootPath is not a directory: " + ToolPathSupport.displayPath(settings.workspaceRoot(), rootPath),
                    null,
                    true
            );
        }

        boolean regex = boolValue(args.get("regex"), false);
        boolean caseSensitive = boolValue(args.get("caseSensitive"), true);
        int maxMatches = intValue(args.get("maxMatches"), DEFAULT_MAX_GREP_MATCHES);
        if (maxMatches <= 0) {
            return ToolPayloads.failure(request, "validation_error", "maxMatches must be > 0", null, true);
        }

        Pattern regexPattern = null;
        if (regex) {
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                regexPattern = Pattern.compile(patternText, flags);
            } catch (Exception e) {
                return ToolPayloads.failure(request, "validation_error", "Invalid regex pattern", null, true);
            }
        }

        String filePatternText = readFirstString(args, List.of("filePattern"));
        java.nio.file.PathMatcher fileMatcher = null;
        java.nio.file.PathMatcher fileNameMatcher = null;
        if (!isBlank(filePatternText)) {
            String normalizedPattern = filePatternText.trim().replace('\\', '/');
            try {
                fileMatcher = rootPath.getFileSystem().getPathMatcher("glob:" + normalizedPattern);
                // For basename filters (for example "fractal.lisp" or "*.lisp"),
                // also match each file name so nested files are included.
                if (!normalizedPattern.contains("/")) {
                    fileNameMatcher = rootPath.getFileSystem().getPathMatcher("glob:" + normalizedPattern);
                }
            } catch (IllegalArgumentException e) {
                return ToolPayloads.failure(request, "validation_error", "Invalid filePattern glob", null, true);
            }
        }

        ArrayNode matches = MAPPER.createArrayNode();
        int scannedFiles = 0;
        int skippedFiles = 0;
        boolean truncated = false;

        try (var walk = Files.walk(rootPath)) {
            Iterator<Path> iterator = walk.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (!Files.isRegularFile(path)) {
                    continue;
                }

                Path relativeToRoot = rootPath.relativize(path);
                if (fileMatcher != null
                        && !fileMatcher.matches(relativeToRoot)
                        && (fileNameMatcher == null || !fileNameMatcher.matches(relativeToRoot.getFileName()))) {
                    continue;
                }

                scannedFiles++;
                try {
                    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (!lineMatches(line, patternText, regexPattern, regex, caseSensitive)) {
                            continue;
                        }

                        int lineNumber = i + 1;
                        String hash = hashLineToken(line);
                        ObjectNode match = MAPPER.createObjectNode();
                        match.put("path", ToolPathSupport.displayPath(settings.workspaceRoot(), path));
                        match.put("lineNumber", lineNumber);
                        match.put("hash", hash);
                        match.put("anchor", anchorFor(lineNumber, hash));
                        match.put("line", line);
                        matches.add(match);

                        if (matches.size() >= maxMatches) {
                            truncated = true;
                            break;
                        }
                    }
                } catch (Exception ignored) {
                    skippedFiles++;
                }

                if (truncated) {
                    break;
                }
            }
        } catch (IOException e) {
            return ToolPayloads.failure(request, "io_error", "Failed to scan files: " + e.getMessage(), null, true);
        }

        ObjectNode data = MAPPER.createObjectNode();
        data.put("pattern", patternText);
        data.put("rootPath", ToolPathSupport.displayPath(settings.workspaceRoot(), rootPath));
        data.put("matchCount", matches.size());
        data.put("truncated", truncated);
        data.put("scannedFiles", scannedFiles);
        data.put("skippedFiles", skippedFiles);
        data.set("matches", matches);
        return ToolPayloads.success(request, "Search completed", data);
    }

    public ToolResult searchReplace(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String pathText = readFirstString(args, List.of("path", "filePath", "targetPath"));
        String expectedSnapshotId = readFirstString(args, List.of("snapshotId"));
        JsonNode editsNode = args.get("edits");

        if (isBlank(pathText)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: path", null, true);
        }
        if (isBlank(expectedSnapshotId)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: snapshotId", null, true);
        }
        if (editsNode == null || !editsNode.isArray() || editsNode.isEmpty()) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: edits[]", null, true);
        }

        Path path;
        try {
            path = ToolPathSupport.resolveWorkspacePath(settings.workspaceRoot(), settings.enforceWorkspaceRoot(), pathText);
        } catch (IllegalArgumentException e) {
            return ToolPayloads.failure(request, "validation_error", e.getMessage(), null, true);
        }

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ToolPayloads.failure(
                    request,
                    "not_found",
                    "File not found: " + ToolPathSupport.displayPath(settings.workspaceRoot(), path),
                    null,
                    true
            );
        }

        try {
            String normalizedBefore = normalizedFileContent(path);
            String displayPath = ToolPathSupport.displayPath(settings.workspaceRoot(), path);
            String actualSnapshotId = snapshotId(normalizedBefore);
            if (!Objects.equals(expectedSnapshotId, actualSnapshotId)) {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("path", displayPath);
                data.put("providedSnapshotId", expectedSnapshotId);
                data.put("currentSnapshotId", actualSnapshotId);
                return ToolPayloads.failure(request, "snapshot_mismatch", "Snapshot mismatch", data, true);
            }

            List<String> lines = new ArrayList<>(normalizedLines(normalizedBefore));
            List<ResolvedEdit> resolvedEdits = resolveEdits(editsNode, lines);
            if (resolvedEdits.isEmpty()) {
                return ToolPayloads.failure(request, "validation_error", "No valid edits provided", null, true);
            }

            List<ResolvedEdit> ascending = new ArrayList<>(resolvedEdits);
            ascending.sort(Comparator.comparingInt(ResolvedEdit::startLine));
            for (int i = 1; i < ascending.size(); i++) {
                ResolvedEdit previous = ascending.get(i - 1);
                ResolvedEdit current = ascending.get(i);
                if (current.startLine() <= previous.endLine()) {
                    ObjectNode conflict = conflictNode(current.index(), "overlapping_range", current.startAnchor(), current.endAnchor());
                    ObjectNode data = MAPPER.createObjectNode();
                    data.put("path", displayPath);
                    ArrayNode conflicts = MAPPER.createArrayNode();
                    conflicts.add(conflict);
                    data.set("conflicts", conflicts);
                    data.put("requiredAction", "adjust_ranges");
                    data.put("recoveryHint", "Edit ranges overlapped. Use non-overlapping inclusive anchor ranges.");
                    return ToolPayloads.failure(request, "anchor_mismatch", "Edit ranges overlap", data, true);
                }
            }

            List<ResolvedEdit> descending = new ArrayList<>(resolvedEdits);
            descending.sort(Comparator.comparingInt(ResolvedEdit::startLine).reversed());
            for (ResolvedEdit edit : descending) {
                int from = edit.startLine() - 1;
                int toExclusive = edit.endLine();
                lines.subList(from, toExclusive).clear();
                if (!edit.replacementLines().isEmpty()) {
                    lines.addAll(from, edit.replacementLines());
                }
            }

            String normalizedAfter = String.join("\n", lines);
            atomicWrite(path, normalizedAfter);

            ObjectNode data = MAPPER.createObjectNode();
            data.put("path", ToolPathSupport.displayPath(settings.workspaceRoot(), path));
            data.put("snapshotIdBefore", actualSnapshotId);
            data.put("snapshotIdAfter", snapshotId(normalizedAfter));
            data.put("appliedEdits", resolvedEdits.size());
            return ToolPayloads.success(request, "Edits applied", data);
        } catch (AnchorConflictException e) {
            ObjectNode data = MAPPER.createObjectNode();
            data.put("path", ToolPathSupport.displayPath(settings.workspaceRoot(), path));
            ArrayNode conflicts = MAPPER.createArrayNode();
            conflicts.add(conflictNode(
                    e.editIndex(),
                    e.reason(),
                    e.startAnchor(),
                    e.endAnchor(),
                    e.lineNumber(),
                    e.expectedHash(),
                    e.actualHash(),
                    e.actualSlicePreview(),
                    e.requiredAction(),
                    e.recoveryHint()
            ));
            data.set("conflicts", conflicts);
            if (!isBlank(e.requiredAction())) {
                data.put("requiredAction", e.requiredAction());
            }
            if (!isBlank(e.recoveryHint())) {
                data.put("recoveryHint", e.recoveryHint());
            }
            return ToolPayloads.failure(request, "anchor_mismatch", e.getMessage(), data, true);
        } catch (Exception e) {
            return ToolPayloads.failure(request, "io_error", "Failed to apply edits: " + e.getMessage(), null, true);
        }
    }

    public ToolResult writeFile(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String pathText = readFirstString(args, List.of("path", "filePath", "targetPath"));
        JsonNode contentNode = args.get("content");
        boolean overwrite = boolValue(args.get("overwrite"), false);
        String expectedSnapshotId = readFirstString(args, List.of("expectedSnapshotId", "snapshotId"));

        if (isBlank(pathText)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: path", null, true);
        }
        if (contentNode == null || !contentNode.isTextual()) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: content", null, true);
        }

        Path path;
        try {
            path = ToolPathSupport.resolveWorkspacePath(settings.workspaceRoot(), settings.enforceWorkspaceRoot(), pathText);
        } catch (IllegalArgumentException e) {
            return ToolPayloads.failure(request, "validation_error", e.getMessage(), null, true);
        }

        try {
            boolean exists = Files.exists(path);
            if (exists && !overwrite) {
                return ToolPayloads.failure(request, "overwrite_guard", "File exists and overwrite=false", null, true);
            }

            if (!isBlank(expectedSnapshotId)) {
                String current = exists ? snapshotId(normalizedFileContent(path)) : "";
                if (!Objects.equals(current, expectedSnapshotId)) {
                    ObjectNode data = MAPPER.createObjectNode();
                    data.put("path", ToolPathSupport.displayPath(settings.workspaceRoot(), path));
                    data.put("providedSnapshotId", expectedSnapshotId);
                    data.put("currentSnapshotId", current);
                    return ToolPayloads.failure(request, "snapshot_mismatch", "Snapshot mismatch", data, true);
                }
            }

            String normalizedContent = normalizeNewlines(contentNode.asText());
            atomicWrite(path, normalizedContent);

            ObjectNode data = MAPPER.createObjectNode();
            data.put("path", ToolPathSupport.displayPath(settings.workspaceRoot(), path));
            data.put("bytesWritten", normalizedContent.getBytes(StandardCharsets.UTF_8).length);
            data.put("snapshotId", snapshotId(normalizedContent));
            data.put("overwrote", exists);
            return ToolPayloads.success(request, "File written", data);
        } catch (Exception e) {
            return ToolPayloads.failure(request, "io_error", "Failed to write file: " + e.getMessage(), null, true);
        }
    }

    public ToolResult deleteFile(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String pathText = readFirstString(args, List.of("path", "filePath", "targetPath"));
        String expectedSnapshotId = readFirstString(args, List.of("expectedSnapshotId", "snapshotId"));
        if (isBlank(pathText)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: path", null, true);
        }

        Path path;
        try {
            path = ToolPathSupport.resolveWorkspacePath(settings.workspaceRoot(), settings.enforceWorkspaceRoot(), pathText);
        } catch (IllegalArgumentException e) {
            return ToolPayloads.failure(request, "validation_error", e.getMessage(), null, true);
        }

        try {
            if (!Files.exists(path)) {
                return ToolPayloads.failure(
                        request,
                        "not_found",
                        "File not found: " + ToolPathSupport.displayPath(settings.workspaceRoot(), path),
                        null,
                        true
                );
            }
            if (!Files.isRegularFile(path)) {
                return ToolPayloads.failure(
                        request,
                        "validation_error",
                        "Path is not a regular file: " + ToolPathSupport.displayPath(settings.workspaceRoot(), path),
                        null,
                        true
                );
            }

            String normalizedBefore = normalizedFileContent(path);
            String currentSnapshotId = snapshotId(normalizedBefore);
            if (!isBlank(expectedSnapshotId) && !Objects.equals(expectedSnapshotId, currentSnapshotId)) {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("path", ToolPathSupport.displayPath(settings.workspaceRoot(), path));
                data.put("providedSnapshotId", expectedSnapshotId);
                data.put("currentSnapshotId", currentSnapshotId);
                return ToolPayloads.failure(request, "snapshot_mismatch", "Snapshot mismatch", data, true);
            }

            long bytesDeleted = Files.size(path);
            Files.delete(path);

            ObjectNode data = MAPPER.createObjectNode();
            data.put("path", ToolPathSupport.displayPath(settings.workspaceRoot(), path));
            data.put("bytesDeleted", bytesDeleted);
            data.put("snapshotIdBefore", currentSnapshotId);
            return ToolPayloads.success(request, "File deleted", data);
        } catch (Exception e) {
            return ToolPayloads.failure(request, "io_error", "Failed to delete file: " + e.getMessage(), null, true);
        }
    }

    private JsonNode readArgsOrNull(ToolRequest request) {
        String argsJson = request.toolCall().argumentsJson();
        if (isBlank(argsJson)) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(argsJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<ResolvedEdit> resolveEdits(JsonNode editsNode, List<String> lines) {
        List<ResolvedEdit> resolved = new ArrayList<>();
        for (int i = 0; i < editsNode.size(); i++) {
            JsonNode editNode = editsNode.get(i);
            if (editNode == null || !editNode.isObject()) {
                throw new AnchorConflictException(i, "invalid_edit", "Edit must be an object", "", "");
            }

            String startAnchor = readFirstString(editNode, List.of("startAnchor", "start", "fromAnchor"));
            String endAnchor = readFirstString(editNode, List.of("endAnchor", "end", "toAnchor"));
            JsonNode replacementNode = readFirstTextNode(editNode, List.of("replacement", "text"));
            if (isBlank(startAnchor) || isBlank(endAnchor) || replacementNode == null || !replacementNode.isTextual()) {
                throw new AnchorConflictException(i, "invalid_edit", "Each edit requires startAnchor, endAnchor, replacement", startAnchor, endAnchor);
            }

            Anchor start = parseAnchor(startAnchor, i);
            Anchor end = parseAnchor(endAnchor, i);

            if (start.lineNumber() > end.lineNumber()) {
                throw new AnchorConflictException(i, "invalid_range", "startAnchor must be <= endAnchor", startAnchor, endAnchor);
            }
            if (start.lineNumber() <= 0 || end.lineNumber() <= 0 || end.lineNumber() > lines.size()) {
                throw new AnchorConflictException(i, "anchor_out_of_bounds", "Anchor line out of bounds", startAnchor, endAnchor);
            }

            String startActualHash = hashLineToken(lines.get(start.lineNumber() - 1));
            if (!Objects.equals(start.hash(), startActualHash)) {
                throw new AnchorConflictException(
                        i,
                        "start_anchor_mismatch",
                        anchorMismatchMessage("startAnchor", start.lineNumber(), start.hash(), startActualHash),
                        startAnchor,
                        endAnchor,
                        start.lineNumber(),
                        start.hash(),
                        startActualHash
                );
            }

            String endActualHash = hashLineToken(lines.get(end.lineNumber() - 1));
            if (!Objects.equals(end.hash(), endActualHash)) {
                throw new AnchorConflictException(
                        i,
                        "end_anchor_mismatch",
                        anchorMismatchMessage("endAnchor", end.lineNumber(), end.hash(), endActualHash),
                        startAnchor,
                        endAnchor,
                        end.lineNumber(),
                        end.hash(),
                        endActualHash
                );
            }

            String expectedText = readFirstString(editNode, List.of("expectedText", "expected"));
            if (!isBlank(expectedText)) {
                String expectedNormalized = normalizeNewlines(expectedText);
                String actualSlice = String.join("\n", lines.subList(start.lineNumber() - 1, end.lineNumber()));
                if (!Objects.equals(expectedNormalized, actualSlice)) {
                    throw new AnchorConflictException(
                            i,
                            "expected_text_mismatch",
                            "expectedText does not match anchored inclusive range",
                            startAnchor,
                            endAnchor,
                            null,
                            null,
                            null,
                            slicePreview(actualSlice),
                            "read_file_refresh",
                            "Run read_file for this path and retry with inclusive anchors; include expectedText only when exact slice is known."
                    );
                }
            }

            resolved.add(new ResolvedEdit(
                    i,
                    start.lineNumber(),
                    end.lineNumber(),
                    startAnchor,
                    endAnchor,
                    replacementLines(replacementNode.asText())
            ));
        }
        return resolved;
    }

    private Anchor parseAnchor(String anchorText, int editIndex) {
        Matcher matcher = ANCHOR_PATTERN.matcher(anchorText.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new AnchorConflictException(editIndex, "invalid_anchor", "Anchor format must be line:hh", anchorText, anchorText);
        }

        int lineNumber;
        try {
            lineNumber = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new AnchorConflictException(editIndex, "invalid_anchor", "Anchor line is invalid", anchorText, anchorText);
        }
        return new Anchor(lineNumber, matcher.group(2));
    }

    private String readFirstString(JsonNode args, List<String> keys) {
        if (args == null || !args.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode node = args.get(key);
            if (node != null && node.isTextual()) {
                return node.asText();
            }
        }
        return null;
    }

    private JsonNode readFirstTextNode(JsonNode args, List<String> keys) {
        if (args == null || !args.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode node = args.get(key);
            if (node != null && node.isTextual()) {
                return node;
            }
        }
        return null;
    }

    private boolean boolValue(JsonNode node, boolean defaultValue) {
        return node == null || !node.isBoolean() ? defaultValue : node.asBoolean();
    }

    private int intValue(JsonNode node, int defaultValue) {
        return node == null || !node.canConvertToInt() ? defaultValue : node.asInt();
    }

    private String normalizedFileContent(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return normalizeNewlines(text);
    }

    private void atomicWrite(Path path, String normalizedContent) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = (parent == null ? Path.of(".") : parent)
                .resolve(path.getFileName().toString() + ".tmp." + UUID.randomUUID());
        try {
            Files.writeString(temp, normalizedContent, StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private boolean lineMatches(
            String line,
            String patternText,
            Pattern regexPattern,
            boolean regex,
            boolean caseSensitive
    ) {
        if (regex && regexPattern != null) {
            return regexPattern.matcher(line).find();
        }
        if (caseSensitive) {
            return line.contains(patternText);
        }
        return line.toLowerCase(Locale.ROOT).contains(patternText.toLowerCase(Locale.ROOT));
    }

    private ObjectNode conflictNode(int editIndex, String reason, String startAnchor, String endAnchor) {
        return conflictNode(editIndex, reason, startAnchor, endAnchor, null, null, null, null, null, null);
    }

    private ObjectNode conflictNode(
            int editIndex,
            String reason,
            String startAnchor,
            String endAnchor,
            Integer lineNumber,
            String expectedHash,
            String actualHash,
            String actualSlicePreview,
            String requiredAction,
            String recoveryHint
    ) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("editIndex", editIndex);
        node.put("reason", reason);
        node.put("startAnchor", startAnchor == null ? "" : startAnchor);
        node.put("endAnchor", endAnchor == null ? "" : endAnchor);
        if (lineNumber != null) {
            node.put("lineNumber", lineNumber);
        }
        if (!isBlank(expectedHash)) {
            node.put("expectedHash", expectedHash);
        }
        if (!isBlank(actualHash)) {
            node.put("actualHash", actualHash);
        }
        if (!isBlank(actualSlicePreview)) {
            node.put("actualSlicePreview", actualSlicePreview);
        }
        if (!isBlank(requiredAction)) {
            node.put("requiredAction", requiredAction);
        }
        if (!isBlank(recoveryHint)) {
            node.put("recoveryHint", recoveryHint);
        }
        return node;
    }

    private String slicePreview(String text) {
        String normalized = normalizeNewlines(text == null ? "" : text);
        int max = 180;
        if (normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, max - 3) + "...";
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private long safeLastModifiedMs(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static String normalizeNewlines(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static List<String> normalizedLines(String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty()) {
            return List.of();
        }

        String[] split = normalizedText.split("\\n", -1);
        if (normalizedText.endsWith("\n") && split.length > 0) {
            split = Arrays.copyOf(split, split.length - 1);
        }
        return new ArrayList<>(Arrays.asList(split));
    }

    private static List<String> replacementLines(String replacementText) {
        String normalized = normalizeNewlines(replacementText);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(Arrays.asList(normalized.split("\\n", -1)));
    }

    private static String hashLineToken(String lineText) {
        CRC32 crc32 = new CRC32();
        byte[] bytes = (lineText == null ? "" : lineText).getBytes(StandardCharsets.UTF_8);
        crc32.update(bytes, 0, bytes.length);
        long reduced = crc32.getValue() % (36L * 36L);
        String base36 = Long.toString(reduced, 36);
        if (base36.length() == 1) {
            return "0" + base36;
        }
        if (base36.length() > 2) {
            return base36.substring(base36.length() - 2);
        }
        return base36;
    }

    private static String anchorFor(int lineNumber, String hash) {
        return lineNumber + ":" + hash;
    }

    private static String anchorMismatchMessage(String anchorName, int lineNumber, String expectedHash, String actualHash) {
        return anchorName + " hash mismatch at line " + lineNumber
                + " (expected " + expectedHash + ", actual " + actualHash + ")";
    }

    private static String snapshotId(String normalizedText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest((normalizedText == null ? "" : normalizedText).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute snapshot id", e);
        }
    }

    private record Anchor(int lineNumber, String hash) {
    }

    private record ResolvedEdit(
            int index,
            int startLine,
            int endLine,
            String startAnchor,
            String endAnchor,
            List<String> replacementLines
    ) {
    }

    private static final class AnchorConflictException extends RuntimeException {
        private final int editIndex;
        private final String reason;
        private final String startAnchor;
        private final String endAnchor;
        private final Integer lineNumber;
        private final String expectedHash;
        private final String actualHash;
        private final String actualSlicePreview;
        private final String requiredAction;
        private final String recoveryHint;

        private AnchorConflictException(int editIndex, String reason, String message, String startAnchor, String endAnchor) {
            this(editIndex, reason, message, startAnchor, endAnchor, null, null, null, null, null, null);
        }

        private AnchorConflictException(
                int editIndex,
                String reason,
                String message,
                String startAnchor,
                String endAnchor,
                Integer lineNumber,
                String expectedHash,
                String actualHash
        ) {
            this(editIndex, reason, message, startAnchor, endAnchor, lineNumber, expectedHash, actualHash, null, null, null);
        }

        private AnchorConflictException(
                int editIndex,
                String reason,
                String message,
                String startAnchor,
                String endAnchor,
                Integer lineNumber,
                String expectedHash,
                String actualHash,
                String actualSlicePreview,
                String requiredAction,
                String recoveryHint
        ) {
            super(message);
            this.editIndex = editIndex;
            this.reason = reason;
            this.startAnchor = startAnchor;
            this.endAnchor = endAnchor;
            this.lineNumber = lineNumber;
            this.expectedHash = expectedHash;
            this.actualHash = actualHash;
            this.actualSlicePreview = actualSlicePreview;
            this.requiredAction = requiredAction;
            this.recoveryHint = recoveryHint;
        }

        private int editIndex() {
            return editIndex;
        }

        private String reason() {
            return reason;
        }

        private String startAnchor() {
            return startAnchor;
        }

        private String endAnchor() {
            return endAnchor;
        }

        private Integer lineNumber() {
            return lineNumber;
        }

        private String expectedHash() {
            return expectedHash;
        }

        private String actualHash() {
            return actualHash;
        }

        private String actualSlicePreview() {
            return actualSlicePreview;
        }

        private String requiredAction() {
            return requiredAction;
        }

        private String recoveryHint() {
            return recoveryHint;
        }
    }
}
