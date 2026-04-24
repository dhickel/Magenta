package io.mindspice.magenta2.ai.chat.model;

import java.util.List;

public record ChatSessionsResponse(
    List<String> conversationIds
) {
}
