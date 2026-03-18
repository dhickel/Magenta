package io.mindspice.magenta.ui.tui.workspace;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record WorkspaceDefinition(
        int schemaVersion,
        String id,
        String name,
        LayoutMode layoutMode,
        List<WindowDescriptor> windows
) {
    public WorkspaceDefinition {
        if (schemaVersion <= 0) {
            throw new IllegalStateException("schemaVersion must be greater than zero");
        }
        id = normalizeRequired(id, "id");
        name = normalizeRequired(name, "name");
        layoutMode = layoutMode == null ? LayoutMode.TILED : layoutMode;
        windows = windows == null ? List.of() : List.copyOf(windows);
        if (windows.isEmpty()) {
            throw new IllegalStateException("workspace '" + id + "' must declare at least one window");
        }
    }

    public enum LayoutMode {
        TILED,
        CASCADE
    }

    public record WindowDescriptor(
            String id,
            String kind,
            String title,
            boolean visible,
            Geometry geometry,
            Map<String, String> bindings
    ) {
        public WindowDescriptor {
            id = normalizeRequired(id, "window id");
            kind = normalizeRequired(kind, "window kind");
            title = title == null || title.isBlank() ? id : title.trim();
            bindings = bindings == null ? Map.of() : Map.copyOf(bindings);
            if (geometry != null) {
                if (geometry.x() < 0 || geometry.y() < 0) {
                    throw new IllegalStateException("window '" + id + "' has invalid geometry origin");
                }
                if (geometry.width() <= 0 || geometry.height() <= 0) {
                    throw new IllegalStateException("window '" + id + "' has invalid geometry dimensions");
                }
            }
        }

        public String binding(String key) {
            Objects.requireNonNull(key, "key");
            return bindings.get(key);
        }
    }

    public record Geometry(int x, int y, int width, int height) {
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
