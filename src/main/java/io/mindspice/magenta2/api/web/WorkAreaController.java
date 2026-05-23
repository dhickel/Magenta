package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.mindspice.magenta2.ai.orchestration.workspaces.WorkArea;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/work-areas")
public class WorkAreaController {
    private static final long MAX_DOWNLOAD_BYTES = 10 * 1024 * 1024;

    private final WorkAreaService workAreaService;
    private final WorkAreaExplorerService explorerService;

    public WorkAreaController(WorkAreaService workAreaService, WorkAreaExplorerService explorerService) {
        this.workAreaService = workAreaService;
        this.explorerService = explorerService;
    }

    @GetMapping
    public List<WorkArea> list(
        @RequestParam String ownerType,
        @RequestParam String ownerId,
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return workAreaService.list(parseOwnerType(ownerType), ownerId, includeInactive);
    }

    @PostMapping("/home")
    public WorkArea ensureHome(@RequestBody WorkAreaOwnerRequest request) {
        return workAreaService.ensureHome(parseOwnerType(request.ownerType()), request.ownerId(), request.displayName());
    }

    @PostMapping
    public WorkArea mark(@RequestBody MarkWorkAreaRequest request) {
        try {
            return workAreaService.markDirectory(
                parseOwnerType(request.ownerType()),
                request.ownerId(),
                request.path(),
                request.displayName()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @DeleteMapping("/{workAreaId}")
    public WorkArea unmark(@PathVariable String workAreaId) {
        try {
            return workAreaService.unmark(workAreaId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/{workAreaId}/files")
    public WorkAreaExplorerService.DirectoryListing files(
        @PathVariable String workAreaId,
        @RequestParam(defaultValue = ".") String path
    ) {
        return explorerService.list(workAreaId, path);
    }

    @GetMapping("/{workAreaId}/files/preview")
    public WorkAreaExplorerService.FilePreview preview(
        @PathVariable String workAreaId,
        @RequestParam String path
    ) {
        return explorerService.preview(workAreaId, path);
    }

    @GetMapping("/{workAreaId}/files/download")
    public ResponseEntity<?> download(
        @PathVariable String workAreaId,
        @RequestParam String path
    ) {
        try {
            Path file = explorerService.download(workAreaId, path);
            long size = Files.size(file);
            if (size > MAX_DOWNLOAD_BYTES) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "file too large: " + size + " bytes"));
            }
            String fileName = file.getFileName().toString();
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(size)
                .body(new InputStreamResource(Files.newInputStream(file)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", exception.getMessage()));
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
        }
    }

    @PutMapping("/{workAreaId}/files/text")
    public WorkAreaExplorerService.FilePreview saveText(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestBody TextSaveRequest request
    ) {
        return explorerService.saveText(workAreaId, path, request.content());
    }

    @PostMapping("/{workAreaId}/directories")
    public WorkAreaExplorerService.Entry createDirectory(
        @PathVariable String workAreaId,
        @RequestBody PathRequest request
    ) {
        return explorerService.createDirectory(workAreaId, request.path());
    }

    @PostMapping("/{workAreaId}/files/rename")
    public WorkAreaExplorerService.Entry rename(
        @PathVariable String workAreaId,
        @RequestBody RenameRequest request
    ) {
        return explorerService.rename(workAreaId, request.path(), request.newName());
    }

    @DeleteMapping("/{workAreaId}/files")
    public WorkAreaExplorerService.DeleteResult delete(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String confirm
    ) {
        return explorerService.deleteRecursive(workAreaId, path, confirm);
    }

    @PostMapping("/{workAreaId}/files/mark-work-area")
    public WorkArea markNested(
        @PathVariable String workAreaId,
        @RequestBody MarkNestedWorkAreaRequest request
    ) {
        return explorerService.mark(workAreaId, request.path(), request.displayName());
    }

    private WorkspaceOwnerType parseOwnerType(String ownerType) {
        try {
            return WorkspaceOwnerType.valueOf(ownerType.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ownerType: " + ownerType);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }

    public record WorkAreaOwnerRequest(String ownerType, String ownerId, String displayName) {
    }

    public record MarkWorkAreaRequest(String ownerType, String ownerId, String path, String displayName) {
    }

    public record MarkNestedWorkAreaRequest(String path, String displayName) {
    }

    public record TextSaveRequest(String content) {
    }

    public record PathRequest(String path) {
    }

    public record RenameRequest(String path, String newName) {
    }
}
