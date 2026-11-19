package co.nitida.security;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.Test;

/**
 * OWASP-driven architecture rules (A05 injection, A09 logging, A04 crypto). Every rule is also run against the
 * deliberate violations in co.nitida.archfixture, so a rule that stops biting breaks the build.
 */
@AnalyzeClasses(packages = "co.nitida", importOptions = ImportOption.DoNotIncludeTests.class)
class SecurityArchitectureTest {

    @ArchTest
    static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS; // A09: logs only through SLF4J

    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /** §13.3: SecureRandom for every token or salt. SecureRandom extends Random, hence the exact-name match. */
    @ArchTest
    static final ArchRule onlySecureRandom = noClasses().should().dependOnClassesThat().haveFullyQualifiedName("java.util.Random")
            .because("tokens and salts must come from SecureRandom (A04)");

    @ArchTest
    static final ArchRule handlersHaveNoDirectDatabaseAccess = noClasses().that().resideInAPackage("..web..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManager")
            .because("handlers parse HTTP and call a use case; SQL lives behind it (A05)");

    @ArchTest
    static final ArchRule noStringBuiltJpaQueries = noClasses().should().callMethod(jakarta.persistence.EntityManager.class, "createNativeQuery", String.class)
            .because("native queries are written in repositories with bind parameters (A05)");

    @ArchTest
    static final ArchRule eventListenersLiveInApplication = methods().that().areAnnotatedWith(org.springframework.modulith.events.ApplicationModuleListener.class)
            .should().beDeclaredInClassesThat().resideInAPackage("..application..")
            .because("listeners are orchestration: after commit, own transaction, idempotent");

    @ArchTest
    static final ArchRule domainEventsAreRecords = classes().that().haveSimpleNameEndingWith("Registered")
            .or().haveSimpleNameEndingWith("Delivered").or().haveSimpleNameEndingWith("Cancelled").or().haveSimpleNameEndingWith("Rejected")
            .or().haveSimpleNameEndingWith("Voided").or().haveSimpleNameEndingWith("Transferred").or().haveSimpleNameEndingWith("Recorded")
            .should().beRecords().because("events are immutable facts");

    // ---- the rules bite: each one fails on its deliberate violation ----

    private static final com.tngtech.archunit.core.domain.JavaClasses FIXTURES = new ClassFileImporter().importPackages("co.nitida.archfixture");

    @Test
    void standardStreamsRuleBites() {
        assertThatThrownBy(() -> noStandardStreams.check(FIXTURES)).isInstanceOf(AssertionError.class).hasMessageContaining("BadPrinter");
    }

    @Test
    void secureRandomRuleBites() {
        assertThatThrownBy(() -> onlySecureRandom.check(FIXTURES)).isInstanceOf(AssertionError.class).hasMessageContaining("BadPrinter");
    }

    @Test
    void handlerDatabaseRuleBites() {
        assertThatThrownBy(() -> handlersHaveNoDirectDatabaseAccess.check(FIXTURES)).isInstanceOf(AssertionError.class).hasMessageContaining("BadHandler");
    }

    @Test
    void listenerLocationRuleBites() {
        assertThatThrownBy(() -> eventListenersLiveInApplication.check(FIXTURES)).isInstanceOf(AssertionError.class).hasMessageContaining("BadListener");
    }

    @Test
    void eventRecordRuleBites() {
        assertThatThrownBy(() -> domainEventsAreRecords.check(FIXTURES)).isInstanceOf(AssertionError.class).hasMessageContaining("ThingRegistered");
    }
}
