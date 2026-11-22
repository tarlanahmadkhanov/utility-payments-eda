plugins {
    id("utility.service-conventions")
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.jooq.docker)
}

// jOOQ and Flyway versions are managed by the Spring Boot BOM; codegen uses exactly the same versions as the runtime.
val jooqVersion = dependencyManagement.importedProperties.getValue("jooq.version")
val flywayVersion = dependencyManagement.importedProperties.getValue("flyway.version")

dependencies {
    // Generated API interfaces and models use Jakarta Bean Validation annotations.
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Persistence (ADR-0001, ADR-0003): jOOQ + Flyway on PostgreSQL.
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Swagger UI over the contract-first openapi.yaml; enabled only in the local profile (D11).
    implementation(libs.springdoc.openapi.webmvc.ui)

    // Classpath of the jOOQ code generator: it applies the same Flyway migrations in a throwaway PostgreSQL container.
    jooqCodegen("org.postgresql:postgresql")
    jooqCodegen("org.jooq:jooq-codegen:$jooqVersion")
    jooqCodegen("org.flywaydb:flyway-core:$flywayVersion")
    jooqCodegen("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")
}

// Contract-first: controller interfaces and DTOs are generated from openapi.yaml.
openApiGenerate {
    generatorName = "spring"
    inputSpec = layout.projectDirectory.file("src/main/resources/openapi/openapi.yaml")
    outputDir = layout.buildDirectory.dir("generated/openapi")
    apiPackage = "az.tarlan.utility.proxy.api"
    modelPackage = "az.tarlan.utility.proxy.api.model"
    configOptions = mapOf(
        "interfaceOnly" to "true",          // generate interfaces only, controllers are written by hand
        "useSpringBoot4" to "true",         // Spring Boot 4 target (implies Jakarta EE)
        "useJackson3" to "true",            // Jackson 3 is the default in Spring Boot 4
        "useTags" to "true",                // one interface per tag: PaymentsApi
        "skipDefaultInterface" to "true",   // no default method bodies returning 501
        "useBeanValidation" to "true",
        "openApiNullable" to "false",       // avoids the jackson-databind-nullable dependency
        "annotationLibrary" to "none",      // no swagger-annotations dependency
        "documentationProvider" to "none",
        "hideGenerationTimestamp" to "true",
    )
}

// jOOQ code generation: throwaway PostgreSQL container (same major version as compose and Testcontainers, ADR-0001),
// Flyway migrations from src/main/resources/db/migration, generated classes in build/generated-jooq.
jooq {
    withContainer {
        image {
            name = "postgres:18"
        }
    }
}

tasks.generateJooqClasses {
    basePackageName.set("az.tarlan.utility.proxy.adapter.persistence.jooq")
    schemas.set(listOf("public"))
}

sourceSets {
    main {
        java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
        // Wired explicitly, so that the build does not depend on what the plugin does by default.
        java.srcDir(tasks.generateJooqClasses.flatMap { it.outputDirectory })
    }
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate, tasks.generateJooqClasses)
}
