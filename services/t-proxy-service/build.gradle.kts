plugins {
    id("utility.service-conventions")
    alias(libs.plugins.openapi.generator)
}

dependencies {
    // Generated API interfaces and models use Jakarta Bean Validation annotations.
    implementation("org.springframework.boot:spring-boot-starter-validation")
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

sourceSets {
    main {
        java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
    }
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}
