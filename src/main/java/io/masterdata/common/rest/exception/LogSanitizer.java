package io.masterdata.common.rest.exception;

/**
 * Los mensajes de excepción incluyen texto que manda el cliente (valores de {@code filter}, rutas de
 * JSON Patch...). Sin limpiar, un {@code %0A} en la petición fabrica líneas falsas en el log, y un
 * valor enorme lo infla.
 */
final class LogSanitizer {

    static final int MAX_LENGTH = 500;

    private LogSanitizer() {
    }

    static String clean(String text) {
        String value = String.valueOf(text).replaceAll("\\p{Cntrl}", " ");
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) + "…" : value;
    }
}
