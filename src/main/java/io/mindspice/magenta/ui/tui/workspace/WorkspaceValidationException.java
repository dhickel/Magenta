package io.mindspice.magenta.ui.tui.workspace;

public final class WorkspaceValidationException extends IllegalStateException {
    private final String status;
    private final String code;
    private final String workspaceId;
    private final String field;

    public WorkspaceValidationException(
            String code,
            String workspaceId,
            String field,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.status = "failed";
        this.code = require(code, "code");
        this.workspaceId = workspaceId;
        this.field = field;
    }

    public String status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String field() {
        return field;
    }

    @Override
    public String getMessage() {
        String base = super.getMessage() == null ? "workspace validation failed" : super.getMessage();
        return "status=" + status
                + ", code=" + code
                + ", workspaceId=" + (workspaceId == null ? "" : workspaceId)
                + ", field=" + (field == null ? "" : field)
                + ", message=" + base;
    }

    private String require(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
