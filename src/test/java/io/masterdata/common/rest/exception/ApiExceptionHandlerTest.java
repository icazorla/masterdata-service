package io.masterdata.common.rest.exception;

import io.masterdata.common.rest.patch.JsonPatch;
import io.masterdata.common.rest.patch.JsonPatchException;
import io.masterdata.common.rest.patch.JsonPatchOperation;
import io.masterdata.common.rest.patch.JsonPatchTestFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Manejadores de {@link ApiExceptionHandler} que los tests de controlador no alcanzan, llamados
 * directamente con los mensajes reales de {@code messages*.properties}.
 */
class ApiExceptionHandlerTest {

    private static final Locale ES = Locale.forLanguageTag("es");

    private final ApiExceptionHandler handler = new ApiExceptionHandler(messageSource());

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static <T extends Throwable> T patchFailure(Class<T> type, JsonPatchOperation op) {
        return catchThrowableOfType(type, () -> JsonPatch.apply(Map.of("foo", "bar"), List.of(op)));
    }

    @Test
    @DisplayName("Un test de JSON Patch fallido es 409, con el detalle de la operación traducido dentro del mensaje")
    void jsonPatchTestFailedIs409WithNestedMessage() {
        JsonPatchTestFailedException ex = patchFailure(JsonPatchTestFailedException.class,
                new JsonPatchOperation("test", "/foo", "baz", null));

        ResponseEntity<Map<String, Object>> response = handler.handleJsonPatchTestFailed(ex, ES);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("status", 409);
        assertThat((String) response.getBody().get("message"))
                .startsWith("Operación 0")
                .contains("se esperaba baz pero era bar");
    }

    @Test
    @DisplayName("Un JSON Patch inválido es 400, con el motivo de la operación traducido dentro del mensaje")
    void invalidJsonPatchIs400WithNestedMessage() {
        JsonPatchException ex = patchFailure(JsonPatchException.class,
                new JsonPatchOperation("remove", "/missing", null, null));

        ResponseEntity<Map<String, Object>> response = handler.handleInvalidJsonPatch(ex, ES);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("message")).contains("'missing'");
    }

    @Test
    @DisplayName("Una violación de integridad de la base de datos es 409")
    void dataIntegrityViolationIs409() {
        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key"), Locale.ENGLISH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message",
                "The resource cannot be saved or deleted because it is referenced by other records");
    }

    @Test
    @DisplayName("Un cuerpo ilegible es 400")
    void unreadableBodyIs400() {
        ResponseEntity<Map<String, Object>> response = handler.handleNotReadable(
                new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])), Locale.ENGLISH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Malformed request body");
    }

    @Test
    @DisplayName("Un 405 sin métodos soportados conocidos no lleva cabecera Allow")
    void methodNotAllowedWithoutSupportedMethodsHasNoAllowHeader() {
        ResponseEntity<Map<String, Object>> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("TRACE"), Locale.ENGLISH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).isEmpty();
        assertThat(response.getBody()).containsEntry("message", "Method TRACE not allowed on this resource");
    }

    @Test
    @DisplayName("Una excepción sin manejador propio es 500 y no expone su mensaje interno")
    void unexpectedExceptionIs500() {
        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(
                new IllegalStateException("secret internal detail"), Locale.ENGLISH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "Internal server error");
        assertThat(response.getBody().toString()).doesNotContain("secret internal detail");
    }
}
