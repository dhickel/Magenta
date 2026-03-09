package io.mindspice.magenta.runtime.persistence;

public final class CommonCommandResults {

    private CommonCommandResults() {
    }

    public record Success(String message) implements ToolCommandResult, SessionContextResult {
        public Success {
            message = message == null ? "ok" : message;
        }
    }

    public record Failure(String code, String message) implements ToolCommandResult, SessionContextResult {
        public Failure {
            code = code == null || code.isBlank() ? "failure" : code;
            message = message == null ? "" : message;
        }
    }
}
