package io.mindspice.magenta.ui.tui.workspace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class WorkspaceConfigLoader {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private final WindowKindFactoryRegistry windowKindRegistry;

    public WorkspaceConfigLoader(WindowKindFactoryRegistry windowKindRegistry) {
        this.windowKindRegistry = Objects.requireNonNull(windowKindRegistry, "windowKindRegistry");
    }

    public Map<String, WorkspaceDefinition> load(Path configRoot) {
        Objects.requireNonNull(configRoot, "configRoot");
        Path workspacesRoot = configRoot.resolve("workspaces");
        if (!Files.isDirectory(workspacesRoot)) {
            return defaultWorkspaceMap();
        }

        List<Path> files = listWorkspaceFiles(workspacesRoot);
        if (files.isEmpty()) {
            return defaultWorkspaceMap();
        }

        Map<String, WorkspaceDefinition> byId = new LinkedHashMap<>();
        for (Path file : files) {
            WorkspaceDocument document = readDocument(file);
            String workspaceId = deriveWorkspaceId(file);
            if (byId.containsKey(workspaceId)) {
                throw new WorkspaceValidationException(
                        "workspace_validation_error",
                        workspaceId,
                        "id",
                        "Duplicate workspace id from filename: " + workspaceId,
                        null
                );
            }
            byId.put(workspaceId, toWorkspaceDefinition(workspaceId, document));
        }
        return Map.copyOf(byId);
    }

    private WorkspaceDefinition toWorkspaceDefinition(String workspaceId, WorkspaceDocument document) {
        int schemaVersion = normalizeSchemaVersion(workspaceId, document.schemaVersion);
        if (document.windows == null || document.windows.isEmpty()) {
            throw new WorkspaceValidationException(
                    "workspace_validation_error",
                    workspaceId,
                    "windows",
                    "workspace '" + workspaceId + "' must declare windows",
                    null
            );
        }

        List<WorkspaceDefinition.WindowDescriptor> windows = new ArrayList<>();
        Map<String, Boolean> seenWindowIds = new LinkedHashMap<>();
        for (WindowDocument window : document.windows) {
            WorkspaceDefinition.WindowDescriptor descriptor;
            try {
                descriptor = new WorkspaceDefinition.WindowDescriptor(
                        window.id,
                        window.kind,
                        window.title,
                        window.visible == null || window.visible,
                        window.geometry == null
                                ? null
                                : new WorkspaceDefinition.Geometry(
                                window.geometry.x == null ? 0 : window.geometry.x,
                                window.geometry.y == null ? 0 : window.geometry.y,
                                window.geometry.width == null ? 0 : window.geometry.width,
                                window.geometry.height == null ? 0 : window.geometry.height
                        ),
                        window.bindings
                );
            } catch (IllegalStateException e) {
                throw new WorkspaceValidationException(
                        "workspace_validation_error",
                        workspaceId,
                        "windows",
                        e.getMessage(),
                        e
                );
            }

            if (!windowKindRegistry.contains(descriptor.kind())) {
                throw new WorkspaceValidationException(
                        "workspace_validation_error",
                        workspaceId,
                        "windows[" + windows.size() + "].kind",
                        "Unknown window kind: " + descriptor.kind(),
                        null
                );
            }
            if (seenWindowIds.putIfAbsent(descriptor.id(), Boolean.TRUE) != null) {
                throw new WorkspaceValidationException(
                        "workspace_validation_error",
                        workspaceId,
                        "windows[" + windows.size() + "].id",
                        "workspace '" + workspaceId + "' contains duplicate window id: " + descriptor.id(),
                        null
                );
            }
            windows.add(descriptor);
        }

        return new WorkspaceDefinition(
                schemaVersion,
                workspaceId,
                document.name,
                parseLayoutMode(document.layoutMode),
                windows
        );
    }

    private WorkspaceDefinition.LayoutMode parseLayoutMode(String layoutMode) {
        if (layoutMode == null || layoutMode.isBlank()) {
            return WorkspaceDefinition.LayoutMode.TILED;
        }
        return switch (layoutMode.trim().toLowerCase(Locale.ROOT)) {
            case "tiled" -> WorkspaceDefinition.LayoutMode.TILED;
            case "cascade", "cascaded" -> WorkspaceDefinition.LayoutMode.CASCADE;
            default -> throw new IllegalStateException("Unsupported workspace layoutMode: " + layoutMode);
        };
    }

    private WorkspaceDocument readDocument(Path file) {
        try {
            return MAPPER.readValue(file.toFile(), WorkspaceDocument.class);
        } catch (WorkspaceValidationException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse workspace config: " + file.toAbsolutePath(), e);
        }
    }

    private int normalizeSchemaVersion(String workspaceId, Integer schemaVersion) {
        if (schemaVersion == null) {
            throw new WorkspaceValidationException(
                    "workspace_validation_error",
                    workspaceId,
                    "schemaVersion",
                    "Missing required schemaVersion",
                    null
            );
        }
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new WorkspaceValidationException(
                    "workspace_validation_error",
                    workspaceId,
                    "schemaVersion",
                    "Unsupported workspace schemaVersion: " + schemaVersion
                            + " (supported: " + SUPPORTED_SCHEMA_VERSION + ")",
                    null
            );
        }
        return schemaVersion;
    }

    private List<Path> listWorkspaceFiles(Path workspacesRoot) {
        try (Stream<Path> stream = Files.walk(workspacesRoot)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && isYaml(path))
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list workspaces under: " + workspacesRoot.toAbsolutePath(), e);
        }
    }

    private String deriveWorkspaceId(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return name;
        }
        return name.substring(0, dot);
    }

    private boolean isYaml(Path path) {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".yaml") || lower.endsWith(".yml");
    }

    private Map<String, WorkspaceDefinition> defaultWorkspaceMap() {
        WorkspaceDefinition.WindowDescriptor chatWindow = new WorkspaceDefinition.WindowDescriptor(
                "chat-main",
                "chat",
                "Chat",
                true,
                null,
                Map.of("alias", "chat")
        );
        WorkspaceDefinition fallback = new WorkspaceDefinition(
                SUPPORTED_SCHEMA_VERSION,
                "default",
                "Default",
                WorkspaceDefinition.LayoutMode.TILED,
                List.of(chatWindow)
        );
        return Map.of(fallback.id(), fallback);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class WorkspaceDocument {
        @JsonProperty("schemaVersion")
        private Integer schemaVersion;
        @JsonProperty("name")
        private String name;
        @JsonProperty("layoutMode")
        private String layoutMode;
        @JsonProperty("windows")
        private List<WindowDocument> windows;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class WindowDocument {
        @JsonProperty("id")
        private String id;
        @JsonProperty("kind")
        private String kind;
        @JsonProperty("title")
        private String title;
        @JsonProperty("visible")
        private Boolean visible;
        @JsonProperty("geometry")
        private GeometryDocument geometry;
        @JsonProperty("bindings")
        private Map<String, String> bindings;
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
