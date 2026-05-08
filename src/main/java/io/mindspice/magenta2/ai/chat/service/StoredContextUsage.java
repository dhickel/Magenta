package io.mindspice.magenta2.ai.chat.service;

import io.mindspice.magenta2.ai.chat.model.ContextUsage;

public record StoredContextUsage(ContextUsage usage, boolean compacted) {
}
