package com.ai.gateway.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "AIRouter API",
                version = "v1",
                description = "OpenAI-compatible AIRouter Personal and Business gateway APIs.",
                contact = @Contact(
                        name = "AIRouter"
                )
        ),
        security = {
                @SecurityRequirement(name = "apiKey"),
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "apiKey",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-Key",
        description = "AIRouter API key. Personal keys use the arpk_ prefix."
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        description = "Personal session Bearer token."
)
public class OpenApiConfig {
}