package io.mindspice.magenta.runtime.tools.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mindspice.magenta.runtime.tools.ToolExecutionSettings;
import io.mindspice.magenta.runtime.tools.ToolPathSupport;
import io.mindspice.magenta.runtime.tools.ToolPayloads;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;
import io.mindspice.sjbdc.SimplyJDBC;
import io.mindspice.sjbdc.SjColumn;
import io.mindspice.sjbdc.SjResult;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SqliteTools {

    private static final ObjectMapper MAPPER = ToolPayloads.mapper();
    private static final int SQL_DISPLAY_PREVIEW_MAX_LINES = 3;
    private static final int SQL_DISPLAY_PREVIEW_LINE_CHARS = 96;

    private final ToolExecutionSettings settings;
    private final SimplyJDBC simplyJDBC = new SimplyJDBC();

    public SqliteTools(ToolExecutionSettings settings) {
        this.settings = settings;
    }

    public ToolResult sqliteQuery(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String dbPathText = readFirstString(args, List.of("dbPath", "path"));
        String sql = readFirstString(args, List.of("sql", "query"));
        if (isBlank(dbPathText) || isBlank(sql)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required arguments: dbPath and sql", null, true);
        }

        ParsedSql parsedSql;
        try {
            parsedSql = parseAndClassify(sql);
        } catch (JSQLParserException e) {
            return ToolPayloads.failure(request, "invalid_sql_kind", "sqlite_query SQL parse failed", null, true);
        }

        if (parsedSql.statements().size() != 1) {
            return ToolPayloads.failure(request, "validation_error", "sqlite_query expects exactly one statement", null, true);
        }

        ParsedStatement parsedStatement = parsedSql.statements().getFirst();
        if (parsedStatement.type() != StatementType.READ) {
            return ToolPayloads.failure(request, "invalid_sql_kind", "sqlite_query only supports read-only SQL", null, true);
        }

        Path dbPath;
        try {
            dbPath = ToolPathSupport.resolveWorkspacePath(settings.workspaceRoot(), settings.enforceWorkspaceRoot(), dbPathText);
        } catch (IllegalArgumentException e) {
            return ToolPayloads.failure(request, "validation_error", e.getMessage(), null, true);
        }

        try {
            SQLiteDataSource dataSource = sqliteDataSource(dbPath);
            List<String> columns;
            try (Connection connection = dataSource.getConnection()) {
                String statement = parsedStatement.sql();
                columns = discoverColumns(connection, statement);
                if (columns.isEmpty()) {
                    ObjectNode data = MAPPER.createObjectNode();
                    data.put("kind", "sqlite_query_result");
                    data.putObject("database")
                            .put("dbPath", ToolPathSupport.displayPath(settings.workspaceRoot(), dbPath));
                    data.put("sqlPreview", sqlDisplayPreview(statement));
                    ObjectNode result = data.putObject("result");
                    result.put("rowCount", 0);
                    result.put("truncated", false);
                    result.set("columns", MAPPER.createArrayNode());
                    result.set("rows", MAPPER.createArrayNode());
                    return ToolPayloads.success(request, "sqlite_query_result", "Query completed", data);
                }

                String wrappedSql = buildJsonWrappedSelect(statement, columns);
                SjResult<JsonRow> rowsResult = simplyJDBC.query(connection, wrappedSql, List.of(settings.maxSqlRows() + 1), JsonRow.class);
                if (rowsResult.isFailure()) {
                    String message = rowsResult.error().map(Throwable::getMessage).orElse("Database query failed");
                    return ToolPayloads.failure(request, "db_error", message, null, true);
                }

                List<JsonRow> rowValues = rowsResult.rows();
                boolean truncated = rowValues.size() > settings.maxSqlRows();
                int resultSize = Math.min(rowValues.size(), settings.maxSqlRows());

                ArrayNode rows = MAPPER.createArrayNode();
                for (int i = 0; i < resultSize; i++) {
                    String rowJson = rowValues.get(i).rowJson();
                    if (rowJson == null || rowJson.isBlank()) {
                        rows.add(MAPPER.createObjectNode());
                        continue;
                    }
                    rows.add(MAPPER.readTree(rowJson));
                }

                ArrayNode columnsNode = MAPPER.createArrayNode();
                columns.forEach(columnsNode::add);

                ObjectNode data = MAPPER.createObjectNode();
                data.put("kind", "sqlite_query_result");
                data.putObject("database")
                        .put("dbPath", ToolPathSupport.displayPath(settings.workspaceRoot(), dbPath));
                data.put("sqlPreview", sqlDisplayPreview(statement));
                ObjectNode result = data.putObject("result");
                result.put("rowCount", rows.size());
                result.put("truncated", truncated);
                result.set("columns", columnsNode);
                result.set("rows", rows);
                if (truncated) {
                    result.put("previewSummary", "Rows truncated to maxSqlRows=" + settings.maxSqlRows());
                }
                return ToolPayloads.success(request, "sqlite_query_result", "Query completed", data);
            }
        } catch (Exception e) {
            return ToolPayloads.failure(request, "db_error", "Failed to query sqlite: " + e.getMessage(), null, true);
        }
    }

    public ToolResult sqliteExec(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String dbPathText = readFirstString(args, List.of("dbPath", "path"));
        String sql = readFirstString(args, List.of("sql", "statement"));
        boolean transactional = boolValue(args.get("transactional"), true);

        if (isBlank(dbPathText) || isBlank(sql)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required arguments: dbPath and sql", null, true);
        }

        ParsedSql parsedSql;
        try {
            parsedSql = parseAndClassify(sql);
        } catch (JSQLParserException e) {
            return ToolPayloads.failure(request, "invalid_sql_kind", "sqlite_exec SQL parse failed", null, true);
        }

        if (parsedSql.statements().isEmpty()) {
            return ToolPayloads.failure(request, "validation_error", "No SQL statements provided", null, true);
        }

        for (ParsedStatement statement : parsedSql.statements()) {
            if (statement.type() == StatementType.READ) {
                return ToolPayloads.failure(request, "invalid_sql_kind", "sqlite_exec does not allow read-only SQL statements", null, true);
            }
            if (statement.type() == StatementType.ATTACH || statement.type() == StatementType.PRAGMA || statement.type() == StatementType.UNKNOWN) {
                return ToolPayloads.failure(request, "invalid_sql_kind", "sqlite_exec blocked unsafe or unsupported SQL statement", null, true);
            }
        }

        Path dbPath;
        try {
            dbPath = ToolPathSupport.resolveWorkspacePath(settings.workspaceRoot(), settings.enforceWorkspaceRoot(), dbPathText);
        } catch (IllegalArgumentException e) {
            return ToolPayloads.failure(request, "validation_error", e.getMessage(), null, true);
        }

        ArrayNode statementsNode = MAPPER.createArrayNode();
        ArrayNode changedTablesNode = MAPPER.createArrayNode();
        List<String> changedTables = new ArrayList<>();
        int totalRowsAffected = 0;
        long lastInsertRowId = 0L;

        SQLiteDataSource dataSource = sqliteDataSource(dbPath);
        try (Connection connection = dataSource.getConnection()) {
            if (transactional) {
                connection.setAutoCommit(false);
            }
            try {
                for (ParsedStatement parsedStatement : parsedSql.statements()) {
                    StatementType type = parsedStatement.type();
                    String statementSql = parsedStatement.sql();
                    SjResult<Integer> updateResult = simplyJDBC.executeUpdate(connection, statementSql, SimplyJDBC.NO_PARAMS);
                    if (updateResult.isFailure()) {
                        String message = updateResult.error().map(Throwable::getMessage).orElse("SQL execution failed");
                        throw new IllegalStateException(message);
                    }

                    int rows = updateResult.first().orElse(0);
                    if (type == StatementType.WRITE) {
                        totalRowsAffected += rows;
                        long insertId = queryLastInsertRowId(connection);
                        if (insertId > 0) {
                            lastInsertRowId = insertId;
                        }
                    }

                    String kind = statementKind(statementSql, type);
                    String table = statementTable(statementSql);
                    if (!table.isBlank() && !changedTables.contains(table)) {
                        changedTables.add(table);
                        changedTablesNode.add(table);
                    }
                    ObjectNode detail = MAPPER.createObjectNode();
                    detail.put("index", statementsNode.size() + 1);
                    detail.put("kind", kind);
                    if (!table.isBlank()) {
                        detail.put("table", table);
                    }
                    detail.put("rowsAffected", rows);
                    if ("insert".equals(kind) && lastInsertRowId > 0) {
                        detail.put("lastInsertRowId", lastInsertRowId);
                    }
                    detail.put("sqlPreview", sqlPreview(statementSql));
                    statementsNode.add(detail);
                }

                if (transactional) {
                    connection.commit();
                    connection.setAutoCommit(true);
                }
            } catch (Exception executionError) {
                if (transactional) {
                    try {
                        connection.rollback();
                    } catch (Exception ignored) {
                        // best effort rollback
                    }
                }
                throw executionError;
            }

            ObjectNode data = MAPPER.createObjectNode();
            data.put("kind", "sqlite_exec_receipt");
            data.putObject("database")
                    .put("dbPath", ToolPathSupport.displayPath(settings.workspaceRoot(), dbPath));
            data.put("sqlPreview", sqlDisplayPreview(sql));
            ObjectNode receipt = data.putObject("receipt");
            receipt.put("statementCount", parsedSql.statements().size());
            receipt.put("rowsAffected", totalRowsAffected);
            receipt.put("transactional", transactional);
            receipt.put("lastInsertRowId", lastInsertRowId);
            receipt.set("changedTables", changedTablesNode);
            receipt.set("statements", statementsNode);
            return ToolPayloads.success(request, "sqlite_exec_receipt", "SQL mutation applied", data);
        } catch (Exception e) {
            return ToolPayloads.failure(request, "db_error", "Failed to execute sqlite statements: " + e.getMessage(), null, true);
        }
    }

    private JsonNode readArgsOrNull(ToolRequest request) {
        String argsJson = request.toolCall().argumentsJson();
        if (isBlank(argsJson)) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(argsJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readFirstString(JsonNode args, List<String> keys) {
        if (args == null || !args.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode node = args.get(key);
            if (node != null && node.isTextual()) {
                return node.asText();
            }
        }
        return null;
    }

    private boolean boolValue(JsonNode node, boolean defaultValue) {
        return node == null || !node.isBoolean() ? defaultValue : node.asBoolean();
    }

    private SQLiteDataSource sqliteDataSource(Path dbPath) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath().normalize());
        return dataSource;
    }

    private List<String> discoverColumns(Connection connection, String sql) throws Exception {
        String inspect = "SELECT * FROM (" + sql + ") m2_columns LIMIT 0";
        try (PreparedStatement statement = connection.prepareStatement(inspect);
             var resultSet = statement.executeQuery()) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                String label = metadata.getColumnLabel(i);
                columns.add(label == null || label.isBlank() ? ("col_" + i) : label);
            }
            return columns;
        }
    }

    private String buildJsonWrappedSelect(String sql, List<String> columns) {
        StringBuilder sb = new StringBuilder("SELECT json_object(");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String column = columns.get(i);
            sb.append('\'').append(escapeSqlString(column)).append('\'')
                    .append(',')
                    .append("q.\"").append(column.replace("\"", "\"\"")).append("\"");
        }
        sb.append(") AS row_json FROM (").append(sql).append(") q LIMIT ?");
        return sb.toString();
    }

    private String escapeSqlString(String text) {
        return text.replace("'", "''");
    }

    private long queryLastInsertRowId(Connection connection) {
        SjResult<LastInsertIdRow> result = simplyJDBC.query(
                connection,
                "SELECT last_insert_rowid() AS last_insert_row_id",
                SimplyJDBC.NO_PARAMS,
                LastInsertIdRow.class
        );
        if (result.isFailure()) {
            return 0L;
        }
        return result.first().map(LastInsertIdRow::lastInsertRowId).orElse(0L);
    }

    private String statementKind(String sql, StatementType type) {
        String upper = sql == null ? "" : sql.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("INSERT") || upper.startsWith("REPLACE")) {
            return "insert";
        }
        if (upper.startsWith("UPDATE")) {
            return "update";
        }
        if (upper.startsWith("DELETE")) {
            return "delete";
        }
        if (upper.startsWith("CREATE")) {
            return "create";
        }
        if (upper.startsWith("DROP")) {
            return "drop";
        }
        if (upper.startsWith("ALTER")) {
            return "alter";
        }
        if (upper.startsWith("TRUNCATE")) {
            return "truncate";
        }
        return switch (type) {
            case DDL -> "ddl";
            case TRANSACTION -> "transaction";
            default -> type.name().toLowerCase(Locale.ROOT);
        };
    }

    private String statementTable(String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }
        String normalized = sql.replace('\n', ' ').replace('\r', ' ').trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        String keyword = "";
        if (upper.startsWith("INSERT INTO ")) {
            keyword = "INSERT INTO ";
        } else if (upper.startsWith("UPDATE ")) {
            keyword = "UPDATE ";
        } else if (upper.startsWith("DELETE FROM ")) {
            keyword = "DELETE FROM ";
        } else if (upper.startsWith("CREATE TABLE ")) {
            keyword = "CREATE TABLE ";
        } else if (upper.startsWith("DROP TABLE ")) {
            keyword = "DROP TABLE ";
        } else if (upper.startsWith("ALTER TABLE ")) {
            keyword = "ALTER TABLE ";
        }
        if (keyword.isEmpty()) {
            return "";
        }
        String remainder = normalized.substring(keyword.length()).trim();
        int splitIndex = remainder.indexOf(' ');
        String token = splitIndex < 0 ? remainder : remainder.substring(0, splitIndex);
        return token.replaceAll("[\"'`;()]", "").trim();
    }

    private String sqlPreview(String sql) {
        if (sql == null) {
            return "";
        }
        String compact = sql.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        int max = 96;
        if (compact.length() <= max) {
            return compact;
        }
        return compact.substring(0, max - 3) + "...";
    }

    private String sqlDisplayPreview(String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }
        String[] physicalLines = sql.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> previewLines = new ArrayList<>(SQL_DISPLAY_PREVIEW_MAX_LINES);
        boolean truncated = false;
        for (String physical : physicalLines) {
            String normalized = physical == null ? "" : physical.replaceAll("\\s+", " ").trim();
            if (normalized.isBlank()) {
                continue;
            }
            while (!normalized.isBlank()) {
                if (previewLines.size() >= SQL_DISPLAY_PREVIEW_MAX_LINES) {
                    truncated = true;
                    break;
                }
                int take = Math.min(SQL_DISPLAY_PREVIEW_LINE_CHARS, normalized.length());
                if (normalized.length() > SQL_DISPLAY_PREVIEW_LINE_CHARS) {
                    int wordBoundary = normalized.lastIndexOf(' ', SQL_DISPLAY_PREVIEW_LINE_CHARS);
                    if (wordBoundary >= 20) {
                        take = wordBoundary;
                    }
                }
                previewLines.add(normalized.substring(0, take).trim());
                normalized = normalized.substring(take).trim();
            }
            if (truncated) {
                break;
            }
        }
        if (previewLines.isEmpty()) {
            return "";
        }
        if (truncated) {
            int last = previewLines.size() - 1;
            String tail = previewLines.get(last);
            if (!tail.endsWith("...")) {
                if (tail.length() > SQL_DISPLAY_PREVIEW_LINE_CHARS - 3) {
                    tail = tail.substring(0, SQL_DISPLAY_PREVIEW_LINE_CHARS - 3).trim();
                }
                previewLines.set(last, tail + "...");
            }
        }
        return String.join("\n", previewLines);
    }

    private ParsedSql parseAndClassify(String sql) throws JSQLParserException {
        Statements parsed = CCJSqlParserUtil.parseStatements(sql);
        List<ParsedStatement> statements = new ArrayList<>();
        for (Statement statement : parsed.getStatements()) {
            if (statement == null) {
                continue;
            }
            String statementSql = statement.toString().trim();
            if (statementSql.isEmpty()) {
                continue;
            }
            statements.add(new ParsedStatement(statementSql, classifyStatement(statement, statementSql)));
        }
        return new ParsedSql(List.copyOf(statements));
    }

    private StatementType classifyStatement(Statement statement, String statementSql) {
        String upper = statementSql.toUpperCase(Locale.ROOT);
        if (upper.startsWith("PRAGMA")) {
            return StatementType.PRAGMA;
        }
        if (upper.startsWith("ATTACH") || upper.startsWith("DETACH")) {
            return StatementType.ATTACH;
        }

        if (statement instanceof Select) {
            return StatementType.READ;
        }
        if (statement instanceof Insert || statement instanceof Update || statement instanceof Delete
                || statement instanceof Merge || upper.startsWith("REPLACE")) {
            return StatementType.WRITE;
        }
        if (statement instanceof Drop || statement instanceof Alter || statement instanceof Truncate
                || statement.getClass().getName().contains(".statement.create.")) {
            return StatementType.DDL;
        }

        if (upper.startsWith("BEGIN") || upper.startsWith("COMMIT") || upper.startsWith("ROLLBACK") || upper.startsWith("SAVEPOINT")) {
            return StatementType.TRANSACTION;
        }

        return StatementType.UNKNOWN;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private record JsonRow(@SjColumn("row_json") String rowJson) {
    }

    private record LastInsertIdRow(@SjColumn("last_insert_row_id") long lastInsertRowId) {
    }

    private enum StatementType {
        READ,
        WRITE,
        DDL,
        ATTACH,
        PRAGMA,
        TRANSACTION,
        UNKNOWN
    }

    private record ParsedStatement(String sql, StatementType type) {
    }

    private record ParsedSql(List<ParsedStatement> statements) {
    }
}
