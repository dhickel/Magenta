package io.mindspice.magenta.runtime.session;

public final class SessionException extends RuntimeException {
    private final SessionHandle sessionHandle;

    public SessionException(SessionHandle sessionHandle, Throwable cause) {
        super(cause == null ? "" : cause.getMessage(), cause);
        this.sessionHandle = sessionHandle;
    }

    public SessionHandle sessionHandle() {
        return sessionHandle;
    }
}
