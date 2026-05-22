package io.mindspice.magenta2.avatar;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.mindspice.magenta2.ai.orchestration.runtime.EventType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationEvent;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationEventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AvatarEmailAlertIngressService {
    private static final int SUBJECT_SNIPPET_LIMIT = 160;
    private static final Pattern ADDRESS_DOMAIN = Pattern.compile("@([^>\\s]+)");

    private final OrchestrationEventService eventService;
    private final String ingressToken;

    public AvatarEmailAlertIngressService(
        OrchestrationEventService eventService,
        @Value("${magenta.avatar.email-alert-token:}") String ingressToken
    ) {
        this.eventService = eventService;
        this.ingressToken = StringUtils.hasText(ingressToken) ? ingressToken.trim() : null;
    }

    public OrchestrationEvent ingest(String token, EmailAlertRequest request) {
        requireValidToken(token);
        if (request == null) {
            throw new IllegalArgumentException("email alert payload is required");
        }
        String messageId = requireText(request.messageId(), "messageId");
        Map<String, Object> payload = redactedPayload(request, messageId);
        return eventService.publish(
            EventType.EMAIL_ALERT_RECEIVED,
            "EMAIL_ALERT",
            payload.get("messageIdHash").toString(),
            payload
        );
    }

    Map<String, Object> redactedPayload(EmailAlertRequest request, String messageId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageIdHash", hash(messageId));
        String fromAddress = trimToNull(request.fromAddress());
        String fromDomain = fromDomain(fromAddress);
        if (fromDomain != null) {
            payload.put("fromDomain", fromDomain);
            payload.put("fromAddressHash", hash(fromAddress.toLowerCase()));
        }
        payload.put("subjectSnippet", snippet(request.subject(), SUBJECT_SNIPPET_LIMIT));
        payload.put("receivedAt", receivedAt(request.receivedAt()));
        payload.put("labels", cleanLabels(request.labels()));
        String importance = trimToNull(request.importance());
        if (importance != null) {
            payload.put("importance", importance);
        }
        String threadKey = trimToNull(request.threadKey());
        if (threadKey != null) {
            payload.put("threadKeyHash", hash(threadKey));
        }
        return payload;
    }

    private void requireValidToken(String token) {
        if (!StringUtils.hasText(ingressToken)) {
            throw new IllegalStateException("Avatar email alert ingress token is not configured");
        }
        if (!constantTimeEquals(ingressToken, token == null ? null : token.trim())) {
            throw new SecurityException("invalid Avatar email alert token");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String fromDomain(String fromAddress) {
        if (!StringUtils.hasText(fromAddress)) {
            return null;
        }
        var matcher = ADDRESS_DOMAIN.matcher(fromAddress);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        return null;
    }

    private String receivedAt(String value) {
        if (!StringUtils.hasText(value)) {
            return Instant.now().toString();
        }
        return Instant.parse(value.trim()).toString();
    }

    private List<String> cleanLabels(List<String> labels) {
        if (labels == null) {
            return List.of();
        }
        return labels.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .limit(20)
            .toList();
    }

    private String snippet(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record EmailAlertRequest(
        String messageId,
        String fromAddress,
        String subject,
        String receivedAt,
        List<String> labels,
        String importance,
        String threadKey,
        String body
    ) {
    }
}
