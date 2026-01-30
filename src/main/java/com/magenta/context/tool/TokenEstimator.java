package com.magenta.context.tool;

import com.magenta.context.model.ContextElement;

import java.util.List;

public class TokenEstimator {
    // Rough estimation: 4 characters per token
    private static final int CHARS_PER_TOKEN = 4;

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / CHARS_PER_TOKEN;
    }

    public static int estimate(List<ContextElement> elements) {
        return elements.stream()
                .mapToInt(ContextElement::estimatedTokens)
                .sum();
    }
}