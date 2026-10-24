package lab.stoneshelter;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import lab.stoneshelter.controllers.StoneChatbotController;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class StoneChatbotArchitectureTest {
    @Test
    void preservesDtoConstructionRestrictionsForOtherControllers() {
        ArchitectureTest.controllersDoNotConstructModelsOrDtos.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("lab.stoneshelter"));
    }

    @Test
    void chatControllerDoesNotDependOnMappingOrPersistence() {
        noClasses().that().haveFullyQualifiedName(StoneChatbotController.class.getName())
                .should().dependOnClassesThat().resideInAnyPackage("lab.stoneshelter.mappers..", "lab.stoneshelter.entities..",
                        "lab.stoneshelter.repositories..", "lab.stoneshelter.criteria..")
                .check(new ClassFileImporter().importClasses(StoneChatbotController.class));
    }
}
