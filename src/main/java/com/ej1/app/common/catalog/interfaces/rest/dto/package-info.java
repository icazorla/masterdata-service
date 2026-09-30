/**
 * DTOs REST para la API de catálogos (peticiones/respuestas intercambiadas por HTTP).
 *
 * <p>Marcado con {@link org.jspecify.annotations.NullMarked} para que los parámetros/
 * argumentos de tipo en este paquete (p. ej. {@code ResponseEntity<CatalogResponse>})
 * se consideren no nulos por defecto, en línea con la seguridad frente a nulos basada
 * en JSpecify de Spring Framework 7. Cualquier campo o parámetro aquí que pueda ser
 * legítimamente {@code null} debe anotarse explícitamente con
 * {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package com.ej1.app.common.catalog.interfaces.rest.dto;

import org.jspecify.annotations.NullMarked;
