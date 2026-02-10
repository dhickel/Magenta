package io.mindspice.sjbdc;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SimplyJDBC {
    public static final Map<String, Object> NO_PARAMS = Collections.emptyMap();

    public SimplyJDBC(SjOptions options) {}

    public QueryResult<Integer> executeUpdate(Connection connection, String sql, Map<String, Object> params) {
        return new QueryResult<>(0);
    }

    public <T> QueryResult<List<T>> query(Connection connection, String sql, Map<String, Object> params, Class<T> type) {
        return new QueryResult<>(Collections.emptyList());
    }

    public static class QueryResult<T> {
        private final T rows;
        public QueryResult(T rows) { this.rows = rows; }
        public QueryResult<T> onError(Consumer<Exception> handler) { return this; }
        public T rows() { return rows; }
    }
}
