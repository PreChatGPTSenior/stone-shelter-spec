package lab.stoneshelter;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import lab.stoneshelter.mappers.entities.AbstractEntityMapper;
import lab.stoneshelter.mappers.dtos.AbstractDtoMapper;
import lab.stoneshelter.mappers.entities.StoneCardSaveRequestToStoneCardEmbeddingEntityMapper;
import lab.stoneshelter.mappers.dtos.StoneCardEmbeddingEntityToStoneCardSaveResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneCardSearchResultEntityToStoneCardSearchResponseMapper;
import lab.stoneshelter.shared.StoneCardSaveRequest;
import lab.stoneshelter.shared.StoneCardSearchRequest;
import lab.stoneshelter.shared.StoneCardSearchResponse;
import lab.stoneshelter.shared.StoneCardMatchResponse;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class StoneCardEmbeddingArchitectureTest {
    @Test
    void servicesAndPersistenceKeepTheirDependencyBoundaries() {
        JavaClasses javaClasses = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("lab.stoneshelter");
        noClasses().that().resideInAPackage("..services..").and().haveSimpleNameStartingWith("StoneCard")
                .should().dependOnClassesThat().resideInAnyPackage("..controllers..", "..handlers..", "jakarta.persistence..",
                        "org.springframework.data.jpa..", "org.springframework.http..", "org.springframework.web..")
                .check(javaClasses);
        noClasses().that().resideInAPackage("..repositories..").and().haveSimpleNameStartingWith("StoneCard")
                .should().dependOnClassesThat().resideInAnyPackage("..shared..", "..mappers..", "..services..")
                .check(javaClasses);
        noClasses().that().resideInAPackage("..controllers..")
                .should().dependOnClassesThat().haveSimpleNameStartingWith("StoneCard").check(javaClasses);
    }

    @Test
    void dtoAndMapperShapesFollowTheConstitution() {
        assertThat(StoneCardSaveRequest.class.isRecord()).isFalse();
        assertThat(StoneCardSaveRequestToStoneCardEmbeddingEntityMapper.class).isAssignableTo(AbstractEntityMapper.class);
        assertThat(StoneCardEmbeddingEntityToStoneCardSaveResponseMapper.class).isAssignableTo(AbstractDtoMapper.class);
        assertThat(StoneCardSearchResultEntityToStoneCardSearchResponseMapper.class).isAssignableTo(AbstractDtoMapper.class);
        for (Class<?> type : new Class<?>[] { StoneCardSearchRequest.class, StoneCardSearchResponse.class, StoneCardMatchResponse.class }) {
            assertThat(type.getPackageName()).isEqualTo("lab.stoneshelter.shared");
            assertThat(type.getRecordComponents().length).isLessThanOrEqualTo(4);
        }
    }
}
