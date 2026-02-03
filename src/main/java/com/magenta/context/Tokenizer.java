package com.magenta.context;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * Token counting using tiktoken (GPT-style tokenization).
 * Uses cl100k_base encoding which works for most modern LLMs.
 */
public class Tokenizer {
    private static final Tokenizer INSTANCE = new Tokenizer();
    private final Encoding encoding;

    private Tokenizer() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        // cl100k_base is used by GPT-4, GPT-3.5-turbo and works well for most LLMs
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    public static Tokenizer getInstance() {
        return INSTANCE;
    }

    /**
     * Count tokens in the given text.
     *
     * @param text Text to tokenize
     * @return Number of tokens
     */
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    /**
     * Estimate tokens with overhead for message structure.
     * Adds ~4 tokens per message for role and formatting.
     *
     * @param text Message content
     * @return Estimated tokens including overhead
     */
    public int countTokensWithOverhead(String text) {
        return countTokens(text) + 4; // Add overhead for message structure
    }

    /**
     * Static convenience method for token estimation.
     */
    public static int estimate(String text) {
        return getInstance().countTokens(text);
    }

    /**
     * Static convenience method with message overhead.
     */
    public static int estimateWithOverhead(String text) {
        return getInstance().countTokensWithOverhead(text);
    }
}
