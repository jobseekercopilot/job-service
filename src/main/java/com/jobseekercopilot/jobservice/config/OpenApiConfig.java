package com.jobseekercopilot.jobservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI jobServiceOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .info(new Info()
                        .title("Jobseeker Copilot - Job Service API")
                        .description("""
                                Canonical multi-provider job search orchestration.

                                The service calls provider gateways, maps and normalises results,
                                deduplicates sources, and reports partial provider failures.

                                Saved jobs retain owner-scoped immutable canonical/source
                                snapshots for later generation and application workflows.
                                """)
                        .version("2.1.0")
                        .contact(new Contact()
                                .name("Jobseeker Copilot"))
                        .license(new License()
                                .name("Proprietary source-available")));
    }
}
