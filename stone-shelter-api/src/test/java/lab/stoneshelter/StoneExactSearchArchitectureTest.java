package lab.stoneshelter;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import lab.stoneshelter.entities.ModelTokenBudgetDayEntity;
import lab.stoneshelter.entities.StoneModelInvocationEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.entities.StoneExactSearchResultEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.shared.StoneExactSearchDecision;
import lab.stoneshelter.shared.StoneExactSearchFilters;
import lab.stoneshelter.shared.StoneAdmissionDateRange;
import lab.stoneshelter.shared.StoneExactSearchReference;
import lab.stoneshelter.repositories.ModelTokenBudgetRepository;
import lab.stoneshelter.repositories.OllamaChatRepository;
import lab.stoneshelter.repositories.OllamaEmbeddingRepository;
import lab.stoneshelter.repositories.StoneExactSearchRepository;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import lab.stoneshelter.services.OllamaExactSearchProvider;
import lab.stoneshelter.services.StoneExactSearchService;
import lab.stoneshelter.services.StoneExactSearchValidationService;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.services.StoneChatbotService;
import lab.stoneshelter.services.StoneChatAvailabilityService;
import lab.stoneshelter.mappers.entities.AbstractEntityMapper;
import lab.stoneshelter.mappers.entities.StoneExactSearchDecisionToStoneExactSearchCriteriaMapper;
import lab.stoneshelter.mappers.entities.StoneExactSearchResultEntityToStoneChatTurnEntityMapper;
import lab.stoneshelter.controllers.StoneChatbotController;
import lab.stoneshelter.exceptions.InvalidStoneExactSearchOutputException;
import lab.stoneshelter.exceptions.ModelCapacityExceededException;
import lab.stoneshelter.exceptions.ModelTokenBudgetExceededException;
import lab.stoneshelter.exceptions.ModelAdmissionDeferredException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

class StoneExactSearchArchitectureTest {
    private final JavaClasses javaClasses = new ClassFileImporter().importClasses(
            StoneExactSearchCriteria.class, ModelTokenReservationEntity.class, ModelTokenBudgetDayEntity.class,
            StoneModelInvocationEntity.class, StoneModelResponseEntity.class, StoneExactSearchResultEntity.class,
            StoneChatTurnEntity.class, StoneExactSearchDecision.class, StoneExactSearchFilters.class,
            StoneAdmissionDateRange.class, StoneExactSearchReference.class, ModelTokenBudgetRepository.class,
            OllamaChatRepository.class, OllamaEmbeddingRepository.class, StoneExactSearchRepository.class,
            ModelInvocationAdmissionService.class, ModelTokenBudgetTransactionService.class,
            OllamaExactSearchProvider.class, StoneExactSearchService.class, StoneExactSearchValidationService.class,
            StoneDocumentEmbeddingService.class, StoneChatbotService.class, StoneChatAvailabilityService.class,
            StoneExactSearchResultEntityToStoneChatTurnEntityMapper.class, StoneExactSearchDecisionToStoneExactSearchCriteriaMapper.class, StoneChatbotController.class,
            InvalidStoneExactSearchOutputException.class, ModelCapacityExceededException.class,
            ModelTokenBudgetExceededException.class, ModelAdmissionDeferredException.class);
    @Test void servicesRepositoriesAndDomainRespectTheirLayers() {
        noClasses().that().resideInAPackage("..services..").should().dependOnClassesThat()
                .resideInAnyPackage("..controllers..", "..handlers..", "org.springframework.http..", "org.springframework.web..",
                        "org.springframework.jdbc..", "jakarta.persistence..", "org.springframework.data.jpa..").check(javaClasses);
        noClasses().that().resideInAPackage("..repositories..").should().dependOnClassesThat()
                .resideInAnyPackage("..services..", "..shared..", "..mappers..").check(javaClasses);
        noClasses().that().resideInAnyPackage("lab.stoneshelter.entities..", "lab.stoneshelter.criteria..")
                .should().dependOnClassesThat().resideInAnyPackage("..shared..", "..controllers..", "..handlers..").check(javaClasses);
        noClasses().that().resideInAPackage("..controllers..").should().dependOnClassesThat()
                .resideInAnyPackage("..entities..", "..repositories..", "..mappers..").check(javaClasses);
    }
    @Test void applicationExceptionsRemainProtocolIndependentAndServicesOwnTransactions() {
        noClasses().that().resideInAPackage("..exceptions..").should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.http..", "org.springframework.web..").check(javaClasses);
        classes().that().resideInAPackage("..services..").should().beAnnotatedWith(Transactional.class).check(javaClasses);
    }
    @Test void largerDataObjectsHavePrivateFieldsAndBeanAccessorsAndRecordsStaySmall() throws Exception {
        for (Class<?> type : List.of(StoneExactSearchDecision.class, StoneExactSearchCriteria.class, StoneModelInvocationEntity.class,
                StoneExactSearchResultEntity.class, StoneChatTurnEntity.class)) {
            assertThat(type.isRecord()).isFalse(); assertThat(type.getDeclaredConstructor()).isNotNull();
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
                String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
                assertThat(type.getMethod("get" + suffix).getReturnType()).isEqualTo(field.getType());
                assertThat(type.getMethod("set" + suffix, field.getType())).isNotNull();
            }
        }
        for (Class<?> type : List.of(ModelTokenReservationEntity.class, ModelTokenBudgetDayEntity.class, StoneModelResponseEntity.class,
                StoneExactSearchFilters.class, StoneAdmissionDateRange.class, StoneExactSearchReference.class))
            assertThat(type.getRecordComponents()).hasSizeLessThanOrEqualTo(4);
    }
    @Test void completedTurnMappingRetainsEntityMapperNullAndReturnContracts() {
        var mapper = new StoneExactSearchResultEntityToStoneChatTurnEntityMapper();
        assertThat(new StoneExactSearchDecisionToStoneExactSearchCriteriaMapper().toEntity(null)).isNull();
        assertThat(mapper).isInstanceOf(AbstractEntityMapper.class); assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.getClass().getPackageName()).isEqualTo("lab.stoneshelter.mappers.entities");
    }
}
