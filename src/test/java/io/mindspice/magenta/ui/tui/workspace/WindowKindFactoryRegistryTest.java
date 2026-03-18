package io.mindspice.magenta.ui.tui.workspace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowKindFactoryRegistryTest {

    @Test
    void resolvesRegisteredKindsCaseInsensitively() {
        WindowKindFactoryRegistry registry = WindowKindFactoryRegistry.fromFactories(java.util.List.of(factory("chat")));

        WindowKindFactory factory = registry.require("ChAt");

        assertThat(factory.kind()).isEqualTo("chat");
        assertThat(registry.kinds()).containsExactly("chat");
    }

    @Test
    void rejectsUnknownKind() {
        WindowKindFactoryRegistry registry = WindowKindFactoryRegistry.fromFactories(java.util.List.of(factory("chat")));

        assertThatThrownBy(() -> registry.require("metrics_panel"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No window factory registered for kind: metrics_panel");
    }

    @Test
    void rejectsDuplicateKindRegistration() {
        WindowKindFactoryRegistry registry = new WindowKindFactoryRegistry();
        registry.register(factory("chat"));

        assertThatThrownBy(() -> registry.register(factory("chat")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate window factory kind: chat");
    }

    private WindowKindFactory factory(String kind) {
        return new WindowKindFactory() {
            @Override
            public String kind() {
                return kind;
            }

            @Override
            public casciian.TWindow create(WorkspaceDefinition.WindowDescriptor descriptor, io.mindspice.magenta.ui.tui.TuiApplication app) {
                throw new UnsupportedOperationException("not needed");
            }
        };
    }
}
