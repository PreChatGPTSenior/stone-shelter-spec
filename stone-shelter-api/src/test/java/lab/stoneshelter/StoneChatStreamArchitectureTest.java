package lab.stoneshelter;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import lab.stoneshelter.mappers.dtos.AbstractDtoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class StoneChatStreamArchitectureTest {
    private List<Class<?>> types() throws Exception {
        List<Class<?>> result = new ArrayList<>();
        for (String name : List.of("controllers.StoneChatStreamController", "controllers.StoneChatHistoryController", "controllers.StoneChatStreamWebConfiguration",
                "entities.StoneChatAdmittedTurnEntity", "entities.StoneChatStreamEntity", "entities.StoneChatStreamEventEntity", "enums.StoneChatStreamEventType",
                "exceptions.StoneChatStreamCapacityExceededException", "services.StoneChatStreamAdmissionService", "services.StoneChatStreamService",
                "services.StoneChatbotService", "services.StoneChatStreamSettings", "handlers.StoneChatStreamReturnValueHandler", "handlers.StoneChatProblemDetailFactory",
                "mappers.dtos.StoneChatStreamEntityToStoneChatStreamResponseMapper", "mappers.dtos.StoneChatStreamEventEntityToStoneChatStreamEventResponseMapper",
                "mappers.dtos.StoneChatStreamErrorContextToStoneChatStreamErrorResponseMapper", "shared.StoneChatStreamResponse", "shared.StoneChatStreamEventResponse",
                "shared.StoneChatStreamStartedResponse", "shared.StoneChatStreamTextDeltaResponse", "shared.StoneChatStreamCompletedResponse", "shared.StoneChatStreamErrorResponse", "shared.StoneChatStreamErrorContext")) add(Class.forName("lab.stoneshelter." + name), result);
        return result;
    }
    private void add(Class<?> type, List<Class<?>> result) { result.add(type); for (Class<?> nested : type.getDeclaredClasses()) add(nested, result); }
    @Test void streamServicesAndDomainStayIndependentOfHttpAndControllersStayThin() throws Exception {
        JavaClasses classes = new ClassFileImporter().importClasses(types());
        noClasses().that().resideInAPackage("..services..").should().dependOnClassesThat().resideInAnyPackage("..controllers..", "..handlers..", "org.springframework.http..", "org.springframework.web..", "jakarta.servlet..", "org.springframework.data.jpa..", "jakarta.persistence..").check(classes);
        noClasses().that().resideInAnyPackage("..entities..", "..enums..", "..exceptions..").should().dependOnClassesThat().resideInAnyPackage("..shared..", "..controllers..", "..handlers..", "org.springframework.http..", "org.springframework.web..", "jakarta.servlet..").check(classes);
        noClasses().that().resideInAPackage("..controllers..").should().dependOnClassesThat().resideInAnyPackage("..entities..", "..repositories..", "..mappers..").check(classes);
    }
    @Test void carriersMappersAndTransactionalServicesFollowTheEstablishedTypeContracts() throws Exception {
        for (Class<?> type : types()) {
            if (type.isRecord()) assertThat(type.getRecordComponents()).as(type.getName()).hasSizeLessThanOrEqualTo(4);
            if (type.getPackageName().equals("lab.stoneshelter.mappers.dtos")) assertThat(AbstractDtoMapper.class.isAssignableFrom(type)).isTrue();
            if (type.isAnnotationPresent(org.springframework.stereotype.Service.class)) assertThat(type.isAnnotationPresent(Transactional.class)).isTrue();
            if (!type.isRecord() && !type.isEnum() && (type.getPackageName().equals("lab.stoneshelter.entities") || type.getPackageName().equals("lab.stoneshelter.shared"))) {
                assertThat(type.getDeclaredConstructor()).isNotNull();
                for (Field field : type.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
                    String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
                    assertThat(type.getMethod("get" + suffix).getReturnType()).isEqualTo(field.getType()); assertThat(type.getMethod("set" + suffix, field.getType())).isNotNull();
                }
            }
        }
    }
}
