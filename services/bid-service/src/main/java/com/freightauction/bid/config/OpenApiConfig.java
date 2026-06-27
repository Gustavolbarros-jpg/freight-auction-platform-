package com.freightauction.bid.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bidServiceOpenAPI() {
        return new OpenAPI()
                .info(buildInfo(
                        "Bid Service API",
                        "Gerenciamento de lances em leilões",
                        "1.0.0"
                ))
                .components(new Components()
                        .addSecuritySchemes("Bearer Token", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token obtido no login")));
    }

    private Info buildInfo(String title, String description, String version) {
        return new Info()
                .title(title)
                .version(version)
                .description(description)
                .contact(new Contact()
                        .name("API Support")
                        .email("api@freightauction.com"));
    }
}