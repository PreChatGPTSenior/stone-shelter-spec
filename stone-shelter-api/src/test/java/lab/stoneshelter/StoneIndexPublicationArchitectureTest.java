package lab.stoneshelter;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import lab.stoneshelter.entities.StoneIndexSourceVersionEntity;
import lab.stoneshelter.enums.StoneIndexEventType;
import lab.stoneshelter.mappers.dtos.AbstractDtoMapper;
import lab.stoneshelter.mappers.dtos.StoneEntityToStoneIndexUpdatePayloadMapper;
import lab.stoneshelter.repositories.StoneIndexSourceVersionRepository;
import lab.stoneshelter.services.StoneCreationService;
import lab.stoneshelter.services.StoneIndexEventService;
import lab.stoneshelter.services.StoneIndexKafkaTopicService;
import lab.stoneshelter.services.StoneService;
import lab.stoneshelter.shared.StoneIndexDeletePayload;
import lab.stoneshelter.shared.StoneIndexUpdatePayload;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class StoneIndexPublicationArchitectureTest {
    private final JavaClasses javaClasses = new ClassFileImporter().importClasses(StoneIndexEventService.class,
            StoneIndexKafkaTopicService.class, StoneCreationService.class, StoneService.class,
            StoneIndexSourceVersionEntity.class, StoneIndexSourceVersionRepository.class, StoneIndexEventType.class,
            StoneIndexUpdatePayload.class, StoneIndexDeletePayload.class, StoneEntityToStoneIndexUpdatePayloadMapper.class);

    @Test
    void applicationAndPersistenceRespectLayerBoundaries() {
        noClasses().that().resideInAPackage("..services..")
                .should().dependOnClassesThat().resideInAnyPackage("..controllers..", "..handlers..",
                        "org.springframework.http..", "org.springframework.web..", "jakarta.persistence..",
                        "org.springframework.data.jpa..", "org.springframework.jdbc..").check(javaClasses);
        noClasses().that().resideInAPackage("..repositories..")
                .should().dependOnClassesThat().resideInAnyPackage("..shared..", "..services..", "..mappers..").check(javaClasses);
        noClasses().that().resideInAnyPackage("lab.stoneshelter.entities..", "..enums..")
                .should().dependOnClassesThat().resideInAPackage("..shared..").check(javaClasses);
        classes().that().resideInAPackage("..services..").should().beAnnotatedWith(Transactional.class).check(javaClasses);
    }

    @Test
    void catalogMutationAndEventServicesNeverGenerateEmbeddingsOrPublishToKafka() {
        noClasses().that().haveNameMatching("lab[.]stoneshelter[.]services[.]Stone(Service|CreationService|IndexEventService)")
                .should().dependOnClassesThat().resideInAnyPackage("org.apache.kafka..", "org.springframework.kafka..", "org.springframework.ai..")
                .check(javaClasses);
        noClasses().that().haveNameMatching("lab[.]stoneshelter[.]services[.]Stone(Service|CreationService|IndexEventService)")
                .should().dependOnClassesThat().haveNameMatching(".*(Embedding|Dispatcher|Ollama).*").check(javaClasses);
    }

    @Test
    void sixFieldPayloadUsesClassAndMapperFollowsDtoContract() throws Exception {
        assertThat(StoneIndexUpdatePayload.class.isRecord()).isFalse();
        assertThat(StoneIndexUpdatePayload.class.getDeclaredFields()).hasSize(6);
        assertThat(StoneIndexUpdatePayload.class.getDeclaredConstructor()).isNotNull();
        assertThat(StoneIndexDeletePayload.class.getRecordComponents()).hasSize(2);
        assertThat(StoneEntityToStoneIndexUpdatePayloadMapper.class).isAssignableTo(AbstractDtoMapper.class);
        assertThat(StoneEntityToStoneIndexUpdatePayloadMapper.class.getPackageName()).isEqualTo("lab.stoneshelter.mappers.dtos");
    }
}
