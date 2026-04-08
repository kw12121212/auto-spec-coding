package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProcessOutputTest {

    @Test
    void constructionAndGetters() {
        long ts = System.currentTimeMillis();
        ProcessOutput output = new ProcessOutput("hello", "error", 0, ts);
        assertEquals("hello", output.stdout());
        assertEquals("error", output.stderr());
        assertEquals(0, output.exitCode());
        assertEquals(ts, output.timestamp());
    }

    @Test
    void stillRunningExitCode() {
        ProcessOutput output = new ProcessOutput("", "", -1, System.currentTimeMillis());
        assertEquals(-1, output.exitCode());
    }

    @Test
    void recordEquality() {
        long ts = 1000L;
        ProcessOutput a = new ProcessOutput("out", "err", 1, ts);
        ProcessOutput b = new ProcessOutput("out", "err", 1, ts);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
