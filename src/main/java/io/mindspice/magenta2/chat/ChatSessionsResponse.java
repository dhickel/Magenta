package io.mindspice.magenta2.chat;

import java.util.List;

public record ChatSessionsResponse(
    List<String> conversationIds
) {
}
