package com.jobseekercopilot.jobservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

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

                                Saved-job persistence is planned but is not implemented in this
                                migration baseline.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Jobseeker Copilot"))
                        .license(new License()
                                .name("Proprietary source-available")));
    }
}
