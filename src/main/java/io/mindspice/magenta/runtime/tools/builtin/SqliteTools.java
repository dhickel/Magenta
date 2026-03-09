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
            dbPath = ToolPathSupport.resolveWorkspacePath(settings.workspaceRoot(), dbPathText);
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
                    data.put("dbPath", ToolPathSupport.displayPath(settings.workspaceRoot(), dbPath));
                    data.put("rowCount", 0);
                    data.put("truncated", false);
                    data.set("columns", MAPPER.createArrayNode());
                    data.set("rows", MAPPER.createArrayNode());
                    return ToolPayloads.success(request, "Query completed", data);
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
                data.put("dbPath", ToolPathSupport.displayPath(settings.workspaceRoot(), dbPath));
                data.put("rowCount", rows.size());
                data.put("truncated", truncated);
                data.set("columns", columnsNode);
                data.set("rows", rows);
                return ToolPayloads.success(request, "Query completed", data);
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
            dbPath = ToolPathSupport.resolveWorkspacePath(settings.workspaceRoot(), dbPathText);
        } catch (IllegalArgumentException e) {
            return ToolPayloads.failure(request, "validation_error", e.getMessage(), null, true);
        }

        ArrayNode details = MAPPER.createArrayNode();
        int totalRowsAffected = 0;

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
                    }

                    ObjectNode detail = MAPPER.createObjectNode();
                    detail.put("statementType", type.name().toLowerCase(Locale.ROOT));
                    detail.put("rowsAffected", rows);
                    detail.put("sql", statementSql);
                    details.add(detail);
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
            data.put("dbPath", ToolPathSupport.displayPath(settings.workspaceRoot(), dbPath));
            data.put("statementCount", parsedSql.statements().size());
            data.put("rowsAffected", totalRowsAffected);
            data.put("transactional", transactional);
            data.set("details", details);
            return ToolPayloads.success(request, "SQL statements executed", data);
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
