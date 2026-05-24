package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkAreaExplorerService {
    private static final long NORMAL_TEXT_BYTES = 10L * 1024 * 1024;
    private static final long NORMAL_MARKDOWN_BYTES = 5L * 1024 * 1024;
    private static final long HARD_EDIT_BYTES = 25L * 1024 * 1024;
    private static final long MAX_PREVIEW_BYTES = 256 * 1024;

    private final WorkAreaService workAreaService;
    private final WorkspaceFileMetadataService metadataService;
    private final WorkspaceFileActionLogRepository actionLogRepository;

    @Autowired
    public WorkAreaExplorerService(
        WorkAreaService workAreaService,
        WorkspaceFileMetadataService metadataService,
        WorkspaceFileActionLogRepository actionLogRepository
    ) {
        this.workAreaService = workAreaService;
        this.metadataService = metadataService;
        this.actionLogRepository = actionLogRepository;
    }

    // Compatibility constructor for narrow unit tests that do not need DB metadata/logging.
    public WorkAreaExplorerService(WorkAreaService workAreaService) {
        this(workAreaService, null, null);
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
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (isImagePath(file)) {
                return new FilePreview(relative, size, false, null, false, "image");
            }
            if (!isSafeTextPath(file)) {
                return new FilePreview(relative, size, false, null, false, "unsupported");
            }
            if (size > HARD_EDIT_BYTES) {
                return new FilePreview(relative, size, false, null, false, "too_large");
            }
            long normalLimit = isMarkdownPath(file) ? NORMAL_MARKDOWN_BYTES : NORMAL_TEXT_BYTES;
            boolean requiresWarning = size > normalLimit;
            if (size > MAX_PREVIEW_BYTES) {
                return new FilePreview(relative, size, true, null, requiresWarning, "text");
            }
            String text = readUtf8(file);
            if (text == null) {
                return new FilePreview(relative, size, false, null, false, "invalid_utf8");
            }
            return new FilePreview(
                relative,
                size,
                true,
                stripBom(text),
                requiresWarning,
                isMarkdownPath(file) ? "markdown" : "text"
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

    @Transactional
    public FilePreview saveText(String workAreaId, String relativePath, String content) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path file = resolveForWrite(root, relativePath);
        if (!isSafeTextPath(file)) {
            throw new IllegalArgumentException("file type is not safe for text editing");
        }
        String normalizedContent = stripBom(content == null ? "" : content);
        byte[] incomingBytes = normalizedContent.getBytes(StandardCharsets.UTF_8);
        if (incomingBytes.length > HARD_EDIT_BYTES) {
            throw new IllegalArgumentException("text file is too large");
        }
        try {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("path is not a regular file");
            }
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && readUtf8(file) == null) {
                throw new IllegalArgumentException("file is not valid UTF-8");
            }
            String lineEnding = Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? dominantLineEnding(file) : "\n";
            Files.createDirectories(file.getParent());
            Files.write(file, applyLineEnding(normalizedContent, lineEnding).getBytes(StandardCharsets.UTF_8));
            log(area, isMarkdownPath(file) ? WorkspaceFileActionType.SAVE_MARKDOWN : WorkspaceFileActionType.SAVE_TEXT,
                rootRelative(area, root, file), null, "SUCCEEDED", "{\"bytes\":" + Files.size(file) + "}");
            return preview(workAreaId, relativePath);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to save text file", exception);
        }
    }

    @Transactional
    public Entry createDirectory(String workAreaId, String relativePath) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path dir = resolveForWrite(root, relativePath);
        try {
            if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("target already exists");
            }
            Path created = Files.createDirectory(dir).toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!created.startsWith(root)) {
                throw new IllegalArgumentException("directory escapes Work Area");
            }
            log(area, WorkspaceFileActionType.CREATE_FOLDER, rootRelative(area, root, created), null, "SUCCEEDED", "{}");
            return entry(root, created);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to create directory", exception);
        }
    }

    @Transactional
    public Entry createTextFile(String workAreaId, String parentRelativePath, String fileName) {
        requirePlainName(fileName);
        String name = fileName.trim();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new IllegalArgumentException("text file name must end with .txt");
        }
        return createEmptyFile(workAreaId, parentRelativePath, name, WorkspaceFileActionType.CREATE_TEXT_FILE);
    }

    @Transactional
    public Entry createMarkdownFile(String workAreaId, String parentRelativePath, String fileName) {
        requirePlainName(fileName);
        String name = fileName.trim();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new IllegalArgumentException("markdown file name must end with .md");
        }
        return createEmptyFile(workAreaId, parentRelativePath, name, WorkspaceFileActionType.CREATE_MARKDOWN_FILE);
    }

    @Transactional
    public Entry rename(String workAreaId, String relativePath, String newName) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path source = resolveExisting(root, relativePath);
        requirePlainName(newName);
        Path target = source.getParent().resolve(newName.trim()).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("rename target escapes Work Area");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("target already exists");
        }
        ensureNotProtected(area, root, source);
        try {
            String sourceRootRelative = rootRelative(area, root, source);
            String targetRootRelative = rootRelative(area, root, target);
            movePath(source, target);
            metadataMove(area, sourceRootRelative, targetRootRelative);
            log(area, WorkspaceFileActionType.RENAME, sourceRootRelative, targetRootRelative, "SUCCEEDED", "{}");
            return entry(root, target.toRealPath());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to rename path", exception);
        }
    }

    @Transactional
    public Entry move(String workAreaId, String sourceRelativePath, String destinationDirectoryRelativePath, String newName) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path source = resolveExisting(root, sourceRelativePath);
        Path destinationDirectory = resolveExisting(root, destinationDirectoryRelativePath);
        if (!Files.isDirectory(destinationDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("destination is not a directory");
        }
        String targetName = StringUtils.hasText(newName) ? newName.trim() : source.getFileName().toString();
        requirePlainName(targetName);
        Path target = destinationDirectory.resolve(targetName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("move target escapes Work Area");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("target already exists");
        }
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) && target.startsWith(source)) {
            throw new IllegalArgumentException("directory cannot be moved into itself or a descendant");
        }
        ensureNotProtected(area, root, source);
        try {
            String sourceRootRelative = rootRelative(area, root, source);
            String targetRootRelative = rootRelative(area, root, target);
            movePath(source, target);
            metadataMove(area, sourceRootRelative, targetRootRelative);
            log(area, WorkspaceFileActionType.MOVE, sourceRootRelative, targetRootRelative, "SUCCEEDED", "{}");
            return entry(root, target.toRealPath(LinkOption.NOFOLLOW_LINKS));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to move path", exception);
        }
    }

    @Transactional
    public Entry copy(String workAreaId, String sourceRelativePath, String destinationDirectoryRelativePath, String newName) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path source = resolveExisting(root, sourceRelativePath);
        Path destinationDirectory = resolveExisting(root, destinationDirectoryRelativePath);
        if (!Files.isDirectory(destinationDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("destination is not a directory");
        }
        String targetName = StringUtils.hasText(newName) ? newName.trim() : source.getFileName().toString();
        requirePlainName(targetName);
        Path target = destinationDirectory.resolve(targetName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("copy target escapes Work Area");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("target already exists");
        }
        try {
            rejectSymbolicTree(root, source);
            copyPath(source, target);
            String sourceRootRelative = rootRelative(area, root, source);
            String targetRootRelative = rootRelative(area, root, target);
            metadataCopy(area, sourceRootRelative, targetRootRelative);
            log(area, WorkspaceFileActionType.COPY, sourceRootRelative, targetRootRelative, "SUCCEEDED", "{}");
            return entry(root, target.toRealPath(LinkOption.NOFOLLOW_LINKS));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to copy path", exception);
        }
    }

    public DeletePreflight deletePreflight(String workAreaId, String relativePath, DeleteStep step) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path target = resolveExisting(root, relativePath);
        ensureNotProtected(area, root, target);
        try {
            List<Path> paths = validatedDeletePaths(root, target);
            boolean directory = Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS);
            DeleteStep requiredStep = directory ? DeleteStep.DIRECTORY_RECURSIVE_CONFIRM : DeleteStep.FILE_CONFIRM;
            if (step == null || step == DeleteStep.INTENT) {
                return new DeletePreflight(
                    root.relativize(target).toString().replace('\\', '/'),
                    directory,
                    paths.size(),
                    directory ? DeleteStep.DIRECTORY_RECURSIVE_CONFIRM : DeleteStep.FILE_CONFIRM,
                    false
                );
            }
            boolean executable = step == requiredStep;
            return new DeletePreflight(
                root.relativize(target).toString().replace('\\', '/'),
                directory,
                paths.size(),
                requiredStep,
                executable
            );
        } catch (IOException exception) {
            log(area, Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                ? WorkspaceFileActionType.DELETE_DIRECTORY : WorkspaceFileActionType.DELETE_FILE,
                rootRelative(area, root, target), null, "FAILED", "{\"error\":\"preflight\"}");
            throw new IllegalStateException("failed to preflight delete", exception);
        }
    }

    @Transactional
    public DeleteResult delete(String workAreaId, String relativePath, DeleteStep step) {
        DeletePreflight preflight = deletePreflight(workAreaId, relativePath, step);
        if (!preflight.executable()) {
            throw new IllegalArgumentException("delete confirmation step is required: " + preflight.requiredStep());
        }
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path target = resolveExisting(root, relativePath);
        ensureNotProtected(area, root, target);
        try {
            List<Path> paths = validatedDeletePaths(root, target);
            for (Path path : paths) {
                Files.delete(path);
            }
            String sourceRootRelative = rootRelative(area, root, target);
            metadataDelete(area, sourceRootRelative);
            log(area, preflight.directory() ? WorkspaceFileActionType.DELETE_DIRECTORY : WorkspaceFileActionType.DELETE_FILE,
                sourceRootRelative, null, "SUCCEEDED", "{\"deletedCount\":" + paths.size() + "}");
            return new DeleteResult(root.relativize(target).toString().replace('\\', '/'), paths.size());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to delete path", exception);
        }
    }

    @Transactional
    public DeleteResult deleteRecursive(String workAreaId, String relativePath, String confirmation) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path target = resolveExisting(root, relativePath);
        String expected = target.getFileName() == null ? "" : target.getFileName().toString();
        if (!expected.equals(confirmation)) {
            throw new IllegalArgumentException("typed confirmation must match path name");
        }
        DeleteStep step = Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
            ? DeleteStep.DIRECTORY_RECURSIVE_CONFIRM
            : DeleteStep.FILE_CONFIRM;
        return delete(workAreaId, relativePath, step);
    }

    public WorkArea mark(String workAreaId, String relativePath, String displayName) {
        WorkArea area = workAreaService.get(workAreaId);
        String areaPath = join(area.areaRelativePath(), relativePath);
        return workAreaService.markDirectory(area.ownerType(), area.ownerId(), areaPath, displayName);
    }

    public WorkspaceFileLabelAssignment addLabel(String workAreaId, String relativePath, String labelSlug) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path target = resolveExisting(root, relativePath);
        if (metadataService == null) {
            throw new IllegalStateException("workspace file metadata service is not available");
        }
        return metadataService.addLabel(area, rootRelative(area, root, target), labelSlug);
    }

    public int removeLabel(String workAreaId, String relativePath, String labelSlug) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path target = resolveExisting(root, relativePath);
        if (metadataService == null) {
            throw new IllegalStateException("workspace file metadata service is not available");
        }
        return metadataService.removeLabel(area, rootRelative(area, root, target), labelSlug);
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
            if (parent != null && !Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(parent);
            }
            Path realParent = (parent == null ? root : parent.toRealPath(LinkOption.NOFOLLOW_LINKS));
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
        if (text.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException("absolute paths are not allowed");
        }
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

    private boolean isMarkdownPath(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".markdown");
    }

    private boolean isImagePath(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
            || name.endsWith(".gif") || name.endsWith(".webp");
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

    private Entry createEmptyFile(
        String workAreaId,
        String parentRelativePath,
        String fileName,
        WorkspaceFileActionType actionType
    ) {
        WorkArea area = workAreaService.get(workAreaId);
        Path root = workAreaService.resolve(area);
        Path parent = resolveExisting(root, parentRelativePath);
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("parent path is not a directory");
        }
        Path file = parent.resolve(fileName).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("file target escapes Work Area");
        }
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("target already exists");
        }
        try {
            Files.writeString(file, "", StandardCharsets.UTF_8);
            log(area, actionType, rootRelative(area, root, file), null, "SUCCEEDED", "{}");
            return entry(root, file.toRealPath(LinkOption.NOFOLLOW_LINKS));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to create file", exception);
        }
    }

    private void movePath(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(source, target);
        }
    }

    private void copyPath(Path source, Path target) throws IOException {
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            try (var walk = Files.walk(source)) {
                for (Path path : walk.toList()) {
                    Path relative = source.relativize(path);
                    Path copyTarget = target.resolve(relative).normalize();
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        Files.createDirectory(copyTarget);
                    } else {
                        Files.copy(path, copyTarget, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
            return;
        }
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private List<Path> validatedDeletePaths(Path root, Path target) throws IOException {
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
            return paths;
        }
    }

    private void rejectSymbolicTree(Path root, Path source) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path real = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
                if (!real.startsWith(root)) {
                    throw new IllegalArgumentException("copy path escapes Work Area");
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("symbolic links are not copied through Work Area explorer");
                }
            }
        }
    }

    private String rootRelative(WorkArea area, Path root, Path path) {
        String withinArea = root.relativize(path.normalize()).toString().replace('\\', '/');
        if (!StringUtils.hasText(withinArea)) {
            return area.areaRelativePath();
        }
        return join(area.areaRelativePath(), withinArea);
    }

    private void metadataMove(WorkArea area, String sourceRootRelative, String targetRootRelative) {
        if (metadataService != null) {
            metadataService.onMove(area, sourceRootRelative, targetRootRelative);
        }
    }

    private void metadataCopy(WorkArea area, String sourceRootRelative, String targetRootRelative) {
        if (metadataService != null) {
            metadataService.onCopy(area, sourceRootRelative, targetRootRelative);
        }
    }

    private void metadataDelete(WorkArea area, String sourceRootRelative) {
        if (metadataService != null) {
            metadataService.onDelete(area, sourceRootRelative);
        }
    }

    private void log(
        WorkArea area,
        WorkspaceFileActionType actionType,
        String sourceRootRelative,
        String targetRootRelative,
        String result,
        String payloadJson
    ) {
        if (actionLogRepository != null) {
            actionLogRepository.record(area, actionType, sourceRootRelative, targetRootRelative, result, payloadJson);
        }
    }

    private String readUtf8(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            return null;
        }
    }

    private String stripBom(String content) {
        if (content != null && !content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }

    private String dominantLineEnding(Path file) throws IOException {
        String text = readUtf8(file);
        if (text == null) {
            throw new IllegalArgumentException("file is not valid UTF-8");
        }
        int crlf = text.split("\\r\\n", -1).length - 1;
        int lf = text.split("(?<!\\r)\\n", -1).length - 1;
        return crlf > lf ? "\r\n" : "\n";
    }

    private String applyLineEnding(String content, String lineEnding) {
        if ("\n".equals(lineEnding)) {
            return content;
        }
        return content.replace("\r\n", "\n").replace("\r", "\n").replace("\n", lineEnding);
    }

    public record DirectoryListing(WorkArea workArea, String path, List<Entry> entries) {
    }

    public record Entry(String name, String path, boolean directory, boolean regularFile, long size, Instant modifiedAt) {
    }

    public record FilePreview(String path, long size, boolean text, String content, boolean requiresWarning, String kind) {
        public FilePreview(String path, long size, boolean text, String content) {
            this(path, size, text, content, false, text ? "text" : "unsupported");
        }
    }

    public record DeleteResult(String path, int deletedCount) {
    }

    public record DeletePreflight(
        String path,
        boolean directory,
        int candidateCount,
        DeleteStep requiredStep,
        boolean executable
    ) {
    }

    public enum DeleteStep {
        INTENT,
        FILE_CONFIRM,
        DIRECTORY_RECURSIVE_CONFIRM
    }
}
