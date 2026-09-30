package com.ej1.app.product.interfaces.rest.mapper;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.port.CreateProductCommand;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.product.domain.port.UpdateProductCommand;
import com.ej1.app.product.interfaces.rest.dto.BrandRef;
import com.ej1.app.product.interfaces.rest.dto.CategoryRef;
import com.ej1.app.product.interfaces.rest.dto.ProductQueryRequest;
import com.ej1.app.product.interfaces.rest.dto.ProductRequest;
import com.ej1.app.product.interfaces.rest.dto.ProductResponse;

import java.util.List;

/**
 * Puerto del mapper REST. Traduce entre los DTOs HTTP ({@link ProductRequest},
 * {@link ProductResponse}) y los tipos de dominio ({@link Product},
 * {@link CreateProductCommand}, {@link UpdateProductCommand}).
 *
 * <p>Esta interfaz vive en el paquete de mapper del adaptador de entrada y no
 * lleva anotaciones de Spring ni de MapStruct. El adaptador del lado de
 * infraestructura ({@code ProductRestMapperAdapter}) la implementa; la inyección
 * de dependencias de Spring resuelve el bean concreto en tiempo de ejecución.
 */
public interface ProductRestMapper {

    ProductResponse toResponse(Product product);

    /**
     * Mapea cada elemento con {@link #toResponse(Product)}. Lo usa cada endpoint de listado
     * paginado ({@code ProductController.findAll}/{@code query}), cuyo cuerpo es la simple
     * lista de resultados — los metadatos de paginación viajan en cabeceras de respuesta en
     * lugar de en un record contenedor (ver {@code com.ej1.app.common.rest.PageHeaders}).
     */
    List<ProductResponse> toResponseList(List<Product> products);

    CreateProductCommand toCreateCommand(ProductRequest request);

    UpdateProductCommand toUpdateCommand(ProductRequest request);

    /**
     * Proyecta un {@link Product} existente sobre la forma editable de {@link ProductRequest}
     * (excluye {@code id}/{@code createdAt}/{@code updatedAt}). Se usa como documento base de
     * JSON Patch (RFC 6902) para {@code PATCH /api/products/{id}} — las operaciones de patch
     * solo pueden apuntar a campos presentes en este documento, así que los campos controlados
     * por el servidor quedan implícitamente protegidos.
     */
    ProductRequest toRequest(Product product);

    /**
     * Traduce el cuerpo de la consulta HTTP ({@link ProductQueryRequest}, solo filtros) al
     * {@link ProductSearchCriteria} del dominio — refleja {@link #toCreateCommand}/
     * {@link #toUpdateCommand} para el caso de uso de consulta. La paginación/orden viajan
     * por separado (como parámetros de consulta en el lado del controller, y como su propio
     * parámetro en {@code ProductService#search}) — ninguno de los dos tipos los transporta.
     */
    ProductSearchCriteria toCriteria(ProductQueryRequest request);

    /**
     * Combina {@code categoryId}/{@code categoryName} — dos campos separados en
     * {@link Product}, ya que el dominio referencia el agregado de categoría solo por
     * id y transporta el nombre como una etiqueta de solo lectura para mostrar (ver el
     * javadoc a nivel de clase de {@link Product}) — en el único {@link CategoryRef}
     * anidado que expone la respuesta. {@code null} cuando el producto no tiene categoría.
     */
    default CategoryRef toCategoryRef(Product product) {
        return product.getCategoryId() == null ? null
                : new CategoryRef(product.getCategoryId(), product.getCategoryName());
    }

    /** Refleja {@link #toCategoryRef(Product)} para {@code brand}/{@link BrandRef}. */
    default BrandRef toBrandRef(Product product) {
        return product.getBrandId() == null ? null
                : new BrandRef(product.getBrandId(), product.getBrandName());
    }
}
