package com.mesaayuda.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Mesa de Ayuda COAC API",
                version = "1.0.0",
                description = "API REST del Sistema de Mesa de Ayuda / Help Desk. "
                        + "Roles: ADMIN, AGENT, USER. Autenticación mediante JWT Bearer.",
                contact = @Contact(name = "Mesa de Ayuda COAC", email = "soporte@coac.local"),
                license = @License(name = "Uso interno")
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}