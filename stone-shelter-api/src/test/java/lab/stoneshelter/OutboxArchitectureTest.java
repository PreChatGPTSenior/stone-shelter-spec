package lab.stoneshelter;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.entities.OutboxDeadLetterEntity;
import lab.stoneshelter.enums.OutboxDeliveryState;
import lab.stoneshelter.exceptions.OutboxIdentityConflictException;
import lab.stoneshelter.exceptions.OutboxValidationException;
import lab.stoneshelter.exceptions.OutboxWriteException;
import lab.stoneshelter.mappers.entities.AbstractEntityMapper;
import lab.stoneshelter.mappers.entities.OutboxWriteRequestToOutboxEventEntityMapper;
import lab.stoneshelter.repositories.OutboxEventRepository;
import lab.stoneshelter.services.OutboxPayloadValidationService;
import lab.stoneshelter.services.OutboxService;
import lab.stoneshelter.services.OutboxDispatcherService;
import lab.stoneshelter.services.OutboxDeliverySettings;
import lab.stoneshelter.services.OutboxDeliveryTransactionService;
import lab.stoneshelter.services.OutboxEventRoutingService;
import lab.stoneshelter.services.OutboxKafkaTopicService;
import lab.stoneshelter.repositories.OutboxKafkaRepository;
import lab.stoneshelter.mappers.entities.OutboxEventEntityToOutboxDeadLetterEntityMapper;
import lab.stoneshelter.shared.OutboxWriteRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class OutboxArchitectureTest {
    private final JavaClasses javaClasses = new ClassFileImporter().importClasses(
            OutboxWriteRequest.class, OutboxEventEntity.class, OutboxDeliveryState.class,
            OutboxWriteRequestToOutboxEventEntityMapper.class, OutboxWriteException.class,
            OutboxValidationException.class, OutboxIdentityConflictException.class,
            OutboxPayloadValidationService.class, OutboxService.class, OutboxEventRepository.class,
            OutboxDispatcherService.class, OutboxDeliverySettings.class, OutboxDeliveryTransactionService.class,
            OutboxEventRoutingService.class, OutboxKafkaTopicService.class, OutboxKafkaRepository.class,
            OutboxDeadLetterEntity.class, OutboxEventEntityToOutboxDeadLetterEntityMapper.class);

    @Test
    void applicationServicesAndPersistenceKeepTheirLayerBoundaries() {
        noClasses().that().resideInAPackage("..services..")
                .should().dependOnClassesThat().resideInAnyPackage("..controllers..", "..handlers..",
                        "org.springframework.http..", "org.springframework.web..", "jakarta.persistence..",
                        "org.springframework.data.jpa..", "org.springframework.jdbc..")
                .check(javaClasses);
        noClasses().that().resideInAPackage("..repositories..")
                .should().dependOnClassesThat().resideInAnyPackage("..shared..", "..mappers..", "..services..")
                .check(javaClasses);
        noClasses().that().resideInAnyPackage("lab.stoneshelter.entities..", "lab.stoneshelter.exceptions..")
                .should().dependOnClassesThat().resideInAnyPackage("..shared..", "..controllers..", "..handlers..",
                        "org.springframework.http..", "org.springframework.web..")
                .check(javaClasses);
    }

    @Test
    void requestAndEntityShapesFollowTheConstitution() throws Exception {
        assertThat(OutboxWriteRequest.class.getPackageName()).isEqualTo("lab.stoneshelter.shared");
        assertThat(OutboxWriteRequest.class.isRecord()).isTrue();
        assertThat(Arrays.stream(OutboxWriteRequest.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("eventId", "eventType", "schemaVersion", "payload");
        assertThat(OutboxWriteRequest.class.getRecordComponents()).hasSizeLessThanOrEqualTo(4);
        verifyEntityShape(OutboxDeadLetterEntity.class);
        verifyEntityShape(OutboxEventEntity.class);
    }

    private void verifyEntityShape(Class<?> entityClass) throws Exception {
        assertThat(entityClass.isRecord()).isFalse();
        assertThat(Modifier.isPublic(entityClass.getDeclaredConstructor().getModifiers())).isTrue();
        for (Field field : entityClass.getDeclaredFields()) {
            assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
            String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
            assertThat(entityClass.getMethod("get" + suffix).getReturnType()).isEqualTo(field.getType());
            assertThat(entityClass.getMethod("set" + suffix, field.getType()).getReturnType()).isEqualTo(void.class);
        }
    }

    @Test
    void mappingAndObjectTransformationApisReturnTheirTargets() throws Exception {
        assertThat(OutboxEventEntityToOutboxDeadLetterEntityMapper.class).isAssignableTo(AbstractEntityMapper.class);
        assertThat(OutboxWriteRequestToOutboxEventEntityMapper.class).isAssignableTo(AbstractEntityMapper.class);
        assertThat(OutboxWriteRequestToOutboxEventEntityMapper.class.getPackageName())
                .isEqualTo("lab.stoneshelter.mappers.entities");
        assertThat(OutboxWriteRequestToOutboxEventEntityMapper.class.getDeclaredMethod("populateEntity",
                OutboxWriteRequest.class, OutboxEventEntity.class).getReturnType()).isEqualTo(OutboxEventEntity.class);
        assertThat(OutboxPayloadValidationService.class.getMethod("validate", OutboxWriteRequest.class).getReturnType())
                .isEqualTo(OutboxWriteRequest.class);
        assertThat(OutboxService.class.getMethod("save", OutboxWriteRequest.class).getReturnType()).isEqualTo(OutboxEventEntity.class);
        assertThat(OutboxEventRepository.class.getMethod("save", OutboxEventEntity.class).getReturnType()).isEqualTo(OutboxEventEntity.class);
    }

    @Test
    void servicesDeclareTransactionsAtTheClassBoundary() {
        classes().that().resideInAPackage("..services..")
                .should().beAnnotatedWith(Transactional.class).check(javaClasses);
    }

    @Test
    void deliveryDoesNotIntroduceModelsStorageOrHttpManagement() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("org.springframework.ai..", "org.springframework.data.redis..", "io.minio..", "..controllers..", "..handlers..")
                .check(javaClasses);
    }
}
