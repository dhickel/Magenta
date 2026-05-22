package io.mindspice.magenta2.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import io.mindspice.magenta2.Magenta2Application;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.core.config.MagentaRootProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class AvatarDataConfigurationTest {

    @Test
    void avatarDataSourceCreatesAvatarSqliteUnderMagentaRoot(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("magenta-root");
        DataSource dataSource = new AvatarDataConfiguration().avatarDataSource(new MagentaRootProperties(root));

        try (var ignored = dataSource.getConnection()) {
            assertThat(root.resolve("avatar.sqlite")).exists();
        }
    }

    @Test
    void applicationContextKeepsPrimaryAndAvatarSchemasSeparate(@TempDir Path tempDir) throws Exception {
        Path magentaRoot = tempDir.resolve("fresh-root");
        Path aiConfig = writeAiConfig(tempDir.resolve("config"));

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Magenta2Application.class)
            .web(WebApplicationType.NONE)
            .run(
                "--server.port=0",
                "--magenta.root.path=" + magentaRoot,
                "--app.ai.config-path=" + aiConfig,
                "--spring.datasource.url=jdbc:sqlite:" + magentaRoot.resolve("magenta.sqlite") + "?foreign_keys=true",
                "--spring.datasource.driver-class-name=org.sqlite.JDBC",
                "--magenta.features.schedules-enabled=false",
                "--magenta.features.reactions-enabled=false",
                "--magenta.plan.execution-stream-timeout-seconds=0"
            )) {
            JdbcTemplate primary = context.getBean(JdbcTemplate.class);
            JdbcTemplate avatar = context.getBean("avatarJdbcTemplate", JdbcTemplate.class);

            assertThat(magentaRoot.resolve("magenta.sqlite")).exists();
            assertThat(magentaRoot.resolve("avatar.sqlite")).exists();
            assertThat(tableExists(primary, "ai_chat_memory")).isTrue();
            assertThat(tableExists(primary, "avatar_profile")).isFalse();
            assertThat(tableExists(avatar, "avatar_profile")).isTrue();
            assertThat(tableExists(avatar, "ai_chat_memory")).isFalse();
            assertThat(context.getBean(AiConfig.class).dataRoot()).isEqualTo(magentaRoot.resolve("root").normalize());
        }
    }

    private static boolean tableExists(JdbcTemplate jdbcTemplate, String table) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from sqlite_master where type = 'table' and name = ?",
            Integer.class,
            table
        );
        return count != null && count == 1;
    }

    private static Path writeAiConfig(Path configDir) throws IOException {
        Files.createDirectories(configDir.resolve("prompts"));
        Files.writeString(configDir.resolve("prompts/system.md"), "Fresh install test agent.");
        Path config = configDir.resolve("ai-config.json");
        Files.writeString(config, """
            {
              "defaultAgent": "magenta",
              "defaultModel": "local-qwen",
              "summaryModel": "local-qwen",
              "planningModel": "local-qwen",
              "compactionModel": "local-qwen",
              "contextBufferPercent": 33,
              "unsafeAllowWildcardShellCommands": false,
              "models": {
                "local-qwen": {
                  "remoteModelName": "qwen3",
                  "remoteEndpoint": "http://localhost:11434",
                  "endpointType": "OLLAMA",
                  "contextLength": 8192
                }
              },
              "agents": {
                "magenta": {
                  "model": "local-qwen",
                  "systemPrompt": "prompts/system.md",
                  "approvedTools": [],
                  "allowedShellCommands": []
                }
              }
            }
            """);
        return config;
    }
}
