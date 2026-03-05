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

        List<String> statements = splitStatements(sql);
        if (statements.size() != 1) {
            return ToolPayloads.failure(request, "validation_error", "sqlite_query expects exactly one statement", null, true);
        }

        String statement = statements.get(0);
        if (detectStatementType(statement) != StatementType.READ) {
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

        List<String> statements = splitStatements(sql);
        if (statements.isEmpty()) {
            return ToolPayloads.failure(request, "validation_error", "No SQL statements provided", null, true);
        }

        for (String statement : statements) {
            if (detectStatementType(statement) == StatementType.READ) {
                return ToolPayloads.failure(request, "invalid_sql_kind", "sqlite_exec does not allow read-only SQL statements", null, true);
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
                for (String statement : statements) {
                    StatementType type = detectStatementType(statement);
                    SjResult<Integer> updateResult = simplyJDBC.executeUpdate(connection, statement, SimplyJDBC.NO_PARAMS);
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
                    detail.put("sql", statement);
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
            data.put("statementCount", statements.size());
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

    private List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        char[] chars = sql.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char next = (i + 1 < chars.length) ? chars[i + 1] : '\0';
            char prev = (i > 0) ? chars[i - 1] : '\0';

            if (!inSingleQuote && !inDoubleQuote && !inBlockComment && c == '-' && next == '-') {
                inLineComment = true;
                current.append(c);
                continue;
            }
            if (inLineComment && c == '\n') {
                inLineComment = false;
                current.append(c);
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote && !inLineComment && c == '/' && next == '*') {
                inBlockComment = true;
                current.append(c);
                continue;
            }
            if (inBlockComment && c == '*' && next == '/') {
                current.append(c).append(next);
                i++;
                inBlockComment = false;
                continue;
            }

            if (inLineComment || inBlockComment) {
                current.append(c);
                continue;
            }

            if (c == '\'' && prev != '\\') {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && prev != '\\') {
                inDoubleQuote = !inDoubleQuote;
            }

            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) {
            statements.add(last);
        }
        return statements;
    }

    private StatementType detectStatementType(String statement) {
        String normalized = stripLeadingCommentsAndWhitespace(statement);
        if (normalized.isBlank()) {
            return StatementType.UNKNOWN;
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.startsWith("WITH")) {
            int insertIndex = findKeywordAfterCte(upper, "INSERT");
            int updateIndex = findKeywordAfterCte(upper, "UPDATE");
            int deleteIndex = findKeywordAfterCte(upper, "DELETE");
            int replaceIndex = findKeywordAfterCte(upper, "REPLACE");
            int selectIndex = findKeywordAfterCte(upper, "SELECT");
            int createIndex = findKeywordAfterCte(upper, "CREATE");
            int dropIndex = findKeywordAfterCte(upper, "DROP");
            int alterIndex = findKeywordAfterCte(upper, "ALTER");

            int min = Integer.MAX_VALUE;
            StatementType type = StatementType.UNKNOWN;
            if (insertIndex != -1 && insertIndex < min) { min = insertIndex; type = StatementType.WRITE; }
            if (updateIndex != -1 && updateIndex < min) { min = updateIndex; type = StatementType.WRITE; }
            if (deleteIndex != -1 && deleteIndex < min) { min = deleteIndex; type = StatementType.WRITE; }
            if (replaceIndex != -1 && replaceIndex < min) { min = replaceIndex; type = StatementType.WRITE; }
            if (selectIndex != -1 && selectIndex < min) { min = selectIndex; type = StatementType.READ; }
            if (createIndex != -1 && createIndex < min) { min = createIndex; type = StatementType.DDL; }
            if (dropIndex != -1 && dropIndex < min) { min = dropIndex; type = StatementType.DDL; }
            if (alterIndex != -1 && alterIndex < min) { type = StatementType.DDL; }
            return type;
        }

        if (upper.startsWith("SELECT") || upper.startsWith("PRAGMA")) return StatementType.READ;
        if (upper.startsWith("INSERT") || upper.startsWith("UPDATE") || upper.startsWith("DELETE") || upper.startsWith("REPLACE")) {
            return StatementType.WRITE;
        }
        if (upper.startsWith("CREATE") || upper.startsWith("DROP") || upper.startsWith("ALTER")) return StatementType.DDL;
        if (upper.startsWith("ATTACH") || upper.startsWith("DETACH")) return StatementType.ATTACH;
        return StatementType.UNKNOWN;
    }

    private String stripLeadingCommentsAndWhitespace(String sql) {
        String result = sql == null ? "" : sql.trim();
        boolean changed = true;

        while (changed) {
            changed = false;
            if (result.startsWith("--")) {
                int newline = result.indexOf('\n');
                if (newline == -1) {
                    return "";
                }
                result = result.substring(newline + 1).trim();
                changed = true;
            }
            if (result.startsWith("/*")) {
                int end = result.indexOf("*/");
                if (end == -1) {
                    return "";
                }
                result = result.substring(end + 2).trim();
                changed = true;
            }
        }

        return result;
    }

    private int findKeywordAfterCte(String sql, String keyword) {
        int parenDepth = 0;
        boolean inQuote = false;

        for (int i = 4; i < sql.length() - keyword.length() + 1; i++) {
            char c = sql.charAt(i);
            if (c == '\'' && (i == 0 || sql.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            }
            if (inQuote) {
                continue;
            }
            if (c == '(') {
                parenDepth++;
            } else if (c == ')') {
                parenDepth = Math.max(0, parenDepth - 1);
            }
            if (parenDepth == 0 && sql.startsWith(keyword, i) && isWordBoundary(sql, i, keyword.length())) {
                return i;
            }
        }
        return -1;
    }

    private boolean isWordBoundary(String sql, int start, int length) {
        boolean startBoundary = start == 0 || !Character.isLetterOrDigit(sql.charAt(start - 1));
        int end = start + length;
        boolean endBoundary = end >= sql.length() || !Character.isLetterOrDigit(sql.charAt(end));
        return startBoundary && endBoundary;
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
        UNKNOWN
    }
}
