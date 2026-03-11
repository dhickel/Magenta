package io.mindspice.magenta.runtime.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaClientFailureClassificationTest {

    @Test
    void classifiesHttpContextOverflowBody() throws Exception {
        OllamaClient client = new OllamaClient();
        Method classifier = OllamaClient.class.getDeclaredMethod(
                "classifyHttpFailure",
                String.class,
                int.class,
                String.class
        );
        classifier.setAccessible(true);

        ModelClientException error = (ModelClientException) classifier.invoke(
                client,
                "chat",
                400,
                "{\"error\":\"context length exceeded\"}"
        );

        assertThat(error.reason()).isEqualTo(ModelClientException.Reason.CONTEXT_OVERFLOW);
        assertThat(error.statusCode()).isEqualTo(400);
    }

    @Test
    void classifiesDoneReasonLengthAsOutputTruncated() throws Exception {
        OllamaClient client = new OllamaClient();
        Method classifier = OllamaClient.class.getDeclaredMethod(
                "classifyDoneReasonFailure",
                String.class,
                int.class,
                String.class
        );
        classifier.setAccessible(true);

        ModelClientException error = (ModelClientException) classifier.invoke(
                client,
                "length",
                200,
                "{\"done_reason\":\"length\"}"
        );

        assertThat(error).isNotNull();
        assertThat(error.reason()).isEqualTo(ModelClientException.Reason.OUTPUT_TRUNCATED);
        assertThat(error.doneReason()).isEqualTo("length");
    }
}
