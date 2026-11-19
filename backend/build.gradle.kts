plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	jacoco
}

group = "co.nitida"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "2.1.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.bouncycastle:bcprov-jdk18on:1.87")
	implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
	implementation("org.springframework.modulith:spring-modulith-observability-api")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.modulith:spring-modulith-starter-jpa")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
	runtimeOnly("org.springframework.modulith:spring-modulith-observability-core")
	runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("net.jqwik:jqwik:1.9.2")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
	testRuntimeOnly("com.h2database:h2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

tasks.bootJar {
	archiveFileName = "app.jar" // stable name for the Dockerfile
}

tasks.withType<Test> {
	useJUnitPlatform()
	// the application and its tests are Java 21, but the tests spawn nothing external
	testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

jacoco {
	toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true // Sonar reads the XML
		html.required = true
	}
}

tasks.test {
	finalizedBy(tasks.jacocoTestReport)
}

// Coverage floor; raise it as the suite grows (never lower it to make a build pass).
tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.jacocoTestReport)
	violationRules {
		rule {
			limit {
				counter = "LINE"
				value = "COVEREDRATIO"
				minimum = "0.70".toBigDecimal()
			}
		}
	}
}

tasks.check {
	dependsOn(tasks.jacocoTestCoverageVerification)
}

// Mutation testing (PIT) over pure domain code only: fast unit tests, no Spring context.
// Wired by hand because the PIT Gradle plugin pins junit-platform-launcher to an old version
// that breaks the JUnit 6 engine used by Spring Boot 4.
val pitest: Configuration by configurations.creating

dependencies {
	pitest("org.pitest:pitest-command-line:1.20.2")
	pitest("org.pitest:pitest-junit5-plugin:1.2.3")
}

tasks.register<JavaExec>("pitest") {
	group = "verification"
	description = "Mutation testing of shared and domain packages; fails below the mutation threshold."
	dependsOn(tasks.testClasses)
	classpath = pitest
	mainClass = "org.pitest.mutationtest.commandline.MutationCoverageReport"
	val reportDir = layout.buildDirectory.dir("reports/pitest")
	doFirst {
		args(
			"--reportDir", reportDir.get().asFile.absolutePath,
			"--targetClasses", "co.nitida.shared.*,co.nitida.*.domain.*",
			"--excludedClasses", "co.nitida.*.domain.*Repository,co.nitida.shared.web.*,co.nitida.shared.audit.*,co.nitida.shared.persistence.*,*Test,*Test$*",
			"--excludedMethods", "get*,is*,toString,hashCode,equals",
			"--targetTests", "co.nitida.*",
			"--excludedTestClasses", "*IntegrationTest,*ApplicationTests",
			"--sourceDirs", "src/main/java",
			"--classPath", sourceSets.test.get().runtimeClasspath.files.joinToString(",") { it.absolutePath },
			"--mutationThreshold", "70",
			"--outputFormats", "HTML,XML",
			"--timestampedReports=false",
			"--threads", "2",
			"--failWhenNoMutations=false"
		)
	}
}
