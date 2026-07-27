package com.jobseekercopilot.jobservice.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionDatabaseVerifierTest {

    @Test
    void acceptsReviewedEncryptedPostgresqlConfiguration() {
        ProductionDatabaseVerifier verifier =
                new ProductionDatabaseVerifier(validEnvironment());

        assertDoesNotThrow(verifier::verifyConfiguration);
    }

    @Test
    void rejectsUnsafeDatabaseAndMigrationConfigurationsWithoutEchoingSecrets() {
        assertUnsafe(environment -> environment.setProperty(
                "spring.datasource.url",
                "jdbc:h2:mem:unsafe"));
        assertUnsafe(environment -> environment.setProperty(
                "spring.datasource.hikari.data-source-properties.sslmode",
                "disable"));
        assertUnsafe(environment -> environment.setProperty(
                "spring.datasource.username",
                "postgres"));
        assertUnsafe(environment -> environment.setProperty(
                "spring.datasource.password",
                "short-secret"));
        assertUnsafe(environment -> environment.setProperty(
                "job-service.database.encryption-at-rest-enabled",
                "false"));
        assertUnsafe(environment -> environment.setProperty(
                "job-service.database.backup-encryption-enabled",
                "false"));
        assertUnsafe(environment -> environment.setProperty(
                "spring.flyway.enabled",
                "false"));
        assertUnsafe(environment -> environment.setProperty(
                "spring.flyway.clean-disabled",
                "false"));
        assertUnsafe(environment -> environment.setProperty(
                "spring.jpa.hibernate.ddl-auto",
                "update"));
        assertUnsafe(environment -> environment.setProperty(
                "spring.h2.console.enabled",
                "true"));
        assertUnsafe(environment -> environment.setProperty(
                "spring.jpa.show-sql",
                "true"));
    }

    @Test
    void isolatedTestsCanExplicitlyDisableTheProductionGuard() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(
                        "job-service.database.production-safety-check",
                        "false");

        assertDoesNotThrow(
                new ProductionDatabaseVerifier(environment)::verifyConfiguration);
    }

    private void assertUnsafe(Consumer<MockEnvironment> change) {
        MockEnvironment environment = validEnvironment();
        change.accept(environment);
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                new ProductionDatabaseVerifier(environment)::verifyConfiguration);
        assertFalse(failure.getMessage().contains(
                environment.getProperty("spring.datasource.password")));
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty(
                        "job-service.database.production-safety-check",
                        "true")
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://database.internal/job_service")
                .withProperty(
                        "spring.datasource.hikari.data-source-properties.sslmode",
                        "verify-full")
                .withProperty(
                        "spring.datasource.username",
                        "job_service_runtime")
                .withProperty(
                        "spring.datasource.password",
                        "test-only-database-password-32-bytes")
                .withProperty(
                        "job-service.database.encryption-at-rest-enabled",
                        "true")
                .withProperty(
                        "job-service.database.encryption-key-reference",
                        "test-key-reference")
                .withProperty(
                        "job-service.database.backup-encryption-enabled",
                        "true")
                .withProperty(
                        "job-service.database.backup-key-reference",
                        "test-backup-key-reference")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.flyway.clean-disabled", "true")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.h2.console.enabled", "false")
                .withProperty("spring.jpa.show-sql", "false");
    }
}
