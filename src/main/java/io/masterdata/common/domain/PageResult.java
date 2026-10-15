package io.masterdata.common.domain;

import java.util.List;

/**
 * Una página de resultados de {@code T}: {@code content}/{@code page}/{@code size}/
 * {@code totalElements}/{@code totalPages}. Todo endpoint de listado paginado proyecta esto directamente
 * sobre su respuesta HTTP — la lista de resultados más
 * {@link io.masterdata.common.rest.PageHeaders} — sin un DTO envoltorio aparte.
 *
 * <p>Deliberadamente agnóstico del framework — el dominio no debe depender de
 * {@code org.springframework.data.domain.Page}. Se llama {@code PageResult}, no
 * {@code Page}, a propósito: un nombre distinto mantiene esa frontera visible de
 * un vistazo en una lista de imports, en lugar de depender solo del paquete para
 * distinguirlo del propio tipo de Spring Data con el mismo nombre corto. Los
 * adaptadores de infraestructura mapean sus propios tipos de paginación (p. ej.
 * el {@code Page<Entity>} de Spring Data) a este record.
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
