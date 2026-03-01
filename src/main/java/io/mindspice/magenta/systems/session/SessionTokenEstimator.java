package io.mindspice.magenta.systems.session;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionTokenEstimator {
    private static final String DEFAULT_ENCODING = "cl100k_base";
    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Map<String, Encoding> ENCODING_CACHE = new ConcurrentHashMap<>();

    private SessionTokenEstimator() {}

    public static int estimate(List<SessionMessage> messages) {
        return estimate(messages, DEFAULT_ENCODING);
    }

    public static int estimate(List<SessionMessage> messages, String encodingName) {
        int total = 0;
        for (SessionMessage message : messages) {
            total += estimateMessage(message, encodingName);
        }
        return total;
    }

    public static int estimateMessage(SessionMessage message) {
        return estimateMessage(message, DEFAULT_ENCODING);
    }

    public static int estimateMessage(SessionMessage message, String encodingName) {
        int total = estimateText(message.content(), encodingName);
        if (message instanceof SessionMessage.AssistantMsg assistant) {
            for (SessionMessage.ToolCall call : assistant.toolCalls()) {
                total += estimateText(call.name() + call.argumentsJson(), encodingName);
            }
        }
        return total;
    }

    public static int estimateText(String text) {
        return estimateText(text, DEFAULT_ENCODING);
    }

    public static int estimateText(String text, String encodingName) {
        if (text == null || text.isBlank()) {
            return 1;
        }
        Encoding encoding = resolveEncoding(encodingName);
        return Math.max(1, encoding.countTokens(text));
    }

    private static Encoding resolveEncoding(String encodingName) {
        String normalized = normalize(encodingName);
        return ENCODING_CACHE.computeIfAbsent(normalized, name -> {
            EncodingType type = EncodingType.valueOf(name.toUpperCase());
            return REGISTRY.getEncoding(type);
        });
    }

    private static String normalize(String encodingName) {
        if (encodingName == null || encodingName.isBlank()) {
            return DEFAULT_ENCODING;
        }
        return encodingName.trim().replace('-', '_').toLowerCase();
    }
}
