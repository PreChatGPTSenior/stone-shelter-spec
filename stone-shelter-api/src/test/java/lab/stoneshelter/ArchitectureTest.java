package lab.stoneshelter;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.stereotype.Service;
import lab.stoneshelter.mappers.entities.AbstractEntityMapper;
import lab.stoneshelter.mappers.dtos.AbstractDtoMapper;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "lab.stoneshelter", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule recordsHaveAtMostFourFields = classes()
            .that().areAssignableTo(Record.class)
            .should(new ArchCondition<JavaClass>("have at most four instance fields") {
                @Override
                public void check(JavaClass type, ConditionEvents events) {
                    long fields = type.getFields().stream()
                            .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
                            .count();
                    events.add(new SimpleConditionEvent(type, fields <= 4,
                            type.getName() + " has " + fields + " instance fields"));
                }
            });

    @ArchTest
    static final ArchRule httpLayerDoesNotAccessEntitiesOrRepositories = noClasses()
            .that().resideInAnyPackage("lab.stoneshelter.controllers..", "lab.stoneshelter.handlers..")
            .should().dependOnClassesThat().resideInAnyPackage("lab.stoneshelter.entities..", "lab.stoneshelter.repositories..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule catalogControllerReturnsDtosDirectly = noClasses()
            .that().haveSimpleName("StoneCatalogController")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.http.ResponseEntity");

    @ArchTest
    static final ArchRule controllersDoNotInvokeMappers = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Mapper")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllersDoNotConstructModelsOrDtos = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().callConstructorWhere(new DescribedPredicate<JavaConstructorCall>(
                    "construct domain models, DTOs or persistence entities") {
                @Override
                public boolean test(JavaConstructorCall call) {
                    var packageName = call.getTargetOwner().getPackageName();
                    return packageName.startsWith("lab.stoneshelter.criteria")
                            || packageName.startsWith("lab.stoneshelter.shared")
                            || packageName.startsWith("lab.stoneshelter.entities");
                }
            })
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule entitiesStayInEntities = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage("lab.stoneshelter.entities..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule servicesAndCustomExceptionsDoNotDependOnProtocolErrors = noClasses()
            .that().resideInAnyPackage("lab.stoneshelter.services..", "lab.stoneshelter.exceptions..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.http..", "org.springframework.web..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule servicesDoNotDependOnHttpLayer = noClasses()
            .that().resideInAnyPackage("lab.stoneshelter.services..", "lab.stoneshelter.criteria..", "lab.stoneshelter.enums..")
            .should().dependOnClassesThat().resideInAnyPackage("lab.stoneshelter.controllers..", "lab.stoneshelter.handlers..")
            .allowEmptyShould(true);
    @ArchTest
    static final ArchRule modelsDoNotDependOnSharedDtos = noClasses()
            .that().resideInAnyPackage("lab.stoneshelter.criteria..", "lab.stoneshelter.enums..", "lab.stoneshelter.entities..")
            .should().dependOnClassesThat().resideInAPackage("lab.stoneshelter.shared..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sharedDtosDoNotDependOnHttpLayer = noClasses()
            .that().resideInAPackage("lab.stoneshelter.shared..")
            .should().dependOnClassesThat().resideInAnyPackage("lab.stoneshelter.controllers..", "lab.stoneshelter.handlers..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule servicesAndCriteriaDoNotDependOnJpaQueryApis = noClasses()
            .that().resideInAnyPackage("lab.stoneshelter.services..", "lab.stoneshelter.criteria..", "lab.stoneshelter.enums..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "org.springframework.data.jpa..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule entitiesAreOnlyUsedByEntitiesRepositoriesServicesAndMappers = noClasses()
            .that().resideOutsideOfPackages("lab.stoneshelter.entities..",
                    "lab.stoneshelter.repositories..", "lab.stoneshelter.mappers..")
            .and().areNotAnnotatedWith(Service.class)
            .should().dependOnClassesThat().areAnnotatedWith("jakarta.persistence.Entity")
            .allowEmptyShould(true);
    @ArchTest
    static final ArchRule entityMappersLiveInEntities = classes()
            .that().haveSimpleNameEndingWith("EntityMapper")
            .should().resideInAPackage("lab.stoneshelter.mappers.entities");

    @ArchTest
    static final ArchRule dtoMappersLiveInDtos = classes()
            .that().haveSimpleNameEndingWith("DtoMapper")
            .or().haveSimpleNameEndingWith("ResponseMapper")
            .should().resideInAPackage("lab.stoneshelter.mappers.dtos");

    @ArchTest
    static final ArchRule entityMappersShareNullPolicy = classes()
            .that().resideInAPackage("lab.stoneshelter.mappers.entities")
            .and().haveSimpleNameEndingWith("Mapper")
            .should().beAssignableTo(AbstractEntityMapper.class);

    @ArchTest
    static final ArchRule dtoMappersShareValidationPolicy = classes()
            .that().resideInAPackage("lab.stoneshelter.mappers.dtos")
            .and().haveSimpleNameEndingWith("Mapper")
            .should().beAssignableTo(AbstractDtoMapper.class);

    @ArchTest
    static final ArchRule servicesLiveInServices = classes()
            .that().areAnnotatedWith(Service.class)
            .should().resideInAPackage("lab.stoneshelter.services");

    @ArchTest
    static final ArchRule enumsLiveInEnums = classes()
            .that().areAssignableTo(Enum.class)
            .should().resideInAPackage("lab.stoneshelter.enums");

    @ArchTest
    static final ArchRule repositoriesLiveInRepositories = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage("lab.stoneshelter.repositories");

    @ArchTest
    static final ArchRule exceptionsLiveInExceptions = classes()
            .that().areAssignableTo(RuntimeException.class)
            .should().resideInAPackage("lab.stoneshelter.exceptions");

    @ArchTest
    static final ArchRule repositoriesDoNotInvokeMappers = noClasses()
            .that().haveSimpleNameEndingWith("Repository")
            .should().dependOnClassesThat().resideInAnyPackage("lab.stoneshelter.mappers..")
            .allowEmptyShould(true);
}
