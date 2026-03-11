package io.mindspice.magenta.runtime.model;

public final class ModelClientException extends RuntimeException {

    public enum Reason {
        CONTEXT_OVERFLOW("context_overflow"),
        OUTPUT_TRUNCATED("output_truncated"),
        STREAM_INCOMPLETE("stream_incomplete"),
        HTTP_ERROR("http_error"),
        MALFORMED_RESPONSE("malformed_response");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Reason reason;
    private final int statusCode;
    private final String doneReason;
    private final String responsePreview;

    public ModelClientException(
            Reason reason,
            String message,
            int statusCode,
            String doneReason,
            String responsePreview,
            Throwable cause
    ) {
        super(message == null ? "" : message, cause);
        this.reason = reason == null ? Reason.HTTP_ERROR : reason;
        this.statusCode = statusCode;
        this.doneReason = doneReason == null ? "" : doneReason;
        this.responsePreview = responsePreview == null ? "" : responsePreview;
    }

    public static ModelClientException of(Reason reason, String message) {
        return new ModelClientException(reason, message, 0, "", "", null);
    }

    public Reason reason() {
        return reason;
    }

    public int statusCode() {
        return statusCode;
    }

    public String doneReason() {
        return doneReason;
    }

    public String responsePreview() {
        return responsePreview;
    }
}
