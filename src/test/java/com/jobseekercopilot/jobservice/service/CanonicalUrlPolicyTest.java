package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CanonicalUrlPolicyTest {

    @Test
    void acceptsOnlyAbsoluteCredentialFreeHttpUrls() {
        assertThat(CanonicalUrlPolicy.safeHttpUrl(
                "https://jobs.example.test/path?source=provider"))
                .isEqualTo(
                        "https://jobs.example.test/path?source=provider");
        assertThat(CanonicalUrlPolicy.safeHttpUrl(
                " http://jobs.example.test/role "))
                .isEqualTo("http://jobs.example.test/role");
        assertThat(CanonicalUrlPolicy.safeHttpUrl(
                "HTTPS://jobs.example.test/role"))
                .isEqualTo("https://jobs.example.test/role");

        for (String unsafe : new String[] {
                "javascript:alert(1)",
                "data:text/html,unsafe",
                "file:///etc/passwd",
                "ftp://jobs.example.test/role",
                "/relative/path",
                "https://user:password@jobs.example.test/role",
                "https://",
                "not a url"}) {
            assertThat(CanonicalUrlPolicy.safeHttpUrl(unsafe))
                    .as("unsafe URL %s", unsafe)
                    .isNull();
        }
    }
}
