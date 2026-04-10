package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MobileAdapterExceptionTest {

    @Test
    void carriesChannelTypeAndMessage() {
        MobileAdapterException ex = new MobileAdapterException("telegram", "API error");
        assertEquals("telegram", ex.channelType());
        assertEquals("API error", ex.getMessage());
    }

    @Test
    void wrapsCause() {
        IOException cause = new IOException("connection reset");
        MobileAdapterException ex = new MobileAdapterException("discord", "send failed", cause);
        assertEquals("discord", ex.channelType());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isRuntimeException() {
        MobileAdapterException ex = new MobileAdapterException("telegram", "test");
        assertTrue(ex instanceof RuntimeException);
    }
}
