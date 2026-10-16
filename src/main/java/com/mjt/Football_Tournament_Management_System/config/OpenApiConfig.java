package com.mjt.Football_Tournament_Management_System.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter JWT token in the format: Bearer <token>")
                        )
                )
                .info(new Info()
                        .title("Football Tournament Management System")
                        .description("""
Football Tournament Management System – REST API to manage tournaments, teams, players, registrations, fixtures and scores.

Auth: JWT (POST /api/auth/login). Include header: Authorization: Bearer <token>.
Public: /api/public/**  •  Teams: /api/teams/**  •  Admin: /api/admin/**

Key features:
• Create tournaments with registration/match windows
• Team signup and player lists
• Round-robin fixture generation & manual fixtures
• Score updates and team dashboards
""")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Manjit Patel")
                                .email("manjitpatel002@gmail.com")
                                .url("https://www.linkedin.com/in/manjitpatel/")
                        )
                        .license(new License()
                                .name("License of APIs")
                                .url("https://github.com/Manjit002")
                        )
                );

    }
}
