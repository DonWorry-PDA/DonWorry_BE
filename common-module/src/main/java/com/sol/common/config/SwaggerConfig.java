package com.sol.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"local", "dev", "prod"})
public class SwaggerConfig {

    private static final String BEARER_AUTH = "BearerAuth";

    @Value("${swagger.server-url:}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("DonWorry API Docs")
                        .description("DonWorry 백엔드 API 명세서")
                        .version("v1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));

        if (!serverUrl.isBlank()) {
            if (!serverUrl.startsWith("https://")) {
                throw new IllegalArgumentException(
                        "Swagger server URL must use HTTPS: " + serverUrl);
            }
            openAPI.addServersItem(new Server().url(serverUrl));
        }

        return openAPI;
    }
}
