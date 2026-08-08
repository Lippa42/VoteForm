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

// Server Ktor (JVM). Gira su desktop per lo sviluppo e, in seguito, embedded
// nell'app Android che fa da host.
include(":server")

// Moduli previsti nelle prossime iterazioni:
// include(":persistence")
// include(":app")
