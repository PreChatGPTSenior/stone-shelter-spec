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

class StoneRagAnswerArchitectureTest {
    private final List<String> names = List.of(
            "lab.stoneshelter.controllers.StoneChatbotController",
            "lab.stoneshelter.criteria.StoneRagAnswerCriteria",
            "lab.stoneshelter.criteria.StoneRagKnowledgeCriteria",
            "lab.stoneshelter.entities.StoneChatTurnEntity",
            "lab.stoneshelter.entities.StoneRagAnswerAssemblyEntity",
            "lab.stoneshelter.entities.StoneRagAnswerResultEntity",
            "lab.stoneshelter.entities.StoneRagCompletionEvidenceEntity",
            "lab.stoneshelter.entities.StoneRagExecutionEntity",
            "lab.stoneshelter.entities.StoneRagGeneratedAnswerEntity",
            "lab.stoneshelter.entities.StoneRagGroundingResultEntity",
            "lab.stoneshelter.entities.StoneRagKnowledgeResultEntity",
            "lab.stoneshelter.entities.StoneRagPreparedTurnEntity",
            "lab.stoneshelter.entities.StoneRagTurnStateEntity",
            "lab.stoneshelter.entities.StoneSemanticSearchExecutionEntity",
            "lab.stoneshelter.enums.StoneRagCompletionKind",
            "lab.stoneshelter.enums.StoneRagMode",
            "lab.stoneshelter.exceptions.InvalidStoneRagOutputException",
            "lab.stoneshelter.exceptions.StoneModelProviderUnavailableException",
            "lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException",
            "lab.stoneshelter.mappers.dtos.StoneRagAnswerCriteriaToStoneRagGenerationRequestMapper",
            "lab.stoneshelter.mappers.dtos.StoneRagAnswerResultEntityToStoneRagAnswerResponseMapper",
            "lab.stoneshelter.mappers.dtos.StoneRagDecisionToStoneSemanticSearchDecisionMapper",
            "lab.stoneshelter.mappers.dtos.StoneRagGeneratedAnswerEntityToStoneRagGeneratedAnswerResponseMapper",
            "lab.stoneshelter.mappers.dtos.StoneRagGenerationRequestToStoneRagGroundingRequestMapper",
            "lab.stoneshelter.mappers.dtos.StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper",
            "lab.stoneshelter.mappers.dtos.StoneRagKnowledgeCriteriaToStoneRagKnowledgeRequestMapper",
            "lab.stoneshelter.mappers.dtos.StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper",
            "lab.stoneshelter.mappers.entities.StoneRagAnswerAssemblyEntityToStoneChatTurnEntityMapper",
            "lab.stoneshelter.mappers.entities.StoneRagDecisionToStoneRagAnswerCriteriaMapper",
            "lab.stoneshelter.repositories.StoneExactSearchRepository",
            "lab.stoneshelter.repositories.StoneRagKnowledgeRepository",
            "lab.stoneshelter.services.OllamaRagAnswerProvider",
            "lab.stoneshelter.services.OllamaRagGroundingProvider",
            "lab.stoneshelter.services.OllamaRagRoutingProvider",
            "lab.stoneshelter.services.StoneChatAvailabilityService",
            "lab.stoneshelter.services.StoneChatReferenceService",
            "lab.stoneshelter.services.StoneChatbotService",
            "lab.stoneshelter.services.StoneExactSearchService",
            "lab.stoneshelter.services.StoneRagAnswerProvider",
            "lab.stoneshelter.services.StoneRagAnswerService",
            "lab.stoneshelter.services.StoneRagAnswerValidationService",
            "lab.stoneshelter.services.StoneRagConversationService",
            "lab.stoneshelter.services.StoneRagDecisionValidationService",
            "lab.stoneshelter.services.StoneRagGroundingProvider",
            "lab.stoneshelter.services.StoneRagGroundingService",
            "lab.stoneshelter.services.StoneRagKnowledgeService",
            "lab.stoneshelter.services.StoneRagRoutingProvider",
            "lab.stoneshelter.services.StoneRagSettings",
            "lab.stoneshelter.services.StoneSemanticCandidateService",
            "lab.stoneshelter.services.StoneSemanticSearchService",
            "lab.stoneshelter.services.StoneStructuredModelService",
            "lab.stoneshelter.shared.StoneRagAnswerResponse",
            "lab.stoneshelter.shared.StoneRagCandidateRequest",
            "lab.stoneshelter.shared.StoneRagDecision",
            "lab.stoneshelter.shared.StoneRagGeneratedAnswerResponse",
            "lab.stoneshelter.shared.StoneRagGenerationRequest",
            "lab.stoneshelter.shared.StoneRagGroundingRequest",
            "lab.stoneshelter.shared.StoneRagGroundingResponse",
            "lab.stoneshelter.shared.StoneRagKnowledgeRequest",
            "lab.stoneshelter.shared.StoneRagKnowledgeResponse",
            "lab.stoneshelter.shared.StoneRagSourceRequest",
            "lab.stoneshelter.shared.StoneRagStoneExplanationResponse");
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
            if (type.getName().contains(".mappers.entities.") && type.getSimpleName().endsWith("Mapper")) assertThat(AbstractEntityMapper.class.isAssignableFrom(type)).isTrue();
            if (type.getName().contains(".mappers.dtos.") && type.getSimpleName().endsWith("Mapper")) assertThat(AbstractDtoMapper.class.isAssignableFrom(type)).isTrue();
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
