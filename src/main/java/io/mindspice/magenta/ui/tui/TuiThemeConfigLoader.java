package io.mindspice.magenta.ui.tui;

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

public final class TuiThemeConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public Map<String, TuiThemeProfile> load(Path configRoot) {
        Objects.requireNonNull(configRoot, "configRoot");
        Path themesRoot = configRoot.resolve("themes");
        if (!Files.isDirectory(themesRoot)) {
            return Map.of();
        }

        List<Path> files = listThemeFiles(themesRoot);
        if (files.isEmpty()) {
            return Map.of();
        }

        Map<String, TuiThemeProfile> byId = new LinkedHashMap<>();
        for (Path file : files) {
            String id = deriveThemeId(file);
            ThemeDocument document = readDocument(file);
            TuiThemeProfile profile = new TuiThemeProfile(
                    id,
                    document.name == null || document.name.isBlank() ? id : document.name.trim(),
                    document.base,
                    document.colors
            );
            if (byId.putIfAbsent(profile.id(), profile) != null) {
                throw new IllegalStateException("Duplicate theme id from filename: " + profile.id());
            }
        }
        return Map.copyOf(byId);
    }

    private ThemeDocument readDocument(Path file) {
        try {
            return MAPPER.readValue(file.toFile(), ThemeDocument.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse theme config: " + file.toAbsolutePath(), e);
        }
    }

    private List<Path> listThemeFiles(Path themesRoot) {
        try (Stream<Path> stream = Files.walk(themesRoot)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && isYaml(path))
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list themes under: " + themesRoot.toAbsolutePath(), e);
        }
    }

    private String deriveThemeId(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private boolean isYaml(Path path) {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".yaml") || lower.endsWith(".yml");
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class ThemeDocument {
        @JsonProperty("name")
        private String name;
        @JsonProperty("base")
        private String base;
        @JsonProperty("colors")
        private Map<String, String> colors;
    }
}
