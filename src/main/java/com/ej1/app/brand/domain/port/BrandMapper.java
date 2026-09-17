package com.ej1.app.brand.domain.port;

import com.ej1.app.brand.domain.Brand;
import com.ej1.app.brand.domain.BrandTranslation;

import java.util.List;

/**
 * Domain mapper port. Maps {@link CreateBrandCommand} / {@link UpdateBrandCommand}
 * to the {@link Brand} aggregate.
 *
 * <p>This interface is part of the domain contract but carries no Spring or MapStruct
 * annotations — those concerns are pushed to the infrastructure adapter
 * ({@code BrandMapperAdapter}), which implements this port and is bound to Spring.
 */
public interface BrandMapper {

    Brand toDomain(CreateBrandCommand cmd);

    default Brand applyUpdate(Brand existing, UpdateBrandCommand cmd) {
        return existing.toBuilder()
                .translations(toTranslations(cmd.translations()))
                .build();
    }

    private static List<BrandTranslation> toTranslations(List<BrandTranslationCommand> commands) {
        return commands.stream()
                .map(c -> new BrandTranslation(c.locale(), c.name(), c.description()))
                .toList();
    }
}
