package io.mindspice.magenta.runtime.session;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import io.mindspice.magenta.runtime.session.SessionMessage;
import io.mindspice.magenta.runtime.session.SessionTokenEstimator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokenEstimatorTest {

    @Test
    void estimateTextUsesJtokkitEncoding() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        Encoding encoding = registry.getEncoding(EncodingType.CL100K_BASE);
        String text = "Token counting should use tokenizer-backed estimation.";

        assertThat(SessionTokenEstimator.estimateText(text, "cl100k_base"))
                .isEqualTo(encoding.countTokens(text));
    }

    @Test
    void estimateTextNormalizesEncodingName() {
        String text = "normalized encoding name";
        int underscore = SessionTokenEstimator.estimateText(text, "cl100k_base");
        int hyphenated = SessionTokenEstimator.estimateText(text, "cl100k-base");

        assertThat(hyphenated).isEqualTo(underscore);
    }

    @Test
    void estimateMessageIncludesAssistantToolCallPayloads() {
        SessionMessage.AssistantMsg assistant = new SessionMessage.AssistantMsg(
                "",
                List.of(new SessionMessage.ToolCall("id-1", "read_file", "{\"path\":\"/tmp/data.txt\"}"))
        );

        int expected = SessionTokenEstimator.estimateText("", "cl100k_base")
                + SessionTokenEstimator.estimateText("read_file{\"path\":\"/tmp/data.txt\"}", "cl100k_base");

        assertThat(SessionTokenEstimator.estimateMessage(assistant, "cl100k_base"))
                .isEqualTo(expected);
    }
}
