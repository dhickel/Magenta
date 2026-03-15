package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteToolsFunctionalTest {

    @TempDir
    Path tempDir;

    @Test
    void sqliteQueryRejectsMultipleStatements() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_query",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"SELECT 1; SELECT 2;\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
    }

    @Test
    void sqliteExecRejectsReadOnlyStatement() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_exec",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"SELECT 1\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("invalid_sql_kind");
    }

    @Test
    void sqliteQuerySupportsCteReads() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_query",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"WITH t AS (SELECT 1 AS n) SELECT n FROM t\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("result").path("rows").get(0).path("n").asInt()).isEqualTo(1);
    }

    @Test
    void sqliteQueryTruncatesRowsAtConfiguredMax() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir, 32_768, 200, 2));
        manager.execute(ToolTestSupport.request("sqlite_exec",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"CREATE TABLE t(id INTEGER); INSERT INTO t(id) VALUES (1); INSERT INTO t(id) VALUES (2); INSERT INTO t(id) VALUES (3);\"}"
        ));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_query",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"SELECT id FROM t ORDER BY id\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("result").path("truncated").asBoolean()).isTrue();
        assertThat(payload.path("data").path("result").path("rowCount").asInt()).isEqualTo(2);
    }

    @Test
    void sqliteExecTransactionalRollbackOnFailure() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        manager.execute(ToolTestSupport.request("sqlite_exec",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"CREATE TABLE t(id INTEGER PRIMARY KEY, name TEXT); INSERT INTO t(name) VALUES ('seed');\",\"transactional\":true}"
        ));

        JsonNode failedExec = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_exec",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"INSERT INTO t(name) VALUES ('rolled_back'); INSERT INTO t(nope) VALUES ('bad');\",\"transactional\":true}"
        )));
        assertThat(failedExec.path("status").asText()).isEqualTo("failed");
        assertThat(failedExec.path("code").asText()).isEqualTo("db_error");

        JsonNode query = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_query",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"SELECT COUNT(*) AS c FROM t\"}"
        )));
        assertThat(query.path("status").asText()).isEqualTo("ok");
        assertThat(query.path("data").path("result").path("rows").get(0).path("c").asInt()).isEqualTo(1);
    }

    @Test
    void sqliteQueryRejectsPragmaStatements() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_query",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"PRAGMA foreign_keys = ON\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("invalid_sql_kind");
    }

    @Test
    void sqliteExecRejectsAttachDetachStatements() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_exec",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"ATTACH DATABASE 'other.sqlite' AS other\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("invalid_sql_kind");
    }

    @Test
    void sqliteExecRejectsDetachStatements() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_exec",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"DETACH DATABASE other\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("invalid_sql_kind");
    }

    @Test
    void sqliteExecRejectsUnknownUnsupportedStatements() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_exec",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"VACUUM\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("invalid_sql_kind");
    }

    @Test
    void sqliteQueryFailsClosedOnParseErrors() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_query",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"SELECT FROM\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("invalid_sql_kind");
    }

    @Test
    void sqliteExecFailsClosedOnParseErrors() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "sqlite_exec",
                "{\"dbPath\":\"db.sqlite\",\"sql\":\"INSERT INTO\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("invalid_sql_kind");
    }
}
