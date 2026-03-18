package io.mindspice.magenta.ui.tui.workspace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WorkspaceOverlayStore {
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private final Path overlayRoot;

    public WorkspaceOverlayStore(Path workspaceRoot) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        this.overlayRoot = workspaceRoot.resolve(".magenta").resolve("ui").resolve("workspaces");
    }

    public Overlay load(String workspaceId) {
        Path path = overlayPath(workspaceId);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            OverlayDocument document = MAPPER.readValue(path.toFile(), OverlayDocument.class);
            return toOverlay(document);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load workspace overlay: " + path.toAbsolutePath(), e);
        }
    }

    public void save(String workspaceId, Overlay overlay) {
        Objects.requireNonNull(overlay, "overlay");
        Path path = overlayPath(workspaceId);
        try {
            Files.createDirectories(overlayRoot);
            MAPPER.writeValue(path.toFile(), fromOverlay(overlay));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save workspace overlay: " + path.toAbsolutePath(), e);
        }
    }

    private Path overlayPath(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalStateException("workspaceId must not be blank");
        }
        return overlayRoot.resolve(workspaceId + ".yaml");
    }

    private Overlay toOverlay(OverlayDocument document) {
        if (document == null) {
            return new Overlay(null, Map.of());
        }
        Map<String, OverlayWindowState> windows = new LinkedHashMap<>();
        if (document.windows != null) {
            for (WindowStateDocument state : document.windows) {
                if (state == null || state.id == null || state.id.isBlank()) {
                    continue;
                }
                windows.put(state.id.trim(), new OverlayWindowState(
                        state.visible,
                        state.maximized,
                        state.geometry == null ? null : new WorkspaceDefinition.Geometry(
                                state.geometry.x == null ? 0 : state.geometry.x,
                                state.geometry.y == null ? 0 : state.geometry.y,
                                state.geometry.width == null ? 0 : state.geometry.width,
                                state.geometry.height == null ? 0 : state.geometry.height
                        )
                ));
            }
        }
        return new Overlay(document.activeWindowId, Map.copyOf(windows));
    }

    private OverlayDocument fromOverlay(Overlay overlay) {
        OverlayDocument document = new OverlayDocument();
        document.activeWindowId = overlay.activeWindowId;
        document.windows = overlay.windows.entrySet().stream().map(entry -> {
            WindowStateDocument state = new WindowStateDocument();
            state.id = entry.getKey();
            state.visible = entry.getValue().visible();
            state.maximized = entry.getValue().maximized();
            WorkspaceDefinition.Geometry geometry = entry.getValue().geometry();
            if (geometry != null) {
                GeometryDocument geometryDocument = new GeometryDocument();
                geometryDocument.x = geometry.x();
                geometryDocument.y = geometry.y();
                geometryDocument.width = geometry.width();
                geometryDocument.height = geometry.height();
                state.geometry = geometryDocument;
            }
            return state;
        }).toList();
        return document;
    }

    public record Overlay(String activeWindowId, Map<String, OverlayWindowState> windows) {
        public Overlay {
            windows = windows == null ? Map.of() : Map.copyOf(windows);
        }
    }

    public record OverlayWindowState(Boolean visible, Boolean maximized, WorkspaceDefinition.Geometry geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class OverlayDocument {
        @JsonProperty("activeWindowId")
        private String activeWindowId;
        @JsonProperty("windows")
        private java.util.List<WindowStateDocument> windows;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class WindowStateDocument {
        @JsonProperty("id")
        private String id;
        @JsonProperty("visible")
        private Boolean visible;
        @JsonProperty("maximized")
        private Boolean maximized;
        @JsonProperty("geometry")
        private GeometryDocument geometry;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class GeometryDocument {
        @JsonProperty("x")
        private Integer x;
        @JsonProperty("y")
        private Integer y;
        @JsonProperty("width")
        private Integer width;
        @JsonProperty("height")
        private Integer height;
    }
}
