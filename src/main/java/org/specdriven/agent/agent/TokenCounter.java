package org.specdriven.agent.agent;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

import java.util.List;

/**
 * Token counting utility backed by jtokkit for accurate estimation.
 * Uses CL100K_BASE encoding (GPT-4 / GPT-3.5-turbo compatible).
 */
public final class TokenCounter {

    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    private TokenCounter() {
    }

    /**
     * Estimates the token count for the given text.
     *
     * @param text the text to count tokens for
     * @return non-negative token count
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        return ENCODING.countTokens(text);
    }

    /**
     * Estimates the total token count for all message content in the list.
     *
     * @param messages the messages to count tokens for
     * @return non-negative token count
     */
    public static int estimate(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        int total = 0;
        for (Message msg : messages) {
            if (msg.content() != null) {
                total += estimate(msg.content());
            }
        }
        return total;
    }
}
