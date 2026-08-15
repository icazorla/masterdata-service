package com.ej1.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger metadata. Pure Spring wiring — lives outside the
 * {@code product} feature package since it describes the API as a whole,
 * not any single aggregate.
 *
 * <p>UI: {@code /swagger-ui.html} — Spec: {@code /v3/api-docs}
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("app API")
                        .description("Spring Boot 4.1 starter project — Product CRUD (Hexagonal Architecture)")
                        .version("v0.0.1")
                        .contact(new Contact().name("app"))
                        .license(new License().name("Unlicense")));
    }
}
