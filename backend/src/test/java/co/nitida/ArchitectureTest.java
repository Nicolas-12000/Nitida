package co.nitida;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Module boundaries (Spring Modulith) and layering rules (ArchUnit). A violation breaks the build. */
@AnalyzeClasses(packages = "co.nitida", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    private static final ApplicationModules MODULES = ApplicationModules.of(NitidaApplication.class);

    @Test
    void modulesRespectTheirBoundariesAndHaveNoCycles() {
        MODULES.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES, Documenter.Options.defaults().withOutputFolder("../docs/modulos")).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }

    @ArchTest
    static final ArchRule noRestControllers = noClasses().should()
            .beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .orShould().beAnnotatedWith(org.springframework.stereotype.Controller.class)
            .because("the HTTP layer is RouterFunction + a thin handler (section 7.3)");

    @ArchTest
    static final ArchRule onlyUseCasesAreTransactional = classes().that().areAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .or().containAnyMethodsThat(com.tngtech.archunit.base.DescribedPredicate.describe("are @Transactional",
                    m -> m.isAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)))
            .should().resideInAPackage("..application..")
            .because("only orchestrators open transactions");

    @ArchTest
    static final ArchRule handlersHoldNoTransactions = noClasses().that().resideInAPackage("..web..")
            .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class);

    @ArchTest
    static final ArchRule domainDoesNotKnowTheOuterLayers = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..web..", "..application..", "..infrastructure..")
            .because("dependencies point one way: web -> application -> domain");

    @ArchTest
    static final ArchRule handlersDoNotTouchRepositories = noClasses().that().resideInAPackage("..web..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("handlers go through use cases or module APIs");

    @ArchTest
    static final ArchRule useCasesAreNamedAsSuch = classes().that().resideInAPackage("..application..").and().areAnnotatedWith(org.springframework.stereotype.Service.class)
            .and().haveSimpleNameEndingWith("UseCase").or().haveSimpleNameEndingWith("UseCases").or().haveSimpleNameEndingWith("Impl")
            .should().bePublic().orShould().haveSimpleNameEndingWith("Impl");
}
