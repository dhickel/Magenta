package io.mindspice.magenta.ui.tui.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsFallbackWorkspaceWhenDirectoryIsMissing() {
        WorkspaceConfigLoader loader = new WorkspaceConfigLoader(registryWithKinds("chat"));

        Map<String, WorkspaceDefinition> loaded = loader.load(tempDir);

        assertThat(loaded).containsKey("default");
        WorkspaceDefinition workspace = loaded.get("default");
        assertThat(workspace.schemaVersion()).isEqualTo(WorkspaceConfigLoader.SUPPORTED_SCHEMA_VERSION);
        assertThat(workspace.windows()).hasSize(1);
        assertThat(workspace.windows().getFirst().kind()).isEqualTo("chat");
    }

    @Test
    void loadsWorkspaceWithFilenameDerivedId() throws Exception {
        Path workspacesDir = tempDir.resolve("workspaces");
        Files.createDirectories(workspacesDir);
        Files.writeString(workspacesDir.resolve("dashboard.yaml"), """
                schemaVersion: 1
                name: Dashboard
                layoutMode: tiled
                windows:
                  - id: chat-main
                    kind: chat
                    title: Chat
                    visible: true
                    geometry:
                      x: 1
                      y: 2
                      width: 80
                      height: 20
                """);

        WorkspaceConfigLoader loader = new WorkspaceConfigLoader(registryWithKinds("chat"));
        Map<String, WorkspaceDefinition> loaded = loader.load(tempDir);

        assertThat(loaded).containsKey("dashboard");
        WorkspaceDefinition workspace = loaded.get("dashboard");
        assertThat(workspace.id()).isEqualTo("dashboard");
        assertThat(workspace.name()).isEqualTo("Dashboard");
        assertThat(workspace.windows()).hasSize(1);
        assertThat(workspace.windows().getFirst().geometry()).isEqualTo(new WorkspaceDefinition.Geometry(1, 2, 80, 20));
    }

    @Test
    void rejectsUnknownYamlKeys() throws Exception {
        Path workspacesDir = tempDir.resolve("workspaces");
        Files.createDirectories(workspacesDir);
        Files.writeString(workspacesDir.resolve("invalid.yaml"), """
                schemaVersion: 1
                name: Invalid
                unknown: true
                windows:
                  - id: chat-main
                    kind: chat
                    title: Chat
                    visible: true
                """);

        WorkspaceConfigLoader loader = new WorkspaceConfigLoader(registryWithKinds("chat"));

        assertThatThrownBy(() -> loader.load(tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse workspace config");
    }

    @Test
    void rejectsDuplicateFilenameIds() throws Exception {
        Path workspacesDir = tempDir.resolve("workspaces");
        Files.createDirectories(workspacesDir.resolve("a"));
        Files.createDirectories(workspacesDir.resolve("b"));

        String content = """
                schemaVersion: 1
                name: Dup
                windows:
                  - id: chat-main
                    kind: chat
                    title: Chat
                    visible: true
                """;
        Files.writeString(workspacesDir.resolve("a/default.yaml"), content);
        Files.writeString(workspacesDir.resolve("b/default.yaml"), content);

        WorkspaceConfigLoader loader = new WorkspaceConfigLoader(registryWithKinds("chat"));

        assertThatThrownBy(() -> loader.load(tempDir))
                .isInstanceOf(WorkspaceValidationException.class)
                .hasMessageContaining("Duplicate workspace id");
    }

    @Test
    void rejectsMissingSchemaVersion() throws Exception {
        Path workspacesDir = tempDir.resolve("workspaces");
        Files.createDirectories(workspacesDir);
        Files.writeString(workspacesDir.resolve("invalid.yaml"), """
                name: Invalid
                windows:
                  - id: chat-main
                    kind: chat
                    title: Chat
                    visible: true
                """);

        WorkspaceConfigLoader loader = new WorkspaceConfigLoader(registryWithKinds("chat"));

        assertThatThrownBy(() -> loader.load(tempDir))
                .isInstanceOf(WorkspaceValidationException.class)
                .hasMessageContaining("schemaVersion")
                .hasMessageContaining("Missing required schemaVersion");
    }

    @Test
    void rejectsUnsupportedSchemaVersion() throws Exception {
        Path workspacesDir = tempDir.resolve("workspaces");
        Files.createDirectories(workspacesDir);
        Files.writeString(workspacesDir.resolve("invalid.yaml"), """
                schemaVersion: 2
                name: Invalid
                windows:
                  - id: chat-main
                    kind: chat
                    title: Chat
                    visible: true
                """);

        WorkspaceConfigLoader loader = new WorkspaceConfigLoader(registryWithKinds("chat"));

        assertThatThrownBy(() -> loader.load(tempDir))
                .isInstanceOf(WorkspaceValidationException.class)
                .hasMessageContaining("Unsupported workspace schemaVersion");
    }

    @Test
    void rejectsUnknownWindowKind() throws Exception {
        Path workspacesDir = tempDir.resolve("workspaces");
        Files.createDirectories(workspacesDir);
        Files.writeString(workspacesDir.resolve("invalid.yaml"), """
                schemaVersion: 1
                name: Invalid
                windows:
                  - id: unknown-main
                    kind: metrics_panel
                    title: Metrics
                    visible: true
                """);

        WorkspaceConfigLoader loader = new WorkspaceConfigLoader(registryWithKinds("chat"));

        assertThatThrownBy(() -> loader.load(tempDir))
                .isInstanceOf(WorkspaceValidationException.class)
                .hasMessageContaining("Unknown window kind: metrics_panel");
    }

    @Test
    void rejectsInvalidGeometryOrigin() throws Exception {
        Path workspacesDir = tempDir.resolve("workspaces");
        Files.createDirectories(workspacesDir);
        Files.writeString(workspacesDir.resolve("invalid.yaml"), """
                schemaVersion: 1
                name: Invalid
                windows:
                  - id: chat-main
                    kind: chat
                    title: Chat
                    visible: true
                    geometry:
                      x: -1
                      y: 0
                      width: 80
                      height: 20
                """);

        WorkspaceConfigLoader loader = new WorkspaceConfigLoader(registryWithKinds("chat"));

        assertThatThrownBy(() -> loader.load(tempDir))
                .isInstanceOf(WorkspaceValidationException.class)
                .hasMessageContaining("invalid geometry origin");
    }

    private WindowKindFactoryRegistry registryWithKinds(String... kinds) {
        List<WindowKindFactory> factories = new java.util.ArrayList<>();
        for (String kind : kinds) {
            factories.add(new WindowKindFactory() {
                @Override
                public String kind() {
                    return kind;
                }

                @Override
                public casciian.TWindow create(
                        WorkspaceDefinition.WindowDescriptor descriptor,
                        io.mindspice.magenta.ui.tui.TuiApplication app
                ) {
                    throw new UnsupportedOperationException("not needed");
                }
            });
        }
        return WindowKindFactoryRegistry.fromFactories(factories);
    }
}
