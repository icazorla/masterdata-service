package com.ej1.app.brand.domain.service;

import com.ej1.app.brand.domain.Brand;
import com.ej1.app.brand.domain.exception.BrandNotFoundException;
import com.ej1.app.brand.domain.exception.DuplicateBrandNameException;
import com.ej1.app.brand.domain.port.BrandMapper;
import com.ej1.app.brand.domain.port.BrandRepository;
import com.ej1.app.brand.domain.port.CreateBrandCommand;
import com.ej1.app.brand.domain.port.UpdateBrandCommand;
import com.ej1.app.brand.infrastructure.persistence.BrandMapperAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository repository;

    @Spy
    private BrandMapper mapper = Mappers.getMapper(BrandMapperAdapter.class);

    @InjectMocks
    private BrandService service;

    private Brand sample;
    private CreateBrandCommand validCreate;
    private UpdateBrandCommand validUpdate;

    @BeforeEach
    void setUp() {
        sample = Brand.builder()
                .id(1L)
                .name("Logitech")
                .description("Fabricante de periféricos")
                .build();

        validCreate = new CreateBrandCommand("Logitech", "Fabricante de periféricos");
        validUpdate = new UpdateBrandCommand("Logitech", "Fabricante de periféricos");
    }

    @Test
    @DisplayName("findById devuelve la marca cuando existe")
    void findById_found() {
        when(repository.findById(1L)).thenReturn(Optional.of(sample));

        assertThat(service.findById(1L)).isEqualTo(sample);
    }

    @Test
    @DisplayName("findById lanza BrandNotFoundException cuando no existe")
    void findById_notFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(BrandNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("findAll delega en el repositorio")
    void findAll_delegatesToRepository() {
        when(repository.findAll()).thenReturn(List.of(sample));

        assertThat(service.findAll()).containsExactly(sample);
    }

    @Test
    @DisplayName("create persiste la marca cuando el nombre es nuevo")
    void create_persistsWhenNameNew() {
        when(repository.existsByNameIgnoreCase("Logitech")).thenReturn(false);
        when(repository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        Brand result = service.create(validCreate);

        assertThat(result.getName()).isEqualTo("Logitech");
        verify(repository).save(any(Brand.class));
    }

    @Test
    @DisplayName("create lanza DuplicateBrandNameException cuando el nombre ya existe")
    void create_throwsWhenNameDuplicated() {
        when(repository.existsByNameIgnoreCase("Logitech")).thenReturn(true);

        assertThatThrownBy(() -> service.create(validCreate))
                .isInstanceOf(DuplicateBrandNameException.class)
                .hasMessageContaining("Logitech");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update modifica la marca cuando el nombre no cambia")
    void update_modifiesWhenNameUnchanged() {
        UpdateBrandCommand updateCmd = new UpdateBrandCommand("Logitech", "Nueva descripción");
        when(repository.findById(1L)).thenReturn(Optional.of(sample));
        when(repository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        Brand result = service.update(1L, updateCmd);

        assertThat(result.getDescription()).isEqualTo("Nueva descripción");
        verify(repository, never()).existsByNameIgnoreCase(any());
    }

    @Test
    @DisplayName("update detecta duplicado cuando el nombre cambia a uno existente")
    void update_throwsWhenNewNameExists() {
        UpdateBrandCommand updateCmd = new UpdateBrandCommand("Samsung", "desc");
        when(repository.findById(1L)).thenReturn(Optional.of(sample));
        when(repository.existsByNameIgnoreCase("Samsung")).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, updateCmd))
                .isInstanceOf(DuplicateBrandNameException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update lanza BrandNotFoundException cuando el id no existe")
    void update_throwsWhenIdNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, validUpdate))
                .isInstanceOf(BrandNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("delete borra cuando el id existe")
    void delete_removesWhenExists() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    @DisplayName("delete lanza BrandNotFoundException cuando el id no existe")
    void delete_throwsWhenNotFound() {
        when(repository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(BrandNotFoundException.class);

        verify(repository, never()).deleteById(any());
    }
}
