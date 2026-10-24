package lab.stoneshelter;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import lab.stoneshelter.entities.OutboxDeadLetterEntity;
import lab.stoneshelter.entities.StoneCardIndexAttemptEntity;
import lab.stoneshelter.entities.StoneCardIndexStateEntity;
import lab.stoneshelter.mappers.entities.AbstractEntityMapper;
import lab.stoneshelter.mappers.entities.StoneCardSaveRequestToStoneCardEmbeddingEntityMapper;
import lab.stoneshelter.repositories.StoneCardEmbeddingRepository;
import lab.stoneshelter.repositories.StoneCardIndexAttemptRepository;
import lab.stoneshelter.repositories.StoneCardIndexStateRepository;
import lab.stoneshelter.services.StoneCardEmbeddingService;
import lab.stoneshelter.services.StoneIndexAttemptTransactionService;
import lab.stoneshelter.services.StoneIndexConsumerService;
import lab.stoneshelter.services.StoneIndexProcessingService;
import lab.stoneshelter.services.StoneIndexProcessingTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class StoneIndexProcessingArchitectureTest {
    private final JavaClasses javaClasses = new ClassFileImporter().importClasses(StoneCardIndexStateEntity.class,
            StoneCardIndexAttemptEntity.class, OutboxDeadLetterEntity.class, StoneCardIndexStateRepository.class,
            StoneCardIndexAttemptRepository.class, StoneCardEmbeddingRepository.class, StoneIndexConsumerService.class,
            StoneIndexProcessingService.class, StoneIndexProcessingTransactionService.class,
            StoneIndexAttemptTransactionService.class, StoneCardEmbeddingService.class,
            StoneCardSaveRequestToStoneCardEmbeddingEntityMapper.class);

    @Test
    void applicationPersistenceAndDomainKeepTheirResponsibilities() {
        noClasses().that().resideInAPackage("..services..").should().dependOnClassesThat()
                .resideInAnyPackage("..controllers..", "..handlers..", "org.springframework.http..",
                        "org.springframework.web..", "org.springframework.jdbc..", "jakarta.persistence..", "org.springframework.data.jpa..")
                .check(javaClasses);
        noClasses().that().resideInAPackage("..repositories..").should().dependOnClassesThat()
                .resideInAnyPackage("..services..", "..shared..", "..mappers..").check(javaClasses);
        noClasses().that().resideInAPackage("lab.stoneshelter.entities..").should().dependOnClassesThat()
                .resideInAnyPackage("..services..", "..shared..", "..controllers..", "..handlers..").check(javaClasses);
        classes().that().resideInAPackage("..services..").should().beAnnotatedWith(Transactional.class).check(javaClasses);
    }

    @Test
    void attemptStateUsesSourceCoordinatesInsteadOfAnEventUuidRegistry() {
        assertThat(StoneCardIndexStateEntity.class.getRecordComponents()).hasSize(3);
        assertThat(StoneCardIndexAttemptEntity.class.getDeclaredFields()).extracting(Field::getName)
                .contains("sourceTopic", "sourcePartition", "sourceOffset", "originalRecord", "attemptCount", "deadLetterPending")
                .doesNotContain("eventId", "processedUuid");
    }

    @Test
    void largeAttemptStateUsesPrivateFieldsAndBeanAccessors() throws Exception {
        assertThat(StoneCardIndexAttemptEntity.class.isRecord()).isFalse();
        assertThat(StoneCardIndexAttemptEntity.class.getDeclaredConstructor()).isNotNull();
        for (Field field : StoneCardIndexAttemptEntity.class.getDeclaredFields()) {
            assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
            String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
            assertThat(StoneCardIndexAttemptEntity.class.getMethod("get" + suffix).getReturnType()).isEqualTo(field.getType());
            assertThat(StoneCardIndexAttemptEntity.class.getMethod("set" + suffix, field.getType())).isNotNull();
        }
    }

    @Test
    void projectionPreparationRetainsTheSharedEntityMapperContract() {
        assertThat(StoneCardSaveRequestToStoneCardEmbeddingEntityMapper.class).isAssignableTo(AbstractEntityMapper.class);
        assertThat(StoneCardSaveRequestToStoneCardEmbeddingEntityMapper.class.getPackageName()).isEqualTo("lab.stoneshelter.mappers.entities");
        noClasses().should().dependOnClassesThat().resideInAnyPackage("..controllers..", "..handlers..").check(javaClasses);
    }
}
