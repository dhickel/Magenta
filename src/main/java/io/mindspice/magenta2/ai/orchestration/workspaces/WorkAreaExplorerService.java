package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkAreaExplorerService {
    private static final long MAX_TEXT_BYTES = 1024 * 1024;
    private static final long MAX_PREVIEW_BYTES = 256 * 1024;

    private final WorkAreaService workAreaService;

    public WorkAreaExplorerService(WorkAreaService workAreaService) {
        this.workAreaService = workAreaService;
    }

    public DirectoryListing list(String workAreaId, String relativePath) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path dir = resolveExisting(root, relativePath);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("path is not a directory");
        }
        try (var stream = Files.list(dir)) {
            List<Entry> entries = stream
                .map(path -> entry(root, path))
                .sorted(Comparator.comparing(Entry::directory).reversed().thenComparing(Entry::name))
                .toList();
            return new DirectoryListing(area, root.relativize(dir).toString().replace('\\', '/'), entries);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to list directory", exception);
        }
    }

    public FilePreview preview(String workAreaId, String relativePath) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path file = resolveExisting(root, relativePath);
        requireRegularFile(file);
        try {
            long size = Files.size(file);
            if (size > MAX_PREVIEW_BYTES || !isSafeTextPath(file)) {
                return new FilePreview(root.relativize(file).toString().replace('\\', '/'), size, false, null);
            }
            return new FilePreview(
                root.relativize(file).toString().replace('\\', '/'),
                size,
                true,
                Files.readString(file, StandardCharsets.UTF_8)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("failed to preview file", exception);
        }
    }

    public Path download(String workAreaId, String relativePath) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path file = resolveExisting(root, relativePath);
        requireRegularFile(file);
        return file;
    }

    public FilePreview saveText(String workAreaId, String relativePath, String content) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path file = resolveForWrite(root, relativePath);
        if (!isSafeTextPath(file)) {
            throw new IllegalArgumentException("file type is not safe for text editing");
        }
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("text file is too large");
        }
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("path is not a regular file");
            }
            Files.write(file, bytes);
            return preview(workAreaId, relativePath);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to save text file", exception);
        }
    }

    public Entry createDirectory(String workAreaId, String relativePath) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path dir = resolveForWrite(root, relativePath);
        try {
            Path created = Files.createDirectories(dir).toRealPath();
            if (!created.startsWith(root)) {
                throw new IllegalArgumentException("directory escapes Work Area");
            }
            return entry(root, created);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to create directory", exception);
        }
    }

    public Entry rename(String workAreaId, String relativePath, String newName) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path source = resolveExisting(root, relativePath);
        requirePlainName(newName);
        Path target = source.getParent().resolve(newName.trim()).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("rename target escapes Work Area");
        }
        ensureNotProtected(area, root, source);
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            return entry(root, target.toRealPath());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to rename path", exception);
        }
    }

    public DeleteResult deleteRecursive(String workAreaId, String relativePath, String confirmation) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path target = resolveExisting(root, relativePath);
        String expected = target.getFileName() == null ? "" : target.getFileName().toString();
        if (!expected.equals(confirmation)) {
            throw new IllegalArgumentException("typed confirmation must match path name");
        }
        ensureNotProtected(area, root, target);
        try (var walk = Files.walk(target)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Path real = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
                if (!real.startsWith(root)) {
                    throw new IllegalArgumentException("delete path escapes Work Area");
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("symbolic links are not deleted through Work Area explorer");
                }
            }
            for (Path path : paths) {
                Files.delete(path);
            }
            return new DeleteResult(root.relativize(target).toString().replace('\\', '/'), paths.size());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to delete path", exception);
        }
    }

    public WorkArea mark(String workAreaId, String relativePath, String displayName) {
        WorkArea area = workAreaService.get(workAreaId);
        String areaPath = join(area.areaRelativePath(), relativePath);
        return workAreaService.markDirectory(area.ownerType(), area.ownerId(), areaPath, displayName);
    }

    private Entry entry(Path root, Path path) {
        try {
            boolean directory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
            boolean regular = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
            long size = regular ? Files.size(path) : 0;
            Instant modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
            String relative = root.relativize(path).toString().replace('\\', '/');
            return new Entry(path.getFileName().toString(), relative, directory, regular, size, modified);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to inspect path", exception);
        }
    }

    private Path resolveExisting(Path root, String relativePath) {
        Path resolved = resolveNormalized(root, relativePath);
        try {
            rejectSymbolicPath(root, resolved);
            Path real = resolved.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!real.startsWith(root)) {
                throw new IllegalArgumentException("path escapes Work Area");
            }
            return real;
        } catch (IOException exception) {
            throw new IllegalArgumentException("path does not exist: " + relativePath, exception);
        }
    }

    private Path resolveForWrite(Path root, String relativePath) {
        Path resolved = resolveNormalized(root, relativePath);
        Path parent = resolved.getParent();
        try {
            Path realParent = (parent == null ? root : Files.createDirectories(parent).toRealPath());
            if (!realParent.startsWith(root)) {
                throw new IllegalArgumentException("path escapes Work Area");
            }
            rejectSymbolicPath(root, parent == null ? root : parent);
            if (Files.exists(resolved, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(resolved)) {
                throw new IllegalArgumentException("symbolic links are not editable through Work Area explorer");
            }
            return resolved;
        } catch (IOException exception) {
            throw new IllegalArgumentException("path cannot be created: " + relativePath, exception);
        }
    }

    private Path resolveNormalized(Path root, String relativePath) {
        String text = StringUtils.hasText(relativePath) ? relativePath.trim().replace('\\', '/') : ".";
        Path path = Path.of(text).normalize();
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("absolute paths are not allowed");
        }
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                throw new IllegalArgumentException("path escapes Work Area");
            }
        }
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("path escapes Work Area");
        }
        return resolved;
    }

    private void ensureNotProtected(WorkArea area, Path root, Path target) {
        if (target.equals(root)) {
            throw new IllegalArgumentException("Work Area root is protected");
        }
        for (WorkArea workArea : workAreaService.list(area.ownerType(), area.ownerId(), false)) {
            Path workAreaPath = workAreaService.resolve(workArea);
            if (target.equals(workAreaPath) || workAreaPath.startsWith(target)) {
                if (workArea.system() || workArea.home()) {
                    throw new IllegalArgumentException("Home/system Work Areas are protected");
                }
                if (!workArea.id().equals(area.id())) {
                    throw new IllegalArgumentException("active Work Area paths are protected");
                }
                if (workAreaService.hasActiveWorkReferences(workArea.id())) {
                    throw new IllegalArgumentException("Work Area is active in queued or running work: " + workArea.id());
                }
            }
        }
    }

    private void rejectSymbolicPath(Path root, Path path) throws IOException {
        Path normalized = path.normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("path escapes Work Area");
        }
        Path current = root;
        Path relative = root.relativize(normalized);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("symbolic links are not allowed in Work Area explorer paths");
            }
        }
    }

    private void requireRegularFile(Path file) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("path is not a regular file");
        }
    }

    private boolean isSafeTextPath(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".json")
            || name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".csv")
            || name.endsWith(".log") || name.endsWith(".xml") || name.endsWith(".html")
            || name.endsWith(".css") || name.endsWith(".js") || !name.contains(".");
    }

    private void requirePlainName(String value) {
        if (!StringUtils.hasText(value) || value.contains("/") || value.contains("\\") || value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("newName must be a plain path segment");
        }
    }

    private String join(String base, String relative) {
        if (!StringUtils.hasText(relative) || ".".equals(relative.trim())) {
            return base;
        }
        return base + "/" + relative.trim().replace('\\', '/');
    }

    public record DirectoryListing(WorkArea workArea, String path, List<Entry> entries) {
    }

    public record Entry(String name, String path, boolean directory, boolean regularFile, long size, Instant modifiedAt) {
    }

    public record FilePreview(String path, long size, boolean text, String content) {
    }

    public record DeleteResult(String path, int deletedCount) {
    }
}
