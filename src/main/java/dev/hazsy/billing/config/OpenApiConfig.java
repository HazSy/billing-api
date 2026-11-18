package dev.hazsy.billing.config;

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
    public OpenAPI billingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Billing API")
                        .version("1.0.0")
                        .description("""
                                Facturación con Spring Boot 3.
                                Autentícate en POST /api/auth/token y pega el token con el botón Authorize.
                                """)
                        .contact(new Contact().name("Óscar López Pellicer").url("https://hazsy.github.io")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
