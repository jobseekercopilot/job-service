package com.jobseekercopilot.jobservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobseekercopilot.jobservice.model.dto.ReedJobSearchResponse;
import com.jobseekercopilot.jobservice.service.JobSearchService;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
class JobServiceSecurityIntegrationTest {

    private static final TestJwksServer JWKS = new TestJwksServer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("job-service.security.jwk-set-uri", JWKS::jwkSetUri);
        registry.add("job-service.security.issuer", () -> TestJwksServer.ISSUER);
        registry.add("job-service.security.audience", () -> TestJwksServer.AUDIENCE);
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobSearchService jobSearchService;

    @BeforeEach
    void response() {
        when(jobSearchService.searchJobs(anyString(), any()))
                .thenReturn(new ReedJobSearchResponse(
                        List.of(), List.of(), 0, 1, 10, List.of()));
    }

    @Test
    void verifiedSubjectIsTheOnlySearchIdentity() throws Exception {
        mockMvc.perform(authenticatedSearch(JWKS.activeToken("alice"))
                        .header("X-User-Id", "victim"))
                .andExpect(status().isOk());

        verify(jobSearchService).searchJobs(eq("alice"), any());
        verify(jobSearchService, never()).searchJobs(eq("victim"), any());
    }

    @Test
    void activeAndPreviouslyPublishedKeysSupportRotation() throws Exception {
        mockMvc.perform(authenticatedSearch(JWKS.activeToken("active-owner")))
                .andExpect(status().isOk());
        mockMvc.perform(authenticatedSearch(JWKS.previousToken("previous-owner")))
                .andExpect(status().isOk());

        verify(jobSearchService).searchJobs(eq("active-owner"), any());
        verify(jobSearchService).searchJobs(eq("previous-owner"), any());
    }

    @Test
    void missingMalformedForgedExpiredAndConstrainedTokensFailUniformly() throws Exception {
        assertAuthenticationFailure(searchRequest().header("X-User-Id", "victim"), null);

        for (String token : List.of(
                "not-a-jwt",
                JWKS.expiredToken("expired-subject"),
                JWKS.forgedKnownKeyToken("forged-subject"),
                JWKS.unknownKeyToken("unknown-key-subject"),
                JWKS.wrongAlgorithmToken("wrong-algorithm-subject"),
                JWKS.wrongIssuerToken("wrong-issuer-subject"),
                JWKS.wrongAudienceToken("wrong-audience-subject"),
                JWKS.refreshTokenType("wrong-type-subject"),
                JWKS.missingSubjectToken(),
                JWKS.blankSubjectToken())) {
            assertAuthenticationFailure(authenticatedSearch(token), token);
        }

        verify(jobSearchService, never()).searchJobs(anyString(), any());
    }

    @Test
    void healthIsPublicWhileDocumentationAndUnrelatedRoutesFailClosed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/not-a-job-service-route"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCorrelationIdIsNotReflected() throws Exception {
        MvcResult result = mockMvc.perform(searchRequest()
                        .header("X-Correlation-Id", "attacker value"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertFalse(result.getResponse().getHeader("X-Correlation-Id").contains("attacker"));
        assertFalse(result.getResponse().getContentAsString().contains("attacker"));
    }

    private void assertAuthenticationFailure(
            MockHttpServletRequestBuilder request,
            String token) throws Exception {
        MvcResult result = mockMvc.perform(request
                        .header("X-Correlation-Id", "job-service-security-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "job-service-security-test"))
                .andExpect(jsonPath("$.schemaVersion").value("1"))
                .andExpect(jsonPath("$.code").value("JOB_SERVICE_AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value(
                        "A valid Bearer access token is required."))
                .andExpect(jsonPath("$.correlationId").value("job-service-security-test"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        if (token != null) {
            assertFalse(body.contains(token));
        }
        assertFalse(body.contains("subject"));
        assertFalse(body.contains("Jwt"));
    }

    private static MockHttpServletRequestBuilder authenticatedSearch(String token) {
        return searchRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder searchRequest() {
        return post("/api/jobs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "aspirations": {
                            "desiredRoles": ["Platform Engineer"],
                            "locations": ["London"]
                          }
                        }
                        """);
    }
}
