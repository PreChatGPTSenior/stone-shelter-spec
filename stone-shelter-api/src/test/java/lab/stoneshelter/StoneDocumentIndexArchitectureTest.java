package lab.stoneshelter;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "lab.stoneshelter", importOptions = ImportOption.DoNotIncludeTests.class)
class StoneDocumentIndexArchitectureTest {
    @ArchTest
    static final ArchRule indexControllerDoesNotAccessPersistenceOrMappers = noClasses()
            .that().haveSimpleName("StoneEncyclopediaController")
            .should().dependOnClassesThat().resideInAnyPackage("lab.stoneshelter.entities..",
                    "lab.stoneshelter.repositories..", "lab.stoneshelter.mappers..");

    @ArchTest
    static final ArchRule documentServicesRemainProtocolIndependent = noClasses()
            .that().resideInAPackage("lab.stoneshelter.services")
            .and().haveSimpleNameStartingWith("StoneDocument")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.http..",
                    "org.springframework.web..", "jakarta.servlet..", "jakarta.persistence..", "org.springframework.data.jpa..");
}
