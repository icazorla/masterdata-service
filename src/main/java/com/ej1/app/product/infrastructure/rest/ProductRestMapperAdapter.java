package com.ej1.app.product.infrastructure.rest;

import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.product.interfaces.rest.dto.ProductQueryRequest;
import com.ej1.app.product.interfaces.rest.mapper.ProductRestMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct-backed implementation of {@link ProductRestMapper}.
 *
 * <p>The {@code @Mapper} annotation lives here (not on the port) so that
 * the generated {@code ProductRestMapperAdapterImpl} is emitted next to its
 * infrastructure neighbours, free of any constraint that would force it into
 * the inbound adapter's package. The resulting bean implements
 * {@link ProductRestMapper} and is registered as a Spring component via
 * {@code componentModel = "spring"}.
 */
@Mapper(componentModel = "spring")
public interface ProductRestMapperAdapter extends ProductRestMapper {

    /**
     * Overridden (rather than left to the inherited default mapping) purely to attach
     * {@code @Mapping(defaultValue = ...)}: every field of {@code request.getPage()} — the
     * nested, entity-agnostic {@link com.ej1.app.shared.rest.dto.PagingRequest} — is
     * deliberately {@code null}-able (see {@link ProductQueryRequest}'s javadoc — Jackson's
     * all-args-constructor deserialization bypasses plain field initializers), so the paging/
     * sort defaults have to be resolved here, in the one place already dedicated to MapStruct
     * config for this port. {@code source = "page.number"} etc. navigate into the nested
     * object; MapStruct generates the null-safe hop for when {@code page} itself is absent
     * from the request, falling back to {@code defaultValue} exactly as it would for a
     * missing top-level property. Note {@code sortBy}'s default ({@code "id"}) is the only
     * *value* here that's Product-specific — {@code ProductService.search} is what actually
     * enforces the Product-specific allowed set for whatever value ends up here.
     */
    @Override
    @Mapping(target = "page", source = "page.number", defaultValue = "0")
    @Mapping(target = "size", source = "page.size", defaultValue = "20")
    @Mapping(target = "sortBy", source = "page.sortBy", defaultValue = "id")
    @Mapping(target = "sortDirection", source = "page.sortDirection", defaultValue = "ASC")
    ProductSearchCriteria toCriteria(ProductQueryRequest request);
}
