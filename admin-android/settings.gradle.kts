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

rootProject.name = "sfide"

// Modulo puro Kotlin/JVM: modello dati + motore voto (nessuna dipendenza Android).
include(":engine")

// Moduli previsti nelle prossime iterazioni:
// include(":server")
// include(":persistence")
// include(":app")
