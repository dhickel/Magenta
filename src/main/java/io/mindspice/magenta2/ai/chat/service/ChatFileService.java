package io.mindspice.magenta2.ai.chat.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import io.mindspice.magenta2.ai.chat.model.ChatFileListing;
import io.mindspice.magenta2.ai.chat.model.ChatFileSummary;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatFileService {
    public static final int MAX_LIST_FILES = 500;

    private final WorkspaceDirectoryService workspaceDirectoryService;

    public ChatFileService(WorkspaceDirectoryService workspaceDirectoryService) {
        this.workspaceDirectoryService = workspaceDirectoryService;
    }

    public int countFiles(String conversationId) {
        try (Stream<Path> paths = Files.walk(root(conversationId))) {
            long count = paths
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .count();
            return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    public ChatFileListing listFiles(String conversationId) {
        Path root = root(conversationId);
        int totalCount = countFiles(conversationId);
        try (Stream<Path> paths = Files.walk(root)) {
            List<ChatFileSummary> files = paths
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .sorted(Comparator.comparing(path -> relativePath(root, path)))
                .limit(MAX_LIST_FILES)
                .map(path -> summarize(root, path))
                .toList();
            return new ChatFileListing(conversationId, totalCount, totalCount > files.size(), files);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Failed to list chat files", e);
        }
    }

    public Path resolveDownload(String conversationId, String relativePath) throws IOException {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("file path is required");
        }
        Path root = root(conversationId).toRealPath();
        Path target = root.resolve(relativePath.replace('\\', '/')).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("file path escapes chat files");
        }
        Path real = target.toRealPath();
        if (!real.startsWith(root) || !Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("chat file not found");
        }
        return real;
    }

    private Path root(String conversationId) {
        return workspaceDirectoryService.chatFiles(conversationId);
    }

    private ChatFileSummary summarize(Path root, Path path) {
        String fileName = path.getFileName().toString();
        String extension = extension(fileName);
        try {
            return new ChatFileSummary(
                relativePath(root, path),
                fileName,
                extension,
                formatLabel(extension),
                Files.size(path),
                Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant()
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to summarize chat file", e);
        }
    }

    private String relativePath(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index <= 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String formatLabel(String extension) {
        return switch (extension) {
            case "md" -> "Markdown";
            case "txt" -> "Text";
            case "json" -> "JSON";
            case "csv" -> "CSV";
            case "html", "htm" -> "HTML";
            case "xml" -> "XML";
            case "png", "jpg", "jpeg", "gif", "webp" -> "Image";
            case "pdf" -> "PDF";
            case "" -> "file";
            default -> extension.toUpperCase(Locale.ROOT);
        };
    }
}
