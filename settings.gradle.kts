pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FluentMaiAndroid"

include(":app")
include(":core:model")
include(":core:database")
include(":core:importer")
include(":core:privacy")
include(":feature:home")
include(":feature:import")
include(":feature:scores")
include(":feature:quarantine")
include(":feature:settings")

