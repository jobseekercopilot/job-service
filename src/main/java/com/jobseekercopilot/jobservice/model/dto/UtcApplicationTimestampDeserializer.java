package com.jobseekercopilot.jobservice.model.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Accepts the legacy offset-less timestamps emitted by Job Matching while
 * preserving the public Job contract as an RFC 3339 timestamp with an offset.
 *
 * <p>Application Tracker stores these values as UTC {@link LocalDateTime}s, so
 * interpreting the legacy representation at UTC does not invent a timezone.
 */
public final class UtcApplicationTimestampDeserializer
        extends JsonDeserializer<OffsetDateTime> {

    @Override
    public OffsetDateTime deserialize(
            JsonParser parser,
            DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (OffsetDateTime) context.handleUnexpectedToken(
                    OffsetDateTime.class,
                    parser);
        }
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            return (OffsetDateTime) context.handleWeirdStringValue(
                    OffsetDateTime.class,
                    value,
                    "Application timestamp must not be blank");
        }
        try {
            return OffsetDateTime.parse(value)
                    .withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value)
                        .atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException invalidTimestamp) {
                return (OffsetDateTime) context.handleWeirdStringValue(
                        OffsetDateTime.class,
                        value,
                        "Expected an RFC 3339 timestamp or a legacy UTC local timestamp");
            }
        }
    }
}
