pluginManagement {
    // Convention plugins (utility.*) are resolved from the build-logic included build.
    includeBuild("build-logic")
}

rootProject.name = "utility-payments-eda"

include("services:t-proxy-service")
