plugins {
    `kotlin-dsl`
}

dependencies {
    // Plugin artifacts must be on the classpath so convention plugins can apply them by id.
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dependency.management.gradle.plugin)
}
