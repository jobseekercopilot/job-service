package com.jobseekercopilot.jobservice.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CorrelationIdFilterTest {

    @Test
    void preservesOnlyBoundedLogSafeCorrelationIds() {
        assertEquals("trace_123.example-test", CorrelationIdFilter.safeCorrelationId(
                "trace_123.example-test"));

        for (String unsafe : new String[]{
                "",
                "contains a space",
                "line\r\nbreak",
                "slash/value",
                "a".repeat(129)}) {
            String replacement = CorrelationIdFilter.safeCorrelationId(unsafe);
            assertNotEquals(unsafe, replacement);
            assertTrue(replacement.matches("[a-f0-9-]{36}"));
        }
    }
}
