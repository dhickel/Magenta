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
                        toGeometry(state.geometry),
                        state.normalGeometry == null ? toGeometry(state.geometry) : toGeometry(state.normalGeometry)
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
                state.geometry = fromGeometry(geometry);
            }
            WorkspaceDefinition.Geometry normalGeometry = entry.getValue().normalGeometry();
            if (normalGeometry != null) {
                state.normalGeometry = fromGeometry(normalGeometry);
            }
            return state;
        }).toList();
        return document;
    }

    private WorkspaceDefinition.Geometry toGeometry(GeometryDocument geometry) {
        if (geometry == null) {
            return null;
        }
        return new WorkspaceDefinition.Geometry(
                geometry.x == null ? 0 : geometry.x,
                geometry.y == null ? 0 : geometry.y,
                geometry.width == null ? 0 : geometry.width,
                geometry.height == null ? 0 : geometry.height
        );
    }

    private GeometryDocument fromGeometry(WorkspaceDefinition.Geometry geometry) {
        GeometryDocument geometryDocument = new GeometryDocument();
        geometryDocument.x = geometry.x();
        geometryDocument.y = geometry.y();
        geometryDocument.width = geometry.width();
        geometryDocument.height = geometry.height();
        return geometryDocument;
    }

    public record Overlay(String activeWindowId, Map<String, OverlayWindowState> windows) {
        public Overlay {
            windows = windows == null ? Map.of() : Map.copyOf(windows);
        }
    }

    public record OverlayWindowState(
            Boolean visible,
            Boolean maximized,
            WorkspaceDefinition.Geometry geometry,
            WorkspaceDefinition.Geometry normalGeometry
    ) {
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
        @JsonProperty("normalGeometry")
        private GeometryDocument normalGeometry;
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
