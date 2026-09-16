package com.ej1.app.category.domain.service;

import com.ej1.app.category.domain.Category;
import com.ej1.app.category.domain.exception.CategoryNotFoundException;
import com.ej1.app.category.domain.exception.DuplicateCategoryNameException;
import com.ej1.app.category.domain.port.CategoryMapper;
import com.ej1.app.category.domain.port.CategoryRepository;
import com.ej1.app.category.domain.port.CreateCategoryCommand;
import com.ej1.app.category.domain.port.UpdateCategoryCommand;
import com.ej1.app.category.infrastructure.persistence.CategoryMapperAdapter;
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
class CategoryServiceTest {

    @Mock
    private CategoryRepository repository;

    @Spy
    private CategoryMapper mapper = Mappers.getMapper(CategoryMapperAdapter.class);

    @InjectMocks
    private CategoryService service;

    private Category sample;
    private CreateCategoryCommand validCreate;
    private UpdateCategoryCommand validUpdate;

    @BeforeEach
    void setUp() {
        sample = Category.builder()
                .id(1L)
                .name("Periféricos")
                .description("Teclados, ratones y accesorios")
                .build();

        validCreate = new CreateCategoryCommand("Periféricos", "Teclados, ratones y accesorios");
        validUpdate = new UpdateCategoryCommand("Periféricos", "Teclados, ratones y accesorios");
    }

    @Test
    @DisplayName("findById devuelve la categoría cuando existe")
    void findById_found() {
        when(repository.findById(1L)).thenReturn(Optional.of(sample));

        assertThat(service.findById(1L)).isEqualTo(sample);
    }

    @Test
    @DisplayName("findById lanza CategoryNotFoundException cuando no existe")
    void findById_notFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("findAll delega en el repositorio")
    void findAll_delegatesToRepository() {
        when(repository.findAll()).thenReturn(List.of(sample));

        assertThat(service.findAll()).containsExactly(sample);
    }

    @Test
    @DisplayName("create persiste la categoría cuando el nombre es nuevo")
    void create_persistsWhenNameNew() {
        when(repository.existsByNameIgnoreCase("Periféricos")).thenReturn(false);
        when(repository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = service.create(validCreate);

        assertThat(result.getName()).isEqualTo("Periféricos");
        verify(repository).save(any(Category.class));
    }

    @Test
    @DisplayName("create lanza DuplicateCategoryNameException cuando el nombre ya existe")
    void create_throwsWhenNameDuplicated() {
        when(repository.existsByNameIgnoreCase("Periféricos")).thenReturn(true);

        assertThatThrownBy(() -> service.create(validCreate))
                .isInstanceOf(DuplicateCategoryNameException.class)
                .hasMessageContaining("Periféricos");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update modifica la categoría cuando el nombre no cambia")
    void update_modifiesWhenNameUnchanged() {
        UpdateCategoryCommand updateCmd = new UpdateCategoryCommand("Periféricos", "Nueva descripción");
        when(repository.findById(1L)).thenReturn(Optional.of(sample));
        when(repository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = service.update(1L, updateCmd);

        assertThat(result.getDescription()).isEqualTo("Nueva descripción");
        verify(repository, never()).existsByNameIgnoreCase(any());
    }

    @Test
    @DisplayName("update detecta duplicado cuando el nombre cambia a uno existente")
    void update_throwsWhenNewNameExists() {
        UpdateCategoryCommand updateCmd = new UpdateCategoryCommand("Pantallas", "desc");
        when(repository.findById(1L)).thenReturn(Optional.of(sample));
        when(repository.existsByNameIgnoreCase("Pantallas")).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, updateCmd))
                .isInstanceOf(DuplicateCategoryNameException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update lanza CategoryNotFoundException cuando el id no existe")
    void update_throwsWhenIdNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, validUpdate))
                .isInstanceOf(CategoryNotFoundException.class);

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
    @DisplayName("delete lanza CategoryNotFoundException cuando el id no existe")
    void delete_throwsWhenNotFound() {
        when(repository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(repository, never()).deleteById(any());
    }
}
