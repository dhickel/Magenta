package io.mindspice.magenta.ui.tui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TuiThemeConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsThemeProfilesFromFilenameDerivedIds() throws Exception {
        Path themesDir = tempDir.resolve("themes");
        Files.createDirectories(themesDir);
        Files.writeString(themesDir.resolve("ocean.yaml"), """
                name: Ocean
                base: default
                colors:
                  tdesktop.background: "black on blue"
                  tmenu.highlighted: "black on cyan"
                """);

        Map<String, TuiThemeProfile> loaded = new TuiThemeConfigLoader().load(tempDir);

        assertThat(loaded).containsKey("ocean");
        assertThat(loaded.get("ocean").name()).isEqualTo("Ocean");
        assertThat(loaded.get("ocean").colors()).containsEntry("tdesktop.background", "black on blue");
    }

    @Test
    void rejectsUnknownThemeFields() throws Exception {
        Path themesDir = tempDir.resolve("themes");
        Files.createDirectories(themesDir);
        Files.writeString(themesDir.resolve("broken.yaml"), """
                name: Broken
                unknown: value
                """);

        assertThatThrownBy(() -> new TuiThemeConfigLoader().load(tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse theme config");
    }
}
