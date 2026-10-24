package lab.stoneshelter;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import lab.stoneshelter.controllers.StoneCardReindexController;
import lab.stoneshelter.entities.StoneCardReindexJobEntity;
import lab.stoneshelter.entities.StoneCardReindexItemEntity;
import lab.stoneshelter.enums.StoneCardReindexTarget;
import lab.stoneshelter.enums.StoneCardReindexStatus;
import lab.stoneshelter.exceptions.InvalidStoneCardReindexRequestException;
import lab.stoneshelter.exceptions.StoneCardReindexConflictException;
import lab.stoneshelter.exceptions.StoneCardReindexNotFoundException;
import lab.stoneshelter.mappers.dtos.AbstractDtoMapper;
import lab.stoneshelter.mappers.dtos.StoneCardReindexJobEntityToStoneCardReindexStartResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneCardReindexJobEntityToStoneCardReindexStatusResponseMapper;
import lab.stoneshelter.repositories.StoneCardReindexControlRepository;
import lab.stoneshelter.repositories.StoneCardReindexJobRepository;
import lab.stoneshelter.repositories.StoneCardReindexItemRepository;
import lab.stoneshelter.services.StoneCardReindexRunnerService;
import lab.stoneshelter.services.StoneCardReindexSettings;
import lab.stoneshelter.services.StoneCardReindexService;
import lab.stoneshelter.services.StoneCardReindexTransactionService;
import lab.stoneshelter.shared.StoneCardReindexStartRequest;
import lab.stoneshelter.shared.StoneCardReindexStartResponse;
import lab.stoneshelter.shared.StoneCardReindexStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class StoneCardReindexArchitectureTest {
    private final JavaClasses javaClasses = new ClassFileImporter().importClasses(StoneCardReindexController.class,
            StoneCardReindexJobEntity.class, StoneCardReindexItemEntity.class, StoneCardReindexTarget.class,
            StoneCardReindexStatus.class, InvalidStoneCardReindexRequestException.class, StoneCardReindexConflictException.class,
            StoneCardReindexNotFoundException.class, StoneCardReindexJobEntityToStoneCardReindexStartResponseMapper.class,
            StoneCardReindexJobEntityToStoneCardReindexStatusResponseMapper.class, StoneCardReindexControlRepository.class,
            StoneCardReindexJobRepository.class, StoneCardReindexItemRepository.class, StoneCardReindexRunnerService.class,
            StoneCardReindexSettings.class, StoneCardReindexService.class, StoneCardReindexTransactionService.class,
            StoneCardReindexStartRequest.class, StoneCardReindexStartResponse.class, StoneCardReindexStatusResponse.class);

    @Test
    void layersAndProtocolBoundariesRemainIntact() {
        noClasses().that().resideInAPackage("..controllers..").should().dependOnClassesThat()
                .resideInAnyPackage("..repositories..", "lab.stoneshelter.entities..", "..mappers..").check(javaClasses);
        noClasses().that().resideInAPackage("..services..").should().dependOnClassesThat()
                .resideInAnyPackage("..controllers..", "..handlers..", "org.springframework.http..", "org.springframework.web..",
                        "org.springframework.data.jpa..", "jakarta.persistence..", "org.springframework.jdbc..").check(javaClasses);
        noClasses().that().resideInAPackage("..repositories..").should().dependOnClassesThat()
                .resideInAnyPackage("..shared..", "..mappers..", "..services..").check(javaClasses);
        noClasses().that().resideInAnyPackage("lab.stoneshelter.entities..", "..exceptions..", "..enums..")
                .should().dependOnClassesThat().resideInAnyPackage("..shared..", "org.springframework.http..", "org.springframework.web..")
                .check(javaClasses);
        classes().that().resideInAPackage("..services..").should().beAnnotatedWith(Transactional.class).check(javaClasses);
    }

    @Test
    void reindexPreparationNeverInvokesModelOrKafkaClients() {
        noClasses().that().resideInAPackage("..services..").should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.ai..", "org.apache.kafka..", "org.springframework.kafka..").check(javaClasses);
        noClasses().that().resideInAPackage("..services..").should().dependOnClassesThat()
                .haveNameMatching(".*(Ollama|EmbeddingService|ConsumerService|DispatcherService).*").check(javaClasses);
    }

    @Test
    void largerDomainAndResponseTypesHavePrivateFieldsAndBeanAccessors() throws Exception {
        for (Class<?> type : java.util.List.of(StoneCardReindexJobEntity.class, StoneCardReindexItemEntity.class, StoneCardReindexStatusResponse.class)) {
            assertThat(type.isRecord()).isFalse();
            assertThat(type.getDeclaredConstructor()).isNotNull();
            for (Field field : type.getDeclaredFields()) {
                assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
                String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
                assertThat(type.getMethod("get" + suffix).getReturnType()).isEqualTo(field.getType());
                assertThat(type.getMethod("set" + suffix, field.getType())).isNotNull();
            }
        }
    }

    @Test
    void controllerReturnsReadyDtosAndMappersInheritValidation() throws Exception {
        assertThat(StoneCardReindexController.class.getMethod("start", StoneCardReindexStartRequest.class).getReturnType())
                .isEqualTo(StoneCardReindexStartResponse.class);
        assertThat(StoneCardReindexJobEntityToStoneCardReindexStartResponseMapper.class).isAssignableTo(AbstractDtoMapper.class);
        assertThat(StoneCardReindexJobEntityToStoneCardReindexStatusResponseMapper.class).isAssignableTo(AbstractDtoMapper.class);
        assertThat(StoneCardReindexStatus.class.getEnumConstants()).containsExactly(
                StoneCardReindexStatus.PENDING, StoneCardReindexStatus.RUNNING, StoneCardReindexStatus.ENQUEUED);
    }
}
