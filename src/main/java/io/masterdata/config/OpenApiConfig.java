package io.masterdata.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
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
 * <p>La API se publica como dos documentos OpenAPI separados (ver {@link #queryApi()} y
 * {@link #adminApi()}), seleccionables desde el desplegable de Swagger UI.
 *
 * <p>UI: {@code /swagger-ui.html} — Specs: {@code /v3/api-docs/consulta}, {@code /v3/api-docs/gestion}
 */
@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH = "basicAuth";

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("app API")
                        .description("Spring Boot 4.1 starter project — catálogos de datos maestros (Hexagonal Architecture)")
                        .version("v0.0.1")
                        .contact(new Contact().name("app"))
                        .license(new License().name("Unlicense")));
    }

    /** API de consulta: solo lectura, localizada según {@code Accept-Language} — para clientes finales. */
    @Bean
    public GroupedOpenApi queryApi() {
        return GroupedOpenApi.builder()
                .group("consulta")
                .displayName("Consulta (/api)")
                .pathsToMatch("/api/**")
                .build();
    }

    /**
     * API de gestión: CRUD completo con todas las traducciones — para el backoffice. Requiere
     * rol {@code ADMIN} (ver {@code SecurityConfig}): se declara HTTP Basic en todas sus
     * operaciones para que Swagger UI muestre el botón <em>Authorize</em> y envíe las credenciales.
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("gestion")
                .displayName("Gestión (/admin/api)")
                .pathsToMatch("/admin/api/**")
                .addOpenApiCustomizer(openApi -> openApi
                        .schemaRequirement(BASIC_AUTH, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"))
                        .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH)))
                .build();
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
