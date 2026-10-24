package lab.stoneshelter;

import java.util.stream.Stream;
import lab.stoneshelter.handlers.ApiExceptionHandler;
import lab.stoneshelter.controllers.StoneCatalogController;
import lab.stoneshelter.services.StoneService;
import lab.stoneshelter.mappers.dtos.StonePhotoEntityToStonePhotoResponseMapper;
import lab.stoneshelter.services.MinioPhotoStorageService;
import lab.stoneshelter.mappers.dtos.AbstractDtoMapper;
import lab.stoneshelter.exceptions.MapperValidationException;
import lab.stoneshelter.mappers.dtos.StoneEntityToStoneCreateResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneEntityToStoneResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneEntityToStoneSearchResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneEntityToStoneUpdateResponseMapper;
import lab.stoneshelter.mappers.entities.AbstractEntityMapper;
import lab.stoneshelter.mappers.entities.StoneCreateRequestToStoneEntityMapper;
import lab.stoneshelter.mappers.entities.StoneUpdateRequestToStoneEntityMapper;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.shared.StoneUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MapperNullContractTest {
    static Stream<AbstractEntityMapper<?, StoneEntity>> entityMappers() {
        return Stream.of(new StoneCreateRequestToStoneEntityMapper(), new StoneUpdateRequestToStoneEntityMapper());
    }

    static Stream<AbstractDtoMapper<StoneEntity, ?>> dtoMappers() {
        return Stream.of(new StoneEntityToStoneCreateResponseMapper(new StonePhotoEntityToStonePhotoResponseMapper(mock(MinioPhotoStorageService.class))), new StoneEntityToStoneResponseMapper(new StonePhotoEntityToStonePhotoResponseMapper(mock(MinioPhotoStorageService.class))),
                new StoneEntityToStoneUpdateResponseMapper(), new StoneEntityToStoneSearchResponseMapper(new StonePhotoEntityToStonePhotoResponseMapper(mock(MinioPhotoStorageService.class))));
    }

    @ParameterizedTest
    @MethodSource("entityMappers")
    void nullRequestReturnsNullWithoutChangingTarget(AbstractEntityMapper<?, StoneEntity> mapper) {
        var entity = new StoneEntity();
        entity.setName("Original");
        entity.setPhoto("");
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toEntity(entity, null)).isNull();
        assertThat(mapper.toEntity(null, null)).isNull();
        assertThat(entity.getName()).isEqualTo("Original");
        assertThat(entity.getPhoto()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("dtoMappers")
    void nullEntityRaisesMappingValidationError(AbstractDtoMapper<StoneEntity, ?> mapper) {
        assertThatThrownBy(() -> mapper.toDto(null))
                .isInstanceOf(MapperValidationException.class)
                .hasMessage("Cannot map a null entity to a DTO.");
    }

    @Test
    void nonNullUpdateRequiresExistingTarget() {
        var mapper = new StoneUpdateRequestToStoneEntityMapper();
        assertThatThrownBy(() -> mapper.toEntity(null, new StoneUpdateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Target entity must not be null.");
    }

    @Test
    void mapperFailureReturnsInternalServerErrorProblemDetail() throws Exception {
        var service = mock(StoneService.class);
        when(service.get(7L)).thenThrow(new MapperValidationException("Cannot map a null entity to a DTO."));
        var mvc = MockMvcBuilders.standaloneSetup(new StoneCatalogController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/v1/stones/7"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("Cannot map a null entity to a DTO."));
    }
}
