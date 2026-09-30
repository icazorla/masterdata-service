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
 * Metadatos de OpenAPI/Swagger. Configuración pura de Spring — vive fuera del
 * paquete de la feature {@code product} ya que describe la API en su conjunto,
 * no un agregado concreto.
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
     * Documenta {@code Accept-Language} en cada operación de Swagger UI. Spring ya resuelve
     * esta cabecera por su cuenta (ver {@code AcceptHeaderLocaleResolver} y
     * {@code ApiExceptionHandler}/{@code messages*.properties}) — Swagger UI simplemente no
     * tiene forma de saber que existe a menos que se declare en el spec de OpenAPI, ya que
     * nunca se vincula como un {@code @RequestHeader} en ningún método de controlador.
     *
     * <p>{@code app.i18n.supported-locales} (separado por comas) alimenta el desplegable de
     * valores válidos para que se mantenga sincronizado con los bundles
     * {@code messages_<locale>.properties} que realmente existan, en lugar de estar
     * codificado aquí de forma fija.
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
