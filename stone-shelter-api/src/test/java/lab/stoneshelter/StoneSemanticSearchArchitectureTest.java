package lab.stoneshelter;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import lab.stoneshelter.mappers.entities.AbstractEntityMapper;
import lab.stoneshelter.mappers.dtos.AbstractDtoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class StoneSemanticSearchArchitectureTest {
    private final List<String> names = List.of(
            "lab.stoneshelter.controllers.StoneChatbotController",
            "lab.stoneshelter.criteria.StoneSemanticPassageCriteria",
            "lab.stoneshelter.criteria.StoneSemanticRetrievalCriteria",
            "lab.stoneshelter.criteria.StoneSemanticSearchCriteria",
            "lab.stoneshelter.criteria.StoneSemanticWish",
            "lab.stoneshelter.entities.StoneChatTurnEntity",
            "lab.stoneshelter.entities.StoneExactSearchPreparationEntity",
            "lab.stoneshelter.entities.StoneSemanticCandidateEntity",
            "lab.stoneshelter.entities.StoneSemanticCheckedCandidateEntity",
            "lab.stoneshelter.entities.StoneSemanticEvidenceResultEntity",
            "lab.stoneshelter.entities.StoneSemanticPreparedTurnEntity",
            "lab.stoneshelter.entities.StoneSemanticRetrievalResultEntity",
            "lab.stoneshelter.entities.StoneSemanticSearchExecutionEntity",
            "lab.stoneshelter.entities.StoneSemanticSearchResultEntity",
            "lab.stoneshelter.entities.StoneSemanticSearchStateEntity",
            "lab.stoneshelter.enums.StoneSemanticEvidenceVerdict",
            "lab.stoneshelter.enums.StoneSemanticSearchMode",
            "lab.stoneshelter.enums.StoneSemanticSupportSource",
            "lab.stoneshelter.exceptions.InvalidStoneSemanticSearchOutputException",
            "lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException",
            "lab.stoneshelter.mappers.dtos.StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper",
            "lab.stoneshelter.mappers.dtos.StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper",
            "lab.stoneshelter.mappers.dtos.StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper",
            "lab.stoneshelter.mappers.dtos.StoneSemanticSearchDecisionToStoneExactSearchDecisionMapper",
            "lab.stoneshelter.mappers.dtos.StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper",
            "lab.stoneshelter.mappers.entities.StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper",
            "lab.stoneshelter.mappers.entities.StoneSemanticSearchDecisionToStoneSemanticSearchCriteriaMapper",
            "lab.stoneshelter.repositories.StoneSemanticSearchRepository",
            "lab.stoneshelter.services.OllamaSemanticEvidenceProvider",
            "lab.stoneshelter.services.OllamaSemanticSearchProvider",
            "lab.stoneshelter.services.StoneChatAvailabilityService",
            "lab.stoneshelter.services.StoneChatbotService",
            "lab.stoneshelter.services.StoneExactSearchService",
            "lab.stoneshelter.services.StoneSemanticCandidateService",
            "lab.stoneshelter.services.StoneSemanticEvidenceProvider",
            "lab.stoneshelter.services.StoneSemanticEvidenceService",
            "lab.stoneshelter.services.StoneSemanticEvidenceValidationService",
            "lab.stoneshelter.services.StoneSemanticSearchProvider",
            "lab.stoneshelter.services.StoneSemanticSearchService",
            "lab.stoneshelter.services.StoneSemanticSearchSettings",
            "lab.stoneshelter.services.StoneSemanticSearchValidationService",
            "lab.stoneshelter.services.StoneStructuredModelService",
            "lab.stoneshelter.shared.StoneSemanticCandidateResponse",
            "lab.stoneshelter.shared.StoneSemanticEvidenceCandidateRequest",
            "lab.stoneshelter.shared.StoneSemanticEvidenceCandidateResponse",
            "lab.stoneshelter.shared.StoneSemanticEvidenceRequest",
            "lab.stoneshelter.shared.StoneSemanticEvidenceResponse",
            "lab.stoneshelter.shared.StoneSemanticEvidenceSourceRequest",
            "lab.stoneshelter.shared.StoneSemanticSearchDecision",
            "lab.stoneshelter.shared.StoneSemanticSearchRequest",
            "lab.stoneshelter.shared.StoneSemanticSearchResponse",
            "lab.stoneshelter.shared.StoneSemanticSearchStateResponse",
            "lab.stoneshelter.shared.StoneSemanticSourceResponse",
            "lab.stoneshelter.shared.StoneSemanticWishEvidenceResponse",
            "lab.stoneshelter.shared.StoneSemanticWishRequest",
            "lab.stoneshelter.shared.StoneSemanticWishResponse");
    private List<Class<?>> types() throws Exception {
        List<Class<?>> types = new ArrayList<>();
        for (String name : names) addType(Class.forName(name), types);
        return types;
    }
    private void addType(Class<?> type, List<Class<?>> types) {
        types.add(type); for (Class<?> nested : type.getDeclaredClasses()) addType(nested, types);
    }
    @Test void domainControllersServicesAndRepositoriesRespectResponsibilities() throws Exception {
        JavaClasses javaClasses = new ClassFileImporter().importClasses(types());
        noClasses().that().resideInAPackage("..services..").should().dependOnClassesThat().resideInAnyPackage(
                "..controllers..", "..handlers..", "org.springframework.http..", "org.springframework.web..", "jakarta.persistence..",
                "org.springframework.jdbc..", "org.springframework.data.jpa..").check(javaClasses);
        noClasses().that().resideInAPackage("..repositories..").should().dependOnClassesThat().resideInAnyPackage(
                "..shared..", "..services..", "..mappers..").check(javaClasses);
        noClasses().that().resideInAnyPackage("lab.stoneshelter.entities..", "lab.stoneshelter.criteria..", "lab.stoneshelter.enums..", "lab.stoneshelter.exceptions..")
                .should().dependOnClassesThat().resideInAnyPackage("..shared..", "..controllers..", "..handlers..", "org.springframework.http..", "org.springframework.web..").check(javaClasses);
        noClasses().that().resideInAPackage("..controllers..").should().dependOnClassesThat()
                .resideInAnyPackage("..entities..", "..repositories..", "..mappers..").check(javaClasses);
    }
    @Test void recordsStaySmallLargerCarriersHaveBeanAccessorsAndMappersUseTheirBaseContracts() throws Exception {
        for (Class<?> type : types()) {
            if (type.isRecord()) assertThat(type.getRecordComponents()).as(type.getName()).hasSizeLessThanOrEqualTo(4);
            if (type.getName().contains(".mappers.entities.")) assertThat(AbstractEntityMapper.class.isAssignableFrom(type)).isTrue();
            if (type.getName().contains(".mappers.dtos.")) assertThat(AbstractDtoMapper.class.isAssignableFrom(type)).isTrue();
            if (type.isAnnotationPresent(org.springframework.stereotype.Service.class)) assertThat(type.isAnnotationPresent(Transactional.class)).as(type.getName()).isTrue();
            if (!type.isRecord() && !type.isEnum() && (type.getPackageName().equals("lab.stoneshelter.entities")
                    || type.getPackageName().equals("lab.stoneshelter.shared"))) {
                assertThat(type.getDeclaredConstructor()).isNotNull();
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
                    String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
                    assertThat(type.getMethod("get" + suffix).getReturnType()).isEqualTo(field.getType());
                    assertThat(type.getMethod("set" + suffix, field.getType())).isNotNull();
                }
            }
        }
    }
}
