package com.jobseekercopilot.jobservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.jobservice.logging.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JobServiceAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JobServiceAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String correlationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        if (correlationId == null) {
            correlationId = CorrelationIdFilter.safeCorrelationId(
                    request.getHeader(CorrelationIdFilter.HEADER_NAME));
        }
        response.setHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
        objectMapper.writeValue(response.getOutputStream(), new AuthenticationError(
                "1",
                "JOB_SERVICE_AUTHENTICATION_REQUIRED",
                "A valid Bearer access token is required.",
                correlationId));
    }

    private record AuthenticationError(
            String schemaVersion,
            String code,
            String message,
            String correlationId) {
    }
}
