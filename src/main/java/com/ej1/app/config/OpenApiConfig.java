package com.ej1.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

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

    /**
     * Documents {@code Accept-Language} on every operation in Swagger UI. Spring already
     * resolves this header on its own (see {@code AcceptHeaderLocaleResolver} and
     * {@code ApiExceptionHandler}/{@code messages*.properties}) — Swagger UI just has no way
     * to know it exists unless it's declared on the OpenAPI spec, since it's never bound as a
     * {@code @RequestHeader} on any controller method.
     *
     * <p>{@code app.i18n.supported-locales} (comma-separated) drives the dropdown of valid
     * values so it stays in sync with whichever {@code messages_<locale>.properties} bundles
     * actually exist, instead of being hardcoded here.
     */
    @Bean
    public GlobalOperationCustomizer acceptLanguageHeaderCustomizer(
            @Value("${app.i18n.supported-locales:en}") String supportedLocalesCsv,
            @Value("${spring.web.locale:en}") String defaultLocale) {
        List<String> supportedLocales = Arrays.stream(supportedLocalesCsv.split(","))
                .map(String::trim)
                .filter(locale -> !locale.isEmpty())
                .toList();
        return (operation, handlerMethod) -> operation.addParametersItem(
                new Parameter()
                        .in("header")
                        .name("Accept-Language")
                        .description("Idioma de los mensajes de error")
                        .required(false)
                        .schema(new StringSchema()._default(defaultLocale)._enum(supportedLocales)));
    }
}
